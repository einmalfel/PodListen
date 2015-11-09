package com.einmalfel.podlisten;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Date;


public class PodcastOperations extends IntentService {
  private static final String TAG = "POS";

  private static final String ACTION_CLEANUP_EPISODES = "com.einmalfel.podlisten.CLEANUP_EPISODES";
  private static final String ACTION_SET_STATE = "com.einmalfel.podlisten.SET_STATE";
  private static final String ACTION_HANDLE_DOWNLOADS = "com.einmalfel.podlisten.HANDLE_DOWNLOADS";

  private static final String EXTRA_EPISODE_STATE = "com.einmalfel.podlisten.EPISODE_STATE";
  private static final String EXTRA_EPISODE_FILTER = "com.einmalfel.podlisten.EPISODE_FILTER";

  public static void handleDownloads(Context context) {
    Intent intent = new Intent(context, PodcastOperations.class);
    intent.setAction(ACTION_HANDLE_DOWNLOADS);
    context.startService(intent);
  }

  public static void setEpisodesState(@NonNull Context context, int state, int stateFilter) {
    Intent intent = new Intent(context, PodcastOperations.class);
    intent.setAction(ACTION_SET_STATE);
    intent.putExtra(EXTRA_EPISODE_FILTER, stateFilter);
    intent.putExtra(EXTRA_EPISODE_STATE, state);
    context.startService(intent);
  }

  /** deletes episodes whose state == stateFilter */
  public static void cleanupEpisodes(@NonNull Context context, int stateFilter) {
    Intent intent = new Intent(context, PodcastOperations.class);
    intent.setAction(ACTION_CLEANUP_EPISODES);
    intent.putExtra(EXTRA_EPISODE_STATE, stateFilter);
    context.startService(intent);
  }

  public PodcastOperations() {
    super("PodcastOperations");
    setIntentRedelivery(true);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    if (intent != null) {
      final String action = intent.getAction();
      Log.i(TAG, "Processing " + action);
      switch (action) {
        case ACTION_SET_STATE:
          setEpisodesState(intent.getIntExtra(EXTRA_EPISODE_STATE, Provider.ESTATE_GONE),
                           intent.getIntExtra(EXTRA_EPISODE_FILTER, Provider.ESTATE_GONE));
          break;
        case ACTION_CLEANUP_EPISODES:
          cleanupEpisodes(intent.getIntExtra(EXTRA_EPISODE_STATE, Provider.ESTATE_GONE));
          break;
        case ACTION_HANDLE_DOWNLOADS:
          handleDownloads();
          break;
        default:
          Log.wtf(TAG, "Unexpected intent action: " + action);
      }
    }
  }

  private void cleanupEpisodes(int stateFilter) {
    ContentResolver resolver = getContentResolver();
    Cursor cursor = resolver.query(
        Provider.episodeJoinPodcastUri,
        new String[]{Provider.K_EID, Provider.K_ETSTAMP, Provider.K_PTSTAMP, Provider.K_EDID},
        Provider.K_ESTATE + " == " + stateFilter,
        null,
        null
    );
    if (cursor == null) {
      Log.wtf(TAG, "Provider query returned null");
      return;
    }
    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
    while (cursor.moveToNext()) {
      // 1. Stop download if any
      long dId = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EDID));
      if (dId != 0) {
        dm.remove(dId);
      }
      // 2. Delete audio and images related to this episode if any
      long episodeId = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_ID));
      Storage storage = Preferences.getInstance().getStorage();
      if (storage == null || !storage.isAvailableRW()) {
        Log.w(TAG, "failed to delete episode media: no storage or it isn't writable");
        continue;
      }
      File f = new File(storage.getPodcastDir(), Long.toString(episodeId));
      if (f.exists() && !f.delete()) {
        Log.w(TAG, "Failed to delete " + f.toURI());
      }
      ImageManager.getInstance().deleteImage(episodeId);
      // 3. Set gone state or completely remove episode from db if it is already absent in the feed
      if (cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_ETSTAMP)) <
          cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_PTSTAMP))) {
        Log.i(TAG, "Feed doesn't contain episode " + episodeId + " anymore. Deleting from db..");
        cursor.close();
        if (resolver.delete(Provider.getUri(Provider.T_EPISODE, episodeId), null, null) != 1) {
          Log.w(TAG, "Failed to delete " + episodeId + " from db");
        }
      } else {
        ContentValues val = new ContentValues(3);
        val.put(Provider.K_ESTATE, Provider.ESTATE_GONE);
        val.put(Provider.K_EDFIN, 0);
        val.put(Provider.K_EDID, 0);
        if (resolver.update(Provider.getUri(Provider.T_EPISODE, episodeId), val, null, null) != 1) {
          Log.w(TAG, "Failed to set GONE state for episode " + episodeId);
        }
      }
    }
    cursor.close();
  }

  private void setEpisodesState(int state, int stateFilter) {
    ContentValues cv = new ContentValues(1);
    cv.put(Provider.K_ESTATE, state);
    int result = getContentResolver().update(Provider.episodeUri,
                                             cv,
                                             Provider.K_ESTATE + " == " + stateFilter,
                                             null);
    Log.i(TAG, "Switched state from " + stateFilter + " to " + state + " for " + result + " eps");
  }

  private void handleDownloads() {
    Storage currentStorage = Preferences.getInstance().getStorage();
    if (currentStorage == null) {
      Log.w(TAG, "No reason to handle downloads now, no storage available)");
      return;
    }
    Cursor cursor = getContentResolver().query(
        Provider.episodeUri,
        new String[]{Provider.K_EDFIN, Provider.K_ID, Provider.K_EDATT},
        Provider.K_EDFIN + " IN (" + Provider.EDFIN_MOVING + ", " + Provider.EDFIN_PROCESSING + ")",
        null,
        null
    );
    if (cursor == null) {
      Log.wtf(TAG, "Provider query returned null");
      return;
    }

    while (cursor.moveToNext()) {
      long epId = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_ID));
      int dFinished = cursor.getInt(cursor.getColumnIndexOrThrow(Provider.K_EDFIN));
      int attempts = cursor.getInt(cursor.getColumnIndexOrThrow(Provider.K_EDATT));
      File downloadLocation = new File(currentStorage.getPodcastDir(), Long.toString(epId));
      ContentValues cv = new ContentValues();
      cv.put(Provider.K_EDATT, attempts + 1);
      cv.put(Provider.K_EDTSTAMP, new Date().getTime());
      if (dFinished == Provider.EDFIN_MOVING) {
        File tempFile = new File(Storage.getPrimaryStorage().getPodcastDir(), Long.toString(epId));
        try {
          Log.i(TAG, "Moving file from temporary location to current storage");
          moveFile(tempFile, downloadLocation);
        } catch (IOException exception) {
          setDownloadErrorCode(epId, Provider.EDFIN_ERROR, cv);
          continue;
        }
      }
      if (!isDownloadedFileOk(downloadLocation)) {
        setDownloadErrorCode(epId, Provider.EDFIN_ERROR, cv);
        continue;
      }
      cv.put(Provider.K_EDFIN, Provider.EDFIN_COMPLETE);
      cv.put(Provider.K_ESIZE, downloadLocation.length());
      long duration = getFileLength(downloadLocation);
      if (duration != 0) {
        cv.put(Provider.K_ELENGTH, duration);
      }
      if (getContentResolver().update(Provider.getUri(Provider.T_EPISODE, epId), cv, null, null) != 1) {
        Log.e(TAG, "Failed to update db row for episode " + epId);
      } else {
        Log.i(TAG, "Successfully downloaded " + epId);
      }
    }
    cursor.close();
  }

  private long getFileLength(File file) {
    long duration = 0;
    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
    // setDataSource may throw RuntimeException for damaged media file
    try {
      mmr.setDataSource(file.getPath());
      String durationString = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      if (durationString != null) {
        try {
          duration = Long.parseLong(durationString);
        } catch (NumberFormatException ignored) {
          Log.e(TAG, file + ": Wrong duration metadata: " + durationString);
        }
      }
    } catch (RuntimeException exception) {
      Log.e(TAG, "Failed to get duration of " + file, exception);
    } finally {
      mmr.release();
    }
    return duration;
  }

  private void setDownloadErrorCode(long episodeId, int dFinValue, @NonNull ContentValues cv) {
    Log.e(TAG, "Download failed. Error code: " + dFinValue);
    cv.put(Provider.K_EDFIN, Provider.EDFIN_ERROR);
    cv.put(Provider.K_EERROR, "Download failed. Error code: " + dFinValue);
    getContentResolver().update(Provider.getUri(Provider.T_EPISODE, episodeId), cv, null, null);
  }

  /**
   * Sometimes body of redirect response is downloaded instead of media file (seen this on xperia
   * Z2 with moscow metro wifi). Such body could be empty or could contain some html code.
   * If downloaded file size is less then 1kB, consider it is an error. If file size is between
   * 1kB and 5MB, check if it starts with < and ends with > (which means it's html/xml).
   */
  private boolean isDownloadedFileOk(@NonNull File file) {
    try {
      long length = file.length();
      if (length < 1024) {
        return false; // it's to small to be audio file
      } else if (length < 5 * 1024 * 1024) {
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");

        for (long offset = 0; offset < length; offset++) {
          randomAccessFile.seek(offset);
          char firstChar = (char) randomAccessFile.readByte();
          if (!Character.isWhitespace(firstChar)) {
            if (firstChar == '<') {
              break; // file begins with \s*<, now check if it ends with >\s*
            } else {
              return true;
            }
          }
        }

        for (long offset = length - 1; offset >= 0; offset--) {
          randomAccessFile.seek(offset);
          char lastChar = (char) randomAccessFile.readByte();
          if (!Character.isWhitespace(lastChar)) {
            return lastChar != '>';
          }
        }

        return false; // file consists of whitespaces. It's probably not media
      } else {
        return true; // file is big enough, it's probably media, not html
      }
    } catch (IOException exception) {
      Log.e(TAG, "Error while checking downloaded file", exception);
      return false;
    }
  }

  public static void moveFile(File source, File destination) throws IOException {
    FileChannel outChannel = null;
    FileChannel inChannel = null;
    try {
      inChannel = new FileInputStream(source).getChannel();
      outChannel = new FileOutputStream(destination).getChannel();
      inChannel.transferTo(0, inChannel.size(), outChannel);
      if (!source.delete()) {
        throw new IOException("Failed to delete " + source);
      }
    } finally {
      if (inChannel != null)
        inChannel.close();
      if (outChannel != null)
        outChannel.close();
    }
  }
}
