package com.einmalfel.podlisten;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Html;
import android.util.Log;

import com.einmalfel.earl.EarlParser;
import com.einmalfel.earl.Enclosure;
import com.einmalfel.earl.Feed;
import com.einmalfel.earl.Item;

import org.unbescape.xml.XmlEscape;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
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
  private static final int TIMEOUT_MS = 15000;


  private final long id;
  private final String link;
  private final SyncState syncState;
  private final ContentProviderClient provider;
  private final Provider.RefreshMode refreshMode;

  public SyncWorker(long id, @NonNull String link, @NonNull ContentProviderClient provider,
                    @NonNull SyncState syncState, Provider.RefreshMode refreshMode) {
    this.id = id;
    this.link = link;
    this.provider = provider;
    this.syncState = syncState;
    this.refreshMode = refreshMode;
  }

  @Override
  public void run() {
    String title = null;
    try {
      InputStream inputStream = openConnectionWithTO(new URL(link)).getInputStream();
      Feed feed = EarlParser.parseOrThrow(inputStream, MAX_EPISODES_TO_PARSE);

      // Episodes need to be timestamped before subscriptions, otherwise cleanup algorithm may
      // delete fresh episodes in case of an exception between feed and episodes update
      long timestamp = new Date().getTime();

      int newEpisodesInserted = 0;
      for (Item episode : feed.getItems()) {
        boolean markNew = newEpisodesInserted < refreshMode.getCount();
        Date pubDate = episode.getPublicationDate();
        if (pubDate != null) {
          markNew &= new Date().getTime() - pubDate.getTime() < refreshMode.getMaxAge();
        }
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
        storeFeedError(new Exception("Failed to update podcast data in db"));
        syncState.signalDBError(link);
      }
    } catch (IOException exception) {
      storeFeedError(exception);
      syncState.signalIOError(link);
    } catch (RemoteException exception) {
      storeFeedError(exception);
      syncState.signalDBError(title == null ? link : title);
    } catch (DataFormatException | XmlPullParserException exception) {
      storeFeedError(exception);
      syncState.signalParseError(link);
    } catch (Exception exception) {
      storeFeedError(exception);
      syncState.signalIOError(title == null ? link : title);
    }
  }

  void storeFeedError(@NonNull Exception exception) {
    Log.w(TAG, "Failed to refresh " + link, exception);
    ContentValues contentValues = new ContentValues();
    contentValues.put(
        Provider.K_PERROR,
        exception.getLocalizedMessage() + " (" + exception.getClass().getSimpleName() + ")");
    contentValues.put(Provider.K_PSTATE, Provider.PSTATE_LAST_REFRESH_FAILED);
    try {
      provider.update(Provider.getUri(Provider.T_PODCAST, id), contentValues, null, null);
    } catch (RemoteException remoteException) {
      Log.e(TAG, "Failed to write refresh error details to DB", remoteException);
    }
  }

  /**@return true if episode was inserted, false in case of error or if episode was already in DB*/
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

    if (audioSize == null || audioSize < 10 * 1024) {
      try {
        audioSize = openConnectionWithTO(new URL(audioLink)).getContentLength();
      } catch (MalformedURLException ex) {
        Log.e(TAG,
              "Episode " + episode.getLink() + " has malformed URL: " + audioLink, ex);
        return false;
      } catch (IOException ex) {
        Log.e(TAG, "Leaving wrong episode size for " + episode.getLink(), ex);
      }
    }

    // put episode into DB
    ContentValues values = new ContentValues();
    values.put(Provider.K_ENAME, title);
    values.put(Provider.K_EAURL, audioLink);
    String description = episode.getDescription();
    if (description != null) {
      String simplifiedDescription = simplifyHTML(description);
      values.put(Provider.K_EDESCR, simplifiedDescription);
      values.put(Provider.K_ESDESCR, getShortDescription(simplifiedDescription));
    }
    values.put(Provider.K_EURL, episode.getLink());
    values.put(Provider.K_ESIZE, audioSize);
    values.put(Provider.K_EERROR, (String) null);
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
      String image = episode.getImageLink();
      if (!ImageManager.getInstance().isDownloaded(id) && image != null) {
        try {
          ImageManager.getInstance().download(id, new URL(image));
        } catch (IOException exception) {
          Log.w(TAG, image + ": Episode image download failed: ", exception);
        }
      }
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
    values.put(Provider.K_PFURL, link);
    values.put(Provider.K_PURL, feed.getLink());
    values.put(Provider.K_PNAME, title);
    // refresh mode is set for one refresh only, so always reset it to default value
    values.put(Provider.K_PRMODE, Provider.RefreshMode.ALL.ordinal());
    String description = feed.getDescription();
    if (description != null) {
      String simplifiedDescription = simplifyHTML(description);
      values.put(Provider.K_PDESCR, simplifiedDescription);
      values.put(Provider.K_PSDESCR, getShortDescription(simplifiedDescription));
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

  @NonNull
  private static String getShortDescription(@NonNull String htmlDescription) {
    String plain = Html.fromHtml(htmlDescription).toString();
    int pL = plain.length();
    return plain.substring(0, pL > Provider.SHORT_DESCR_LENGTH ? Provider.SHORT_DESCR_LENGTH : pL);
  }

  private static final Pattern brPattern = Pattern.compile("<br.*?/>\\s*<br.*/>|<p/?>|</li>");
  private static final Pattern erasePattern = Pattern.compile("<img.+?>|</p>|</?ul/?>");
  private static final Pattern listPattern = Pattern.compile("<li>");
  private static final Pattern spannedStartPattern = Pattern.compile("\\A<p dir=\"ltr\">");
  private static final Pattern spannedEndPattern = Pattern.compile("</p>\\z");

  @NonNull
  private static String simplifyHTML(@NonNull String text) {
    text = erasePattern.matcher(text).replaceAll("");
    text = listPattern.matcher(text).replaceAll("\u2022");
    text = brPattern.matcher(text).replaceAll("<br/>");

    // on next line we throw out tags not supported by spanned text
    text = Html.toHtml(Html.fromHtml(text));

    // There is a problem: toHtml returns escaped html, thus making resulting string much longer.
    // The only solution I found is to make use of unbescape library.
    text = XmlEscape.unescapeXml(text).trim();

    // toHtml frames result in <p dir="ltr">...</p> (direction could probably be locale-dependent).
    // TextView will display it as additional whitespace at the end of text, so try to delete it.
    if (spannedStartPattern.matcher(text).find() && spannedEndPattern.matcher(text).find()) {
      text = spannedStartPattern.matcher(text).replaceAll("");
      text = spannedEndPattern.matcher(text).replaceAll("");
    }

    return text.trim();
  }

  private boolean updateEpisodeTimestamp(long id, @NonNull ContentProviderClient provider,
                                         long timestamp) throws RemoteException {
    ContentValues values = new ContentValues();
    values.put(Provider.K_ETSTAMP, timestamp);
    return provider.update(Provider.getUri(Provider.T_EPISODE, id), values, null, null) > 0;
  }

  URLConnection openConnectionWithTO(URL url) throws IOException {
    URLConnection result = url.openConnection();
    result.setConnectTimeout(TIMEOUT_MS);
    result.setReadTimeout(TIMEOUT_MS);
    return result;
  }
}
