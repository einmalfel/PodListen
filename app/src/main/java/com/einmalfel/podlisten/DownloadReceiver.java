package com.einmalfel.podlisten;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class DownloadReceiver extends BroadcastReceiver {
  private static final String TAG = "DLR";
  static final String NEW_EPISODE_ACTION = "com.einmalfel.podlisten.NEW_EPISODE";
  static final String DOWNLOAD_HEARTBEAT_ACTION = "com.einmalfel.podlisten.DOWNLOAD_HEARTBEAT";
  static final String UPDATE_QUEUE_ACTION = "com.einmalfel.podlisten.UPDATE_QUEUE";
  static final String DOWNLOAD_EPISODE_ACTION = "com.einmalfel.podlisten.DOWNLOAD_EPISODE";
  static final String URL_EXTRA_NAME = "URL";
  static final String TITLE_EXTRA_NAME = "TITLE";
  static final String ID_EXTRA_NAME = "ID";
  private static Boolean charging = null;

  static boolean isDeviceCharging() {
    IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    Intent batteryStatus = PodListenApp.getContext().registerReceiver(null, intentFilter);
    if (batteryStatus == null) {
      return false; // If we failed to get state, it's safer to assume that device is not charging
    } else {
      int s = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
      return s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL;
    }
  }

  public DownloadReceiver() {
    super();
    if (charging == null) {
      charging = isDeviceCharging();
    }
  }

  /** @return true if download request was dispatched to DownloadManager, false otherwise */
  private boolean download(Context context, String url, String title, long id) {
    Storage storage = Preferences.getInstance().getStorage();
    if (storage == null || !storage.isAvailableRW()) {
      Log.e(TAG, "Discarding download, as there is no storage, or it isn't writable");
      return false;
    }

    // if file was downloaded before (e.g. partially or with error), remove it
    File target = new File(storage.getPodcastDir(), Long.toString(id));
    if (target.exists() && !target.delete()) {
      Log.e(TAG, "Failed to delete previous download " + target);
      return false;
    }

    Preferences.DownloadNetwork downloadNetwork = Preferences.getInstance().getDownloadNetwork();
    DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setAllowedOverMetered(downloadNetwork == Preferences.DownloadNetwork.ANY ||
                                   downloadNetwork == Preferences.DownloadNetwork.NON_ROAMING)
        .setAllowedOverRoaming(downloadNetwork == Preferences.DownloadNetwork.ANY)
        .setDestinationUri(Uri.fromFile(target))
        .setDescription(context.getString(R.string.episode_downloading, url))
        .setVisibleInDownloadsUi(false)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

    DownloadManager dM = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    long downloadId;
    try {
      downloadId = dM.enqueue(rq);
    } catch (SecurityException e) {
      if (storage.isPrimaryStorage()) {
        throw e;
      }
      Log.w(TAG, "DM produced security exception. Downloading to primary storage and copying", e);
      rq.setDestinationUri(null)
        .setDestinationInExternalFilesDir(
            context, Environment.DIRECTORY_PODCASTS, Long.toString(id));
      downloadId = dM.enqueue(rq);
    } catch (NullPointerException npe) {
      // By reading DownloadManager code I found that it could throw NPE when requesting download.
      // DM process provides ContentProvider, that contains state of downloads. When new download
      // is being queued DM client class inserts new row into it, and if DM service is killed
      // (because of crash or low memory condition) insert call trows RemoteException, which isn't
      // handled properly and this cause dereference of null in DownloadManager.enqueue()
      Log.e(TAG, "Ignoring NPE in DM.enqueue(). Will try to restart d/l next time", npe);
      return false;
    }

    ContentValues cv = new ContentValues(1);
    cv.put(Provider.K_EDID, downloadId);
    context.getContentResolver().update(Provider.getUri(Provider.T_EPISODE, id), cv, null, null);
    return true;
  }

  private void processDownloadResult(Context context, long downloadId) {
    DownloadManager dM = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    Cursor c = dM.query(new DownloadManager.Query().setFilterById(downloadId));
    if (c == null || !c.moveToFirst()) {
      Log.i(TAG, "DownloadManager query failed");
      if (c != null) {
        c.close();
      }
      return;
    }
    int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
    String fileName = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME));
    int reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
    c.close();

    ContentValues values = new ContentValues(3);
    Storage currentStorage = Preferences.getInstance().getStorage();
    values.put(Provider.K_EDID, 0);
    if (status == DownloadManager.STATUS_SUCCESSFUL && !TextUtils.isEmpty(fileName) &&
        currentStorage != null) {
      try {
        values.put(Provider.K_EDFIN, currentStorage.contains(new File(fileName)) ?
            Provider.EDFIN_PROCESSING : Provider.EDFIN_MOVING);
        values.put(Provider.K_EERROR, (String) null);
      } catch (IOException exception) {
        Log.wtf(TAG, "Can't convert download path to cannonical form", exception);
      }
    }
    if (!values.containsKey(Provider.K_EDFIN)) {
      Log.w(TAG, downloadId + " download failed, reason " + reason);
      // TODO: replace error code with smth human-readable
      values.put(Provider.K_EERROR, "Download failed. Error code: " + reason);
      values.put(Provider.K_EDFIN, Provider.EDFIN_ERROR);
    }
    if (context.getContentResolver().update(
        Provider.episodeUri, values, Provider.K_EDID + " == " + downloadId, null) == 1) {
      PodcastOperations.handleDownloads(context);
    } else {
      Log.e(TAG, "Failed to update dp row for download " + downloadId);
    }
  }

  private int getRunningCount(Context context) {
    DownloadManager dM = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    DownloadManager.Query query = new DownloadManager.Query().setFilterByStatus(
        DownloadManager.STATUS_PAUSED | DownloadManager.STATUS_PENDING | DownloadManager.STATUS_RUNNING);
    Cursor cursor = dM.query(query);
    if (cursor == null) {
      Log.wtf(TAG, "Download manager query failed", new NullPointerException());
      return Integer.MAX_VALUE; // to prevent starting of new downloads
    } else {
      int runningCount = cursor.getCount();
      cursor.close();
      return runningCount;
    }
  }

  /**
   * @param context to run db queries and to get DownloadManager instance
   * @param force   if false, failed downloads won't be restarted more often than 1/refresh period
   */
  private void updateDownloadQueue(Context context, boolean force) {
    Preferences prefs = Preferences.getInstance();
    if (!charging && prefs.getAutoDownloadACOnly()) {
      return;
    }
    if (prefs.getAutoDownloadMode() == Preferences.AutoDownloadMode.NEVER) {
      return;
    }
    int runningDownloadsCount = getRunningCount(context);
    int maxParallelDownloads = prefs.getMaxDownloads().toInt();
    if (runningDownloadsCount >= maxParallelDownloads) {
      return;
    }

    String condition = Provider.K_EDID + " == 0 AND " + Provider.K_EDFIN + " NOT IN (" +
        Provider.EDFIN_COMPLETE + ", " + Provider.EDFIN_MOVING + ", " + Provider.EDFIN_PROCESSING +
        ") AND ";
    if (prefs.getAutoDownloadMode() == Preferences.AutoDownloadMode.PLAYLIST) {
      condition += Provider.K_ESTATE + " == " + Provider.ESTATE_IN_PLAYLIST;
    } else {
      condition += Provider.K_ESTATE + " != " + Provider.ESTATE_GONE;
    }
    if (!force) {
      long refreshIntervalMs = prefs.getRefreshInterval().periodSeconds * 1000;
      long dayRefreshInterval = Preferences.RefreshIntervalOption.DAY.periodSeconds * 1000;
      if (refreshIntervalMs == 0 || refreshIntervalMs > dayRefreshInterval) {
        refreshIntervalMs = dayRefreshInterval;
      }
      condition += " AND " + Provider.K_EDTSTAMP + "<" + (new Date().getTime() - refreshIntervalMs);
    }
    Cursor queue = context.getContentResolver().query(
        Provider.episodeUri,
        new String[]{Provider.K_EAURL, Provider.K_ENAME, Provider.K_ID},
        condition,
        null,
        Provider.K_EDATT + " ASC, " + Provider.K_EDATE + " ASC");
    if (queue == null) {
      throw new AssertionError("Unexpectedly got null while querying provider");
    }
    int urlInd = queue.getColumnIndexOrThrow(Provider.K_EAURL);
    int titleInd = queue.getColumnIndexOrThrow(Provider.K_ENAME);
    int idInd = queue.getColumnIndexOrThrow(Provider.K_ID);
    while (queue.moveToNext() && runningDownloadsCount < maxParallelDownloads) {
      if (download(
          context, queue.getString(urlInd), queue.getString(titleInd), queue.getLong(idInd))) {
        Log.d(TAG, "Updating queue : adding " + queue.getString(titleInd));
        runningDownloadsCount++;
      }
    }
    queue.close();
  }

  static void stopDownloads(@Nullable String selection) {
    Context context = PodListenApp.getContext();
    String finalSelection = Provider.K_EDID + " != 0";
    if (selection != null && !selection.isEmpty()) {
      finalSelection += " AND " + selection;
    }
    Cursor cursor = context.getContentResolver().query(
        Provider.episodeUri, new String[]{Provider.K_EDID}, finalSelection, null, null);
    if (cursor != null) {
      if (cursor.getCount() != 0) {
        long[] ids = new long[cursor.getCount()];
        int columnId = cursor.getColumnIndexOrThrow(Provider.K_EDID);
        int i = 0;
        while (cursor.moveToNext()) {
          ids[i++] = cursor.getLong(columnId);
        }

        DownloadManager dM = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        int removeResult = dM.remove(ids);
        if (removeResult != ids.length) {
          Log.e(TAG, "Failed to delete " + (ids.length - removeResult) + " downloads");
        }
        ContentValues cv = new ContentValues(1);
        cv.put(Provider.K_EDID, 0);
        cv.put(Provider.K_EDFIN, 0);
        context.getContentResolver().update(Provider.episodeUri, cv, finalSelection, null);
      }
      cursor.close();
    } else {
      Log.e(TAG, "Query failed unexpectedly", new AssertionError());
    }
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Preferences preferences = Preferences.getInstance();
    // wait while preferences are changing
    synchronized (preferences) {
      switch (intent.getAction()) {
        case Intent.ACTION_POWER_CONNECTED:
          charging = true;
          updateDownloadQueue(context, true);
          break;
        case Intent.ACTION_POWER_DISCONNECTED:
          charging = false;
          if (preferences.getAutoDownloadACOnly()) {
            stopDownloads(null);
          }
          break;
        case DOWNLOAD_EPISODE_ACTION:
          download(context,
                   intent.getStringExtra(URL_EXTRA_NAME),
                   intent.getStringExtra(TITLE_EXTRA_NAME),
                   intent.getLongExtra(ID_EXTRA_NAME, -1));
          break;
        case DOWNLOAD_HEARTBEAT_ACTION:
          updateProgress(context);
          break;
        case UPDATE_QUEUE_ACTION:
          updateDownloadQueue(context, true);
          break;
        case NEW_EPISODE_ACTION:
          if (getRunningCount(context) < preferences.getMaxDownloads().toInt() &&
              (charging || !preferences.getAutoDownloadACOnly()) &&
              preferences.getAutoDownloadMode() == Preferences.AutoDownloadMode.ALL_NEW) {
            download(context,
                     intent.getStringExtra(URL_EXTRA_NAME),
                     intent.getStringExtra(TITLE_EXTRA_NAME),
                     intent.getLongExtra(ID_EXTRA_NAME, -1));
          }
          break;
        case DownloadManager.ACTION_NOTIFICATION_CLICKED:
          Intent i = new Intent(context, MainActivity.class)
              .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          context.startActivity(i);
          break;
        case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
          processDownloadResult(context,
                                intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L));
          updateDownloadQueue(context, false);
          break;
      }
    }
  }

  private void updateProgress(Context context) {
    //TODO call this asynchronously
    Cursor c = context.getContentResolver().query(
        Provider.episodeUri,
        new String[]{Provider.K_ID, Provider.K_EDID},
        Provider.K_EDID + " != 0",
        null,
        null);
    while (c.moveToNext()) {
      long downLoadId = c.getLong(c.getColumnIndexOrThrow(Provider.K_EDID));
      long id = c.getLong(c.getColumnIndexOrThrow(Provider.K_ID));
      DownloadManager dM = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
      Cursor q = dM.query(new DownloadManager.Query().setFilterById(downLoadId));
      if (q != null && q.moveToFirst()) {
        int state = q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        // WORKAROUND: sometimes ACTION_DOWNLOAD_COMPLETE is somehow not received (or there was an
        // exception in callback), so handle there episodes completed more than a minute ago
        if (state == DownloadManager.STATUS_SUCCESSFUL || state == DownloadManager.STATUS_FAILED) {
          long t = q
              .getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP));
          if (System.currentTimeMillis() - t > 60000) {
            Log.e(TAG, "Found lost completed download, processing " + downLoadId);
            processDownloadResult(context, downLoadId);
          }
        } else {
          int got = q
              .getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
          int total = q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
          // ignore dubious data. E.g. sometimes it reports total size is -1B or 128B
          if (got > 0 && total > 1000 && total > got) {
            ContentValues v = new ContentValues(2);
            v.put(Provider.K_EDFIN, 99L * got / total);
            v.put(Provider.K_ESIZE, total);
            context.getContentResolver().update(
                Provider.getUri(Provider.T_EPISODE, id), v, null, null);
          }
        }
      } else {
        Log.e(TAG, "Failed to obtain download info for episode " + id + ". Resetting K_EDID to 0");
        ContentValues v = new ContentValues(2);
        v.put(Provider.K_EDID, 0);
        context.getContentResolver().update(Provider.getUri(Provider.T_EPISODE, id), v, null, null);
      }
      if (q != null) {
        q.close();
      }
    }
    c.close();
  }
}
