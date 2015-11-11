package com.einmalfel.podlisten;

import android.content.ContentProviderClient;
import android.content.ContentValues;
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
import java.util.Calendar;
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
  private static final Date PODCAST_EPOCH;

  // there where no podcasts before year 2000. Earlier pubDate's will be replaced with current time
  static {
    Calendar calendar = Calendar.getInstance();
    calendar.set(2000, 0, 0);
    PODCAST_EPOCH = calendar.getTime();
  }

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
    try {
      InputStream inputStream = openConnectionWithTO(new URL(link)).getInputStream();
      Feed feed = EarlParser.parseOrThrow(inputStream, MAX_EPISODES_TO_PARSE);

      // Episodes need to be timestamped before subscriptions, otherwise cleanup algorithm may
      // delete fresh episodes in case of an exception between feed and episodes update
      Date timestamp = new Date();

      int newEpisodesInserted = 0;
      for (Item episode : feed.getItems()) {
        boolean markNew = newEpisodesInserted < refreshMode.getCount();
        Date pubDate = episode.getPublicationDate();
        if (pubDate != null) {
          markNew &= timestamp.getTime() - pubDate.getTime() < refreshMode.getMaxAge();
        }
        if (tryInsertEpisode(episode, id, timestamp, provider, markNew) && markNew) {
          newEpisodesInserted++;
        }
      }

      updateFeed(id, feed, timestamp);

      // delete every gone episode whose timestamp is less then feeds timestamp
      PodcastOperations.cleanupEpisodes(PodListenApp.getContext(), Provider.ESTATE_GONE);

    } catch (IOException exception) {
      storeFeedError(exception);
      syncState.signalIOError(link);
    } catch (RemoteException exception) {
      storeFeedError(exception);
      syncState.signalDBError(link);
    } catch (DataFormatException | XmlPullParserException exception) {
      storeFeedError(exception);
      syncState.signalParseError(link);
    } catch (Exception exception) {
      storeFeedError(exception);
      syncState.signalIOError(link);
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

  @NonNull
  private Date correctDate(@Nullable Date date, @NonNull Date current) {
    return date == null || date.after(current) || date.before(PODCAST_EPOCH) ? current : date;
  }

  /** @return true if episode was inserted, false in case of error or if episode was already in DB */
  private boolean tryInsertEpisode(
      @NonNull Item episode, long subscriptionId, @NonNull Date timestamp,
      @NonNull ContentProviderClient provider, boolean markNew) {
    String title = episode.getTitle();
    if (title == null) {
      title = PodListenApp.getContext().getString(R.string.episode_no_title);
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
    values.put(Provider.K_EDTSTAMP, 0);
    values.put(Provider.K_EDFIN, 0);
    values.put(Provider.K_EDID, 0);
    values.put(Provider.K_EDATE, correctDate(episode.getPublicationDate(), timestamp).getTime());
    values.put(Provider.K_EPID, subscriptionId);
    values.put(Provider.K_ID, id);
    values.put(Provider.K_ETSTAMP, timestamp.getTime());
    values.put(Provider.K_ESTATE, markNew ? Provider.ESTATE_NEW : Provider.ESTATE_GONE);
    try {
      provider.insert(Provider.episodeUri, values);
    } catch (RemoteException exception) {
      Log.e(TAG, "Episode insert failed: ", exception);
      return false;
    }

    // notify DownloadReceiver about new episode
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
    }

    return true;
  }

  private void updateFeed(long id, @NonNull Feed feed, @NonNull Date timestamp)
      throws RemoteException {
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
    values.put(Provider.K_PTSTAMP, timestamp.getTime());
    String image = feed.getImageLink();
    if (!ImageManager.getInstance().isDownloaded(id) && image != null) {
      try {
        ImageManager.getInstance().download(id, new URL(image));
      } catch (IOException exception) {
        Log.w(TAG, image + ": Feed image download failed: ", exception);
      }
    }
    if (provider.update(Provider.getUri(Provider.T_PODCAST, id), values, null, null) != 1) {
      throw new RemoteException("Failed to update database with new podcast data");
    }
  }

  @NonNull
  private static String getShortDescription(@NonNull String htmlDescription) {
    String plain = Html.fromHtml(htmlDescription).toString();
    int pL = plain.length();
    return plain.substring(0, pL > Provider.SHORT_DESCR_LENGTH ? Provider.SHORT_DESCR_LENGTH : pL);
  }

  // use <br[^>]*> instead of <br.*?> because <br.*?><bt.*?> will match <br/><sometag><br/>
  private static final String BR_TAG = "</?br[^>]*>";
  private static final Pattern listPattern = Pattern.compile("<li[^>]*>");
  private static final Pattern brPattern = Pattern.compile("</?img[^>]*>|</?li[^>]*>|\\n");
  private static final Pattern paragraphPattern = Pattern.compile("</?p[^>]*>");
  private static final Pattern trimStartPattern = Pattern.compile("\\A(\\s|" + BR_TAG + ")*");
  private static final Pattern trimEndPattern = Pattern.compile("(\\s|" + BR_TAG + ")*\\Z");
  private static final Pattern brRepeatPattern = Pattern.compile("(\\s*" + BR_TAG + "\\s*)+");

  @NonNull
  static String simplifyHTML(@NonNull String text) {
    // replace opening <li> tag with bullet symbol. Otherwise <li> will be thrown out by Html.toHtml
    text = listPattern.matcher(text).replaceAll("\u2022");

    // replace \n and </li> with line breaks. Need all LF tokens to be <br> to reduce them later
    text = brPattern.matcher(text).replaceAll("<br/>");

    // throw out tags not supported by spanned text
    text = Html.toHtml(Html.fromHtml(text));

    // toHtml may add some excess <p> tags
    text = paragraphPattern.matcher(text).replaceAll("<br/>");

    // There is a problem: toHtml returns escaped html, thus making resulting string much longer.
    // The only solution I found is to make use of unbescape library.
    text = XmlEscape.unescapeXml(text);

    // trim all whitespaces and <br>'s at the start and at the end
    text = trimEndPattern.matcher(text).replaceAll("");
    text = trimStartPattern.matcher(text).replaceAll("");

    // reduce repeated <br>'s
    text = brRepeatPattern.matcher(text).replaceAll("<br/>");
    return text;
  }

  private boolean updateEpisodeTimestamp(long id, @NonNull ContentProviderClient provider,
                                         @NonNull Date timestamp) throws RemoteException {
    ContentValues values = new ContentValues();
    values.put(Provider.K_ETSTAMP, timestamp.getTime());
    return provider.update(Provider.getUri(Provider.T_EPISODE, id), values, null, null) > 0;
  }

  URLConnection openConnectionWithTO(URL url) throws IOException {
    URLConnection result = url.openConnection();
    result.setConnectTimeout(TIMEOUT_MS);
    result.setReadTimeout(TIMEOUT_MS);
    return result;
  }
}
