package com.einmalfel.podlisten;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helper class intended to do podcast-related stuff
 */
public class PodcastHelper {
  private static final String TAG = "EPM";
  private static final int TIMEOUT_MS = 15000;
  private static final DateFormat formatYYYYMMDD = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
  private static PodcastHelper instance;
  private final Context context = PodListenApp.getContext();
  private final ContentResolver resolver = context.getContentResolver();

  static URLConnection openConnectionWithTO(URL url) throws IOException {
    URLConnection result = url.openConnection();
    result.setConnectTimeout(TIMEOUT_MS);
    result.setReadTimeout(TIMEOUT_MS);
    if (result instanceof HttpURLConnection) {
      HttpURLConnection httpURLConnection = (HttpURLConnection) result;
      httpURLConnection.setInstanceFollowRedirects(true);
      if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM ||
          httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
        httpURLConnection.disconnect();
        URL newUrl = new URL(url, httpURLConnection.getHeaderField("Location"));
        Log.d(TAG, "Following redirect from " + url + " to " + newUrl);
        return openConnectionWithTO(newUrl);
      }
    }
    return result;
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

  @NonNull
  public static String shortDateFormat(long date) {
    if (new Date().getTime() - date > 6 * 24 * 60 * 60 * 1000) {
      return formatYYYYMMDD.format(date);
    } else {
      return DateUtils.getRelativeTimeSpanString(date).toString();
    }
  }

  @NonNull
  public String shortFormatDurationMs(long milliseconds) {
    long minutes = milliseconds / 60 / 1000;
    long hours = minutes / 60;
    return (hours > 0 ? hours + context.getString(R.string.hour_abbreviation) : "") +
        minutes % 60 + context.getString(R.string.minute_abbreviation);
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
      ContentValues values = new ContentValues(5);
      values.put(Provider.K_PFURL, url);
      values.put(Provider.K_PRMODE, refreshMode.ordinal());
      values.put(Provider.K_ID, id);
      values.put(Provider.K_PSTATE, Provider.PSTATE_NEW);
      values.put(Provider.K_PTSTAMP, 0);
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
        Snackbar.make(container,
                      context.getString(R.string.podcast_already_subscribed, url),
                      Snackbar.LENGTH_LONG)
                .show();
      }
      return result;
    } catch (PodcastHelper.SubscriptionNotInsertedException notInsertedException) {
      if (container != null) {
        Snackbar.make(container, R.string.podcast_subscribe_failed, Snackbar.LENGTH_LONG).show();
      }
      return 0;
    }
  }
}
