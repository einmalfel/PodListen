package com.einmalfel.podlisten;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;


public class PodcastOperations extends IntentService {
  private static final String TAG = "POS";

  private static final String ACTION_CLEANUP_EPISODES = "com.einmalfel.podlisten.CLEANUP_EPISODES";
  private static final String ACTION_SET_STATE = "com.einmalfel.podlisten.SET_STATE";

  private static final String EXTRA_EPISODE_STATE = "com.einmalfel.podlisten.EPISODE_STATE";
  private static final String EXTRA_EPISODE_FILTER = "com.einmalfel.podlisten.EPISODE_FILTER";


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
}
