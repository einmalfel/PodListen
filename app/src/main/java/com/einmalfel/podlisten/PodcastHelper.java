package com.einmalfel.podlisten;


import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.einmalfel.podlisten.support.UnitConverter;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helper class intended to do podcast-related stuff, like properly deleting episodes, etc
 */
public class PodcastHelper {
  static final int MIN_IMAGE_WIDTH_SP = 70;
  static final int MAX_IMAGE_WIDTH_SP = 150;
  private static final String TAG = "EPM";
  private static PodcastHelper instance;
  final int minImageWidthPX;
  final int maxImageWidthPX;
  private final Context context = PodListenApp.getContext();
  private final ContentResolver resolver= context.getContentResolver();

  public PodcastHelper() {
    // allow image to take up to 150dp but not more then one fifth of screen width
    int oneFifthScreenWidth = context.getResources().getDisplayMetrics().widthPixels / 5;
    int boundSetInSp = UnitConverter.getInstance().spToPx(MAX_IMAGE_WIDTH_SP);
    maxImageWidthPX = boundSetInSp > oneFifthScreenWidth ? oneFifthScreenWidth : boundSetInSp;
    minImageWidthPX = UnitConverter.getInstance().spToPx(MIN_IMAGE_WIDTH_SP);
  }

  //  not making synchronized method to speed up access
  public static PodcastHelper getInstance() {
    if (instance == null) {
      synchronized (PodcastHelper.class) {
        if (instance == null) {
          instance = new PodcastHelper();
        }
      }
    }
    return instance;
  }

  public static long generateId(@NonNull String url) {
    return (long) url.hashCode() - Integer.MIN_VALUE;
  }

  public static void deleteEpisodeDialog(final long episodeId, final Context context) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        PodcastHelper.getInstance().markEpisodeGone(episodeId);
      }
    });
    builder
        .setNegativeButton(R.string.cancel, null)
        .setTitle(context.getString(R.string.delete_episode))
        .create()
        .show();
  }

  private void tryDeleteFiles(long id) {
    Storage storage = Preferences.getInstance().getStorage();
    if (storage == null || !storage.isAvailableRW()) {
      Log.w(TAG, "failed to delete episode media: no storage or it isn't writable");
      return;
    }

    File f = new File(storage.getPodcastDir(), Long.toString(id));
    if (f.exists() && !f.delete()) {
      Log.w(TAG, "Failed to delete " + f.toURI());
    }

    ImageManager.getInstance().deleteImage(id);
  }

  public boolean markEpisodeGone(long id) {
    return markEpisodeGone(id, false);
  }

  /**
   * Marks episode as gone.
   * If episode is absent in origin feed, deletes it from DB.
   * Tries to delete downloaded media (if there is some on mounted external storage)
   *
   * @param id episode id to delete
   * @return true if success, false if episode is already absent in db
   */
  public boolean markEpisodeGone(long id, boolean quiet) {
    boolean result = false;
    // TODO what if subscription is deleted?
    Cursor c = resolver.query(
        Provider.getUri(Provider.T_E_JOIN_P, id),
        new String[]{Provider.K_ETSTAMP, Provider.K_PTSTAMP, Provider.K_ENAME, Provider.K_EDID},
        Provider.K_ESTATE + " != ?",
        new String[]{Integer.toString(Provider.ESTATE_GONE)},
        null);
    if (c.moveToFirst()) {
      if (!quiet) {
        Toast.makeText(
            context,
            "Episode deleted: " + c.getString(c.getColumnIndex(Provider.K_ENAME)),
            Toast.LENGTH_SHORT
        ).show();
      }
      long dId = c.getLong(c.getColumnIndexOrThrow(Provider.K_EDID));
      if (dId != 0) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        dm.remove(dId);
      }
      if (c.getLong(c.getColumnIndexOrThrow(Provider.K_ETSTAMP)) < c.getLong(c.getColumnIndexOrThrow(Provider.K_PTSTAMP))) {
        Log.i(TAG, "Feed doesn't contain episode " + id + " anymore. Deleting..");
        c.close();
        return deleteEpisode(id);
      } else {
        ContentValues val = new ContentValues(3);
        val.put(Provider.K_ESTATE, Provider.ESTATE_GONE);
        val.put(Provider.K_EDFIN, 0);
        val.put(Provider.K_EDID, 0);
        result = resolver.update(Provider.getUri(Provider.T_EPISODE, id), val, null, null) == 1;
      }
    }
    c.close();
    tryDeleteFiles(id);
    return result;

  }

  private static final DateFormat formatYYYYMMDD = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

  @NonNull
  public static String shortDateFormat(long date) {
    if (new Date().getTime() - date > 6 * 24 * 60 * 60 * 1000) {
      return formatYYYYMMDD.format(date);
    } else {
      return DateUtils.getRelativeTimeSpanString(date).toString();
    }
  }

  @NonNull
  public static String shortFormatDurationMs(long milliseconds) {
    long minutes = milliseconds / 60 / 1000;
    long hours = minutes / 60;
    return (hours > 0 ? hours + "h" : "") + minutes % 60 + "m";
  }

  /**
   * Based on http://stackoverflow.com/a/3758880/2015129
   */
  public static String humanReadableByteCount(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    if (bytes < unit)
      return bytes + "B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
    return String.format("%d%sB", (int) (bytes / Math.pow(unit, exp)), pre);
  }

  /**
   * Try remove episode from db.
   * Remove media files if any.
   *
   * @param id of episode
   * @return true if successfully deleted from db
   */
  public boolean deleteEpisode(long id) {
    tryDeleteFiles(id);
    return resolver.delete(Provider.getUri(Provider.T_EPISODE, id), null, null) == 1;
  }

  public class SubscriptionNotInsertedException extends Throwable {
  }

  /**
   * Add subscription to podcasts table
   *
   * @param url url to subscribe
   * @return ID of podcast or zero if already subscribed
   * @throws SubscriptionNotInsertedException if failed to insert subscription into db
   */
  public long addSubscription(String url, @NonNull Provider.RefreshMode refreshMode)
      throws SubscriptionNotInsertedException {
    if (!url.toLowerCase().matches("^\\w+://.*")) {
      url = "http://" + url;
      Log.w(TAG, "Feed download protocol defaults to http, new url: " + url);
    }
    long id = generateId(url);
    Cursor c = resolver.query(Provider.getUri(Provider.T_PODCAST, id), null, null, null, null);
    int count = c.getCount();
    c.close();
    if (count == 1) {
      return 0;
    } else {
      ContentValues values = new ContentValues(4);
      values.put(Provider.K_PFURL, url);
      values.put(Provider.K_PRMODE, refreshMode.ordinal());
      values.put(Provider.K_ID, id);
      values.put(Provider.K_PATSTAMP, new Date().getTime());
      if (resolver.insert(Provider.podcastUri, values) == null) {
        throw new SubscriptionNotInsertedException();
      } else {
        return id;
      }
    }
  }

  long trySubscribe(@NonNull String url, @Nullable View container,
                    @NonNull Provider.RefreshMode refreshMode) {
    try {
      long result = addSubscription(url, refreshMode);
      if (result == 0 && container != null) {
        Snackbar.make(container, "Already subscribed to " + url, Snackbar.LENGTH_LONG).show();
      }
      return result;
    } catch (PodcastHelper.SubscriptionNotInsertedException notInsertedException) {
      if (container != null) {
        Snackbar.make(container, "DB error - failed to subscribe", Snackbar.LENGTH_LONG).show();
      }
      return 0;
    }
  }

  int getListImageWidth(@NonNull Bitmap image) {
    final int width = image.getWidth();
    if (width < minImageWidthPX) {
      return minImageWidthPX;
    } else if (width > maxImageWidthPX) {
      return maxImageWidthPX;
    } else {
      return width;
    }
  }

  void clearNewEpisodes() {
    Cursor c = context.getContentResolver().query(
        Provider.episodeUri,
        new String[]{Provider.K_ID},
        Provider.K_ESTATE + " = ?",
        new String[]{Integer.toString(Provider.ESTATE_NEW)},
        null);
    while (c.moveToNext()) {
      PodcastHelper.getInstance().markEpisodeGone(
          c.getLong(c.getColumnIndex(Provider.K_ID)), true);
    }
    c.close();
  }
}
