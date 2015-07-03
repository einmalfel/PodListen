package com.einmalfel.podlisten;


import android.accounts.Account;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.code.rome.android.repackaged.com.sun.syndication.feed.synd.SyndEnclosure;
import com.google.code.rome.android.repackaged.com.sun.syndication.feed.synd.SyndEntry;
import com.google.code.rome.android.repackaged.com.sun.syndication.feed.synd.SyndFeed;
import com.google.code.rome.android.repackaged.com.sun.syndication.io.FeedException;
import com.google.code.rome.android.repackaged.com.sun.syndication.io.SyndFeedInput;
import com.google.code.rome.android.repackaged.com.sun.syndication.io.XmlReader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;


public class EpisodesSyncAdapter extends AbstractThreadedSyncAdapter {
  private static final String TAG = "ESA";
  private static final String[] P_PROJECTION = {Provider.K_PFURL, Provider.K_ID, Provider.K_PSTATE};
  private static final Pattern AUDIO_PATTERN = Pattern.compile("^audio/\\w*");
  private static final int NEW_SUBSCRIPTION_LIMIT = 2; // limit episodes to add for feeds TODO option
  private static final int OLD_SUBSCRIPTION_LIMIT = 1000; // limit episodes to add for old feeds

  public EpisodesSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult) {
    Log.i(TAG, "Starting sync..");
    Context context = getContext();
    // Get podcasts cursor
    Cursor c = null;
    try {
      c = provider.query(Provider.podcastUri, P_PROJECTION, null, null, Provider.K_PSTATE);
    } catch (RemoteException e) {
      Log.e(TAG, "Podcast provider query failed with remote exception " + e);
      syncResult.databaseError = true;
    }
    if (c == null) {
      return;
    }
    if (c.getCount() < 1) {
      c.close();
      return;
    }

    // ROME-for-android doesn't work with system class loader which is used by default
    Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

    //setup notification
    Intent intent = new Intent(context, MainActivity.class);
    Bundle opts = new Bundle();
    opts.putInt(MainActivity.PAGE_LAUNCH_OPTION, MainActivity.Pages.NEW_EPISODES.ordinal());
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent pendingIntent =
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT, opts);
    NotificationManagerCompat nm = NotificationManagerCompat.from(context);
    NotificationCompat.Builder nb = new NotificationCompat.Builder(context)
        .setSmallIcon(R.mipmap.ic_sync_green_24dp)
        .setContentTitle(context.getString(R.string.refreshing))
        .setOngoing(true)
        .setProgress(c.getCount(), 0, false)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(pendingIntent);
    nm.notify(0, nb.build());

    // For each podcast launch rss parsing on its feed URL
    try {
      int furlIndex = c.getColumnIndexOrThrow(Provider.K_PFURL);
      int idIndex = c.getColumnIndexOrThrow(Provider.K_ID);
      int stateIndex = c.getColumnIndexOrThrow(Provider.K_PSTATE);
      int count = 0;
      while (c.moveToNext()) {
        long id = c.getLong(idIndex);
        String feedUrl = c.getString(furlIndex);
        int state = c.getInt(stateIndex);
        try {
          loadFeed(
              feedUrl, id, provider,
              state == Provider.PSTATE_NEW ? NEW_SUBSCRIPTION_LIMIT : OLD_SUBSCRIPTION_LIMIT);
        } catch (IOException e) {
          Log.e(TAG, "IO error while loading feed, skipping. " + feedUrl + " Exception: " + e);
          syncResult.stats.numIoExceptions++;
        } catch (FeedException e) {
          Log.e(TAG, "Feed error while loading feed, skipping. " + feedUrl + " Exception: " + e);
          syncResult.stats.numIoExceptions++;
        }
        nb.setProgress(c.getCount(), c.getPosition() + 1, false);
        count = countNewInDB(provider);
        if (count > 0) {
          nb.setContentText(Integer.toString(count) + ' ' + getContext().getString(R.string.new_episodes));
        }
        nm.notify(0, nb.build());
      }
      if (count == 0) {
        if (syncResult.stats.numIoExceptions == c.getCount()) {
          nb.setContentText(context.getString(R.string.refresh_failed));
        } else {
          nb.setContentText(context.getString(R.string.no_new_episodes));
        }
      }
    } catch (RemoteException re) {
      Log.e(TAG, "Content provider error " + re);
      syncResult.databaseError = true;
      nb.setContentText(context.getString(R.string.db_error));
    } finally {
      c.close();
      nb.setOngoing(false)
          .setProgress(0, 0, false)
          .setContentTitle(context.getString(R.string.refreshed));
      nm.notify(0, nb.build());
      Intent bi = new Intent(DownloadStartReceiver.REFRESH_FINISHED_INTENT);
      getContext().sendBroadcast(bi);
    }
  }

  private static int countNewInDB(@NonNull ContentProviderClient cpc) throws RemoteException {
    Cursor cursorBefore = cpc.query(Provider.episodeUri, null, Provider.K_ESTATE + " = ?",
        new String[]{Integer.toString(Provider.ESTATE_NEW)}, null);
    int count = cursorBefore.getCount();
    cursorBefore.close();
    return count;
  }

  private boolean updateTimeStamp(long id, @NonNull ContentProviderClient cpc, long timestamp)
      throws RemoteException {
    ContentValues values = new ContentValues();
    values.put(Provider.K_ETSTAMP, timestamp);
    return cpc.update(Provider.getUri(Provider.T_EPISODE, id), values, null, null) > 0;
  }

  private void loadFeed(@NonNull String url, long pid, @NonNull ContentProviderClient cpc,
                        int maxNewEpisodes) throws IOException, RemoteException, FeedException {
    Log.i(TAG, "Refreshing " + url);

    SyndFeedInput input = new SyndFeedInput();
    SyndFeed feed = input.build(new XmlReader(new URL(url)));

    long timestamp = new Date().getTime();

    // try update timestamp on each episode in received feed
    // if this fails, episode is not yet in db, insert it
    int inserted = 0;
    for (Object entry : feed.getEntries()) {
      SyndEntry syndEntry = (SyndEntry) entry;
      String audioLink = extractAudioUrl(syndEntry.getEnclosures());
      if (audioLink == null) {
        Log.i(TAG, syndEntry.getTitle() + " has no audio attachment, skipping");
        continue;
      }
      long id = (long) audioLink.hashCode() - Integer.MIN_VALUE;
      if (!updateTimeStamp(id, cpc, timestamp)) {
        tryInsertEntry(syndEntry, id, pid, timestamp, audioLink, cpc, inserted >= maxNewEpisodes);
        inserted++;
      }
    }

    // update podcast timestamp AFTER episode timestamps, so gone episodes wont be deleted in case
    // of connection fail in the middle of sync
    updatePodcastInfo(pid, cpc, feed, timestamp);

    //cleanup episodes which are both not interesting for user (ESTATE_GONE) and absent in feed
    Cursor episodesToDelete = cpc.query(
        Provider.episodeUri,
        new String[]{Provider.K_ID},
        Provider.K_ETSTAMP + " < ? AND " + Provider.K_ESTATE + " == ? AND " + Provider.K_EPID + " == ?",
        new String[]{Long.toString(timestamp), Integer.toString(Provider.ESTATE_GONE), Long.toString(pid)},
        null);
    while (episodesToDelete.moveToNext()) {
      long id = episodesToDelete.getLong(episodesToDelete.getColumnIndex(Provider.K_ID));
      boolean result = PodcastHelper.getInstance().deleteEpisode(id);
      Log.d(TAG, "Episode " + Long.toString(id) + " is absent in updated feed and gone. Deleted: " +
          Boolean.toString(result));
    }
    episodesToDelete.close();
  }

  private static void updatePodcastInfo(long id,
                                        @NonNull ContentProviderClient cpc,
                                        @NonNull SyndFeed feed,
                                        long timestamp) throws RemoteException {
    ContentValues values = new ContentValues();
    putStringIfNotNull(values, Provider.K_PURL, feed.getLink());
    putStringIfNotNull(values, Provider.K_PNAME, feed.getTitle());
    String description = feed.getDescription();
    if (description != null) {
      putStringIfNotNull(values, Provider.K_PDESCR, simplifyHTML(description));
    }
    //TODO check if image file exists and download it
    values.put(Provider.K_PSTATE, Provider.PSTATE_SEEN_ONCE);
    values.put(Provider.K_PTSTAMP, timestamp);
    int updated = cpc.update(Provider.getUri(Provider.T_PODCAST, id), values, null, null);
    if (updated != 1) {
      Log.e(TAG, "Unexpected number of items updated " + updated + " id " + id);
    }
  }

  @Nullable
  private Long tryInsertEntry(@NonNull SyndEntry entry, long id, long pid, long timestamp,
                              @NonNull String audioLink, @NonNull ContentProviderClient cpc,
                              boolean gone) throws RemoteException {
    String title = entry.getTitle();
    if (title == null) {
      title = getContext().getString(R.string.no_title);
    }

    ContentValues values = new ContentValues();
    putStringIfNotNull(values, Provider.K_ENAME, title);
    putStringIfNotNull(values, Provider.K_EAURL, audioLink);
    String description = entry.getDescription().getValue();
    if (description != null) {
      putStringIfNotNull(values, Provider.K_EDESCR, simplifyHTML(description));
    }
    putStringIfNotNull(values, Provider.K_EURL, entry.getLink());
    long size = extractAudioLength(entry.getEnclosures());
    // sometimes rss doesn't contain length attribute; sometimes it has erroneously small value
    if (size < 100 * 1024) {
      try {
        size = new URL(audioLink).openConnection().getContentLength();
      } catch (MalformedURLException ex) {
        Log.e(TAG, "Skipping episode " + entry.getLink() + ", malformed URL: " + ex);
        return null;
      } catch (IOException ex) {
        Log.e(TAG, "Leaving wrong episode size for " + entry.getLink() + ", IO exception: " + ex);
      }
    }
    values.put(Provider.K_ESIZE, size);
    values.put(Provider.K_EPLAYED, 0);
    values.put(Provider.K_ELENGTH, 0);
    values.put(Provider.K_EDATT, 0);
    values.put(Provider.K_EDFIN, 0);
    Date date = entry.getPublishedDate();
    if (date != null) {
      values.put(Provider.K_EDATE, date.getTime());
    }
    values.put(Provider.K_EPID, pid);
    values.put(Provider.K_ID, id);
    values.put(Provider.K_ETSTAMP, timestamp);
    values.put(Provider.K_ESTATE, gone ? Provider.ESTATE_GONE : Provider.ESTATE_NEW);
    cpc.insert(Provider.episodeUri, values);

    if (!gone) {
      Log.d(TAG, "New episode! " + title);
      Intent bi = new Intent(DownloadStartReceiver.NEW_EPISODE_INTENT);
      bi.putExtra(DownloadStartReceiver.URL_EXTRA_NAME, audioLink);
      bi.putExtra(DownloadStartReceiver.TITLE_EXTRA_NAME, title);
      bi.putExtra(DownloadStartReceiver.ID_EXTRA_NAME, id);
      getContext().sendBroadcast(bi);
    }

    return id;
  }

  private static boolean isAudioEnclosure(@NonNull SyndEnclosure enclosure) {
    return AUDIO_PATTERN.matcher(enclosure.getType()).matches() && enclosure.getUrl() != null;
  }

  private static long extractAudioLength(@NonNull List enclosures) {
    for (Object o : enclosures) {
      SyndEnclosure enclosure = (SyndEnclosure) o;
      if (isAudioEnclosure(enclosure)) {
        return enclosure.getLength();
      }
    }
    throw new RuntimeException("extractAudioLength was called when no audio is available");
  }

  @Nullable
  private static String extractAudioUrl(@NonNull List enclosures) {
    for (Object o : enclosures) {
      SyndEnclosure enclosure = (SyndEnclosure) o;
      if (isAudioEnclosure(enclosure)) {
        return enclosure.getUrl();
      }
    }
    return null;
  }

  private static void putStringIfNotNull(@NonNull ContentValues values, @NonNull String key,
                                         @Nullable String s) {
    if (s != null) {
      values.put(key, s);
    }
  }

  private static final Pattern brPattern = Pattern.compile("<br.*?/>\\s*<br.*/>|<p/?>|</li>");
  private static final Pattern erasePattern = Pattern.compile("<img.+?>|</p>|</?ul/?>");
  private static final Pattern listPattern = Pattern.compile("<li>");
  @NonNull
  private static String simplifyHTML(@NonNull String text) {
    text = erasePattern.matcher(text).replaceAll("");
    text = listPattern.matcher(text).replaceAll("\u2022");
    text = brPattern.matcher(text).replaceAll("<br/>");
    return text.trim();
  }
}
