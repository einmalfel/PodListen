package com.einmalfel.podlisten;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.einmalfel.earl.EarlParser;
import com.einmalfel.earl.Enclosure;
import com.einmalfel.earl.Feed;
import com.einmalfel.earl.Item;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

class SyncWorker implements Runnable {
  private static final String TAG = "SWK";
  /**
   * Feed parsing stops after reading this number of feed items
   */
  private static final int MAX_EPISODES_TO_PARSE = 1000;
  private static final Pattern AUDIO_PATTERN = Pattern.compile("^audio/\\w*");
  private final long id;
  private final String link;
  private final int maxNewEpisodes;
  private final SyncState syncState;
  private final ContentProviderClient provider;

  public SyncWorker(long id, @NonNull String link, @NonNull ContentProviderClient provider,
                    @NonNull SyncState syncState, int maxNewEpisodes) {
    this.id = id;
    this.link = link;
    this.maxNewEpisodes = maxNewEpisodes;
    this.provider = provider;
    this.syncState = syncState;
  }

  @Override
  public void run() {
    String title = null;
    try {
      InputStream inputStream = new URL(link).openConnection().getInputStream();
      Feed feed = EarlParser.parseOrThrow(inputStream, MAX_EPISODES_TO_PARSE);

      // Episodes need to be timestamped before subscriptions, otherwise cleanup algorithm may
      // delete fresh episodes in case of an exception between feed and episodes update
      long timestamp = new Date().getTime();

      int newEpisodesInserted = 0;
      for (Item episode : feed.getItems()) {
        boolean markNew = newEpisodesInserted < maxNewEpisodes;
        if (tryInsertEpisode(episode, id, timestamp, provider, markNew) && markNew) {
          newEpisodesInserted++;
        }
      }

      title = updateFeed(id, feed, timestamp);

      // delete every gone episode whose timestamp is less then feeds timestamp
      if (title != null) {
        Cursor episodesToDelete = provider.query(
            Provider.episodeUri,
            new String[]{Provider.K_ID},
            Provider.K_ETSTAMP + " < ? AND " + Provider.K_ESTATE + " == ? AND " + Provider.K_EPID + " == ?",
            new String[]{Long.toString(timestamp),
                         Integer.toString(Provider.ESTATE_GONE),
                         Long.toString(id)},
            null);
        if (episodesToDelete != null) {
          while (episodesToDelete.moveToNext()) {
            long id = episodesToDelete.getLong(episodesToDelete.getColumnIndex(Provider.K_ID));
            boolean result = PodcastHelper.getInstance().deleteEpisode(id);
            Log.d(TAG, "Episode " + id + " is absent in updated feed and gone. Deleted: " + result);
          }
          episodesToDelete.close();
        } else {
          Log.e(TAG, "Failed to obtain episodesToDelete cursor");
        }

        syncState.signalFeedSuccess(title, newEpisodesInserted);
      } else {
        Log.e(TAG, "Feed update failed, id " + id);
        syncState.signalDBError(link);
      }
    } catch (IOException exception) {
      Log.w(TAG, link + ": Failed to load feed: ", exception);
      syncState.signalIOError(link);
    } catch (RemoteException exception) {
      Log.w(TAG, link + ": Failed to load feed: ", exception);
      syncState.signalDBError(title == null ? link : title);
    } catch (DataFormatException | XmlPullParserException exception) {
      Log.w(TAG, link + ": Failed to load feed", exception);
      syncState.signalParseError(link);
    } catch (Exception exception) {
      Log.e(TAG, link + ": Something unexpected happened while refreshing", exception);
      syncState.signalIOError(title == null ? link : title);
    }
  }

  private boolean tryInsertEpisode(@NonNull Item episode, long subscriptionId, long timestamp,
                                   @NonNull ContentProviderClient provider, boolean markNew) {
    String title = episode.getTitle();
    if (title == null) {
      title = "NO TITLE";
    }

    // extract audio enclosure or return
    String audioLink = null;
    Integer audioSize = null;
    for (Enclosure enclosure : episode.getEnclosures()) {
      String type = enclosure.getType();
      if (type != null && AUDIO_PATTERN.matcher(type).matches()) {
        audioLink = enclosure.getLink();
        audioSize = enclosure.getLength();
      }
    }
    if (audioLink == null) {
      Log.i(TAG, title + " lacks audio, skipped");
      return false;
    }
    if (audioSize == null || audioSize < 10 * 1024) {
      try {
        audioSize = new URL(audioLink).openConnection().getContentLength();
      } catch (MalformedURLException ex) {
        Log.e(TAG,
              "Episode " + episode.getLink() + " has malformed URL: " + audioLink, ex);
        return false;
      } catch (IOException ex) {
        Log.e(TAG, "Leaving wrong episode size for " + episode.getLink(), ex);
      }
    }

    // try update episode timestamp. If this fails, episode is not yet in db, insert it
    long id = PodcastHelper.generateId(audioLink);
    try {
      if (updateEpisodeTimestamp(id, provider, timestamp)) {
        return false;
      }
    } catch (RemoteException exception) {
      Log.e(TAG, "DB failed to timestamp episode " + id + ", skipping, exception: ", exception);
      return false;
    }

    // put episode into DB
    ContentValues values = new ContentValues();
    values.put(Provider.K_ENAME, title);
    values.put(Provider.K_EAURL, audioLink);
    String description = episode.getDescription();
    if (description != null) {
      values.put(Provider.K_EDESCR, simplifyHTML(description));
    }
    values.put(Provider.K_EURL, episode.getLink());
    values.put(Provider.K_ESIZE, audioSize);
    values.put(Provider.K_EPLAYED, 0);
    values.put(Provider.K_ELENGTH, 0);
    values.put(Provider.K_EDATT, 0);
    values.put(Provider.K_EDFIN, 0);
    values.put(Provider.K_EDID, 0);
    Date date = episode.getPublicationDate();
    if (date != null) {
      values.put(Provider.K_EDATE, date.getTime());
    }
    values.put(Provider.K_EPID, subscriptionId);
    values.put(Provider.K_ID, id);
    values.put(Provider.K_ETSTAMP, timestamp);
    values.put(Provider.K_ESTATE, markNew ? Provider.ESTATE_NEW : Provider.ESTATE_GONE);
    try {
      provider.insert(Provider.episodeUri, values);
    } catch (RemoteException exception) {
      Log.e(TAG, "Episode insert failed: ", exception);
      return false;
    }

    // notify DownloadStartReceiver about new episode
    if (markNew) {
      Log.d(TAG, "New episode! " + title);
      Intent bi = new Intent(DownloadStartReceiver.NEW_EPISODE_ACTION);
      bi.putExtra(DownloadStartReceiver.URL_EXTRA_NAME, audioLink);
      bi.putExtra(DownloadStartReceiver.TITLE_EXTRA_NAME, title);
      bi.putExtra(DownloadStartReceiver.ID_EXTRA_NAME, id);
      PodListenApp.getContext().sendBroadcast(bi);
    }

    return true;
  }

  /**
   * @return feed title if success, null otherwise
   * @throws RemoteException
   */
  @Nullable
  private String updateFeed(long id, @NonNull Feed feed, long timestamp) throws RemoteException {
    ContentValues values = new ContentValues();
    String title = feed.getTitle();
    values.put(Provider.K_PURL, link);
    values.put(Provider.K_PNAME, title);
    String description = feed.getDescription();
    if (description != null) {
      values.put(Provider.K_PDESCR, simplifyHTML(description));
    }
    values.put(Provider.K_PSTATE, Provider.PSTATE_SEEN_ONCE);
    values.put(Provider.K_PTSTAMP, timestamp);
    String image = feed.getImageLink();
    if (!ImageManager.getInstance().isDownloaded(id) && image != null) {
      try {
        ImageManager.getInstance().download(id, new URL(image));
      } catch (IOException exception) {
        Log.w(TAG, image + ": Feed image download failed: ", exception);
      }
    }
    int updated = provider.update(Provider.getUri(Provider.T_PODCAST, id), values, null, null);
    return updated == 1 ? title : null;
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

  private boolean updateEpisodeTimestamp(long id, @NonNull ContentProviderClient provider,
                                         long timestamp) throws RemoteException {
    ContentValues values = new ContentValues();
    values.put(Provider.K_ETSTAMP, timestamp);
    return provider.update(Provider.getUri(Provider.T_EPISODE, id), values, null, null) > 0;
  }
}
