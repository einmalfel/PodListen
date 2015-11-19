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


public class BackgroundOperations extends IntentService {
  private static final String TAG = "BGS";

  private static final String ACTION_CLEANUP_EPISODES = "com.einmalfel.podlisten.CLEANUP_EPISODES";
  private static final String ACTION_HANDLE_DOWNLOADS = "com.einmalfel.podlisten.HANDLE_DOWNLOADS";

  private static final String EXTRA_EPISODE_STATE = "com.einmalfel.podlisten.EPISODE_STATE";

  public static void handleDownloads(Context context) {
    Intent intent = new Intent(context, BackgroundOperations.class);
    intent.setAction(ACTION_HANDLE_DOWNLOADS);
    context.startService(intent);
  }

  /** deletes episodes whose state == stateFilter */
  public static void cleanupEpisodes(@NonNull Context context, int stateFilter) {
    Intent intent = new Intent(context, BackgroundOperations.class);
    intent.setAction(ACTION_CLEANUP_EPISODES);
    intent.putExtra(EXTRA_EPISODE_STATE, stateFilter);
    context.startService(intent);
  }

  public BackgroundOperations() {
    super("BackgroundOperations");
    setIntentRedelivery(true);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    if (intent != null) {
      final String action = intent.getAction();
      Log.i(TAG, "Processing " + action);
      switch (action) {
        case ACTION_CLEANUP_EPISODES:
          cleanupEpisodes(intent.getIntExtra(EXTRA_EPISODE_STATE, Provider.ESTATE_GONE));
          break;
        case ACTION_HANDLE_DOWNLOADS:
          handleDownloads();
          break;
        default:
          Log.wtf(TAG, "Unexpected intent action: " + action);
      }
      Log.i(TAG, "Finished processing of " + action);
    }
  }

  private void cleanupEpisodes(int stateFilter) {
    ContentResolver resolver = getContentResolver();
    String where = Provider.K_ESTATE + " == " + stateFilter;
    if (stateFilter == Provider.ESTATE_GONE) {
      // only process ones that aren't included in feed anymore OR have media associated with them
      where += " AND (" + Provider.K_ETSTAMP + " < " + Provider.K_PTSTAMP + " OR " +
          Provider.K_EDFIN + " != 0 OR " + Provider.K_EDID + " != 0)";
    }
    Cursor cursor = resolver.query(
        Provider.episodeJoinPodcastUri,
        new String[]{Provider.K_EID, Provider.K_ETSTAMP, Provider.K_PTSTAMP, Provider.K_EDID},
        where,
        null,
        null
    );
    if (cursor == null) {
      Log.wtf(TAG, "Provider query returned null");
      return;
    }
    Log.i(TAG, "Cleaning up " + cursor.getCount() + " episodes");
    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
    boolean downloadsStopped = false;
    while (cursor.moveToNext()) {
      long episodeId = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_ID));
      // 1. Stop download if any
      long dId = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EDID));
      if (dId != 0) {
        dm.remove(dId);
        ContentValues val = new ContentValues(1);
        val.put(Provider.K_EDID, 0);
        if (resolver.update(Provider.getUri(Provider.T_EPISODE, episodeId), val, null, null) != 1) {
          Log.w(TAG, "Failed to reset DID for episode " + episodeId);
        }
        downloadsStopped = true;
      }
      // 2. Delete audio and images related to this episode if any
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
    if (downloadsStopped) {
      sendBroadcast(new Intent(DownloadReceiver.UPDATE_QUEUE_ACTION));
    }
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
          Log.i(TAG, "Moving file from " + tempFile + " to " + downloadLocation);
          moveFile(tempFile, downloadLocation);
        } catch (IOException exception) {
          Log.e(TAG, "Failed to move file from temporary storage", exception);
          setDownloadErrorCode(epId, Provider.EDFIN_ERROR, cv);
          continue;
        }
      }
      if (!isDownloadedFileOk(downloadLocation)) {
        Log.e(TAG, "Bad data received for episode " + epId);
        setDownloadErrorCode(epId, Provider.EDFIN_ERROR, cv);
        continue;
      }
      cv.put(Provider.K_EDFIN, Provider.EDFIN_COMPLETE);
      cv.put(Provider.K_ESIZE, downloadLocation.length());
      long duration = getFileLength(downloadLocation);
      if (duration != 0) {
        cv.put(Provider.K_ELENGTH, duration);
      }
      if (getContentResolver()
          .update(Provider.getUri(Provider.T_EPISODE, epId), cv, null, null) != 1) {
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
        Log.e(TAG, file + " is too small to be an audio file");
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
            if (lastChar == '>') {
              Log.e(TAG, file + ": XML/HTML downloaded instead of audio");
              return false;
            } else {
              return true;
            }
          }
        }

        Log.e(TAG, file + " consists of whitespaces only");
        return false;
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
    } finally {
      if (inChannel != null)
        inChannel.close();
      if (!source.delete()) {
        Log.e(TAG, "Failed to delete source " + source);
      }
      if (outChannel != null)
        outChannel.close();
    }
  }
}
