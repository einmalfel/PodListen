package com.einmalfel.podlisten;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.util.Collection;
import java.util.LinkedHashSet;

public class DownloadStartReceiver extends BroadcastReceiver {
  private static final String TAG = "DSR";
  static final String NEW_EPISODE_INTENT = "com.einmalfel.podlisten.NEW_EPISODE";
  static final String REFRESH_FINISHED_INTENT = "com.einmalfel.podlisten.REFRESH_FINISHED";
  static final String URL_EXTRA_NAME = "URL";
  static final String TITLE_EXTRA_NAME = "TITLE";
  static final String ID_EXTRA_NAME = "ID";
  private static final Collection<Long> ids = new LinkedHashSet<Long>(0);
  private static final Collection<String> urls = new LinkedHashSet<String>(0);
  private DownloadManager downloadManager;

  private static boolean isWiFiConnected(Context context) {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
        Context.CONNECTIVITY_SERVICE);
    return cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
  }

  private void download(Context context, String url, String title, long id) {
    if (urls.contains(url)) {
      Log.w(TAG, "Already downloading episode id " + Long.toString(id) + ". Skipping..");
      return;
    }

    DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setAllowedOverMetered(false)
        .setAllowedOverRoaming(false)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PODCASTS, Long.toString(id))
        .setDescription("Downloading podcast " + url)
        .setVisibleInDownloadsUi(false)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

    ids.add(getDM(context).enqueue(rq));
    urls.add(url);
  }

  private DownloadManager getDM(Context context) {
    if (downloadManager == null) {
      downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    }
    return downloadManager;
  }

  private void processDownloadResult(long id, Context context) {
    DownloadManager.Query query = new DownloadManager.Query();
    query.setFilterById(id);
    Cursor cursor = getDM(context).query(query);
    if (!cursor.moveToFirst()) {
      Log.e(TAG, "DownloadManager query failed");
      return;
    }
    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
    String url = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI));
    urls.remove(url);
    cursor.close();
    ContentValues values = new ContentValues();
    values.put(Provider.K_EDATT, 1);
    if (status == DownloadManager.STATUS_SUCCESSFUL) {
      values.put(Provider.K_EDFIN, 100);
      Log.i(TAG, "Successfully downloaded " + url);
    } else {
      Log.w(TAG, url + " download Failed");
      values.put(Provider.K_EDFIN, 0);
    }
    int updated = context.getContentResolver().update(
        Provider.episodeUri,
        values,
        Provider.K_EAURL + " = ?",
        new String[]{url});
    if (updated != 1) {
      Log.e(TAG, "Something went wrong while updating episode. Url " + url + ", updated " + updated);
    }
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (isWiFiConnected(context)) {
      String action = intent.getAction();
      if (intent.getAction().equals(NEW_EPISODE_INTENT)) {
        download(context,
            intent.getStringExtra(URL_EXTRA_NAME),
            intent.getStringExtra(TITLE_EXTRA_NAME),
            intent.getLongExtra(ID_EXTRA_NAME, -1));
      } else if (action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
        if (ids.remove(id)) {
          processDownloadResult(id, context);
        }
      } else {
        //Restart all unfinished not gone downloads. Conditions here:
        // - phone just booted up or wi-fi state changed or refresh finished
        // - wi-fi connected
        Log.i(TAG, "Restarting unfinished downloads");
        Cursor c = context.getContentResolver().query(
            Provider.episodeUri,
            new String[]{Provider.K_EAURL, Provider.K_ID, Provider.K_ENAME},
            Provider.K_EDFIN + " != ? AND " + Provider.K_ESTATE + " != ?",
            new String[]{"100", Integer.toString(Provider.ESTATE_GONE)},
            Provider.K_EDATE);
        int urlIndex = c.getColumnIndex(Provider.K_EAURL);
        int idIndex = c.getColumnIndex(Provider.K_ID);
        int titleIndex = c.getColumnIndex(Provider.K_ENAME);
        if (c.moveToFirst()) {
          do {
            download(context, c.getString(urlIndex), c.getString(titleIndex), c.getLong(idIndex));
          } while (c.moveToNext());
        }
        c.close();
      }
    }
  }
}
