package com.einmalfel.podlisten;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

/**
 * This class keeps records on sync errors and manages sync notification
 */
public class SyncState {
  private static final int NOTIFICATION_ID = 1;
  private final SyncResult syncResult;
  private final NotificationManagerCompat nm;
  private final NotificationCompat.Builder nb;
  private final Context context;
  private int maxFeeds = 0;
  private int errors = 0;
  private int parsed = 0;
  private int newEpisodes = 0;
  private boolean stopped = false;

  SyncState(@NonNull Context context, @NonNull SyncResult syncResult) {
    this.syncResult = syncResult;
    this.context = context;
    nm = NotificationManagerCompat.from(context);
    nb = new NotificationCompat.Builder(context);
    Intent intent = new Intent(context, MainActivity.class);
    intent.putExtra(MainActivity.PAGE_LAUNCH_OPTION, MainActivity.Pages.NEW_EPISODES.ordinal());
    PendingIntent pendingIntent = PendingIntent.getActivity(
        context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    nb.setSmallIcon(R.mipmap.ic_sync_white_24dp)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(pendingIntent);
  }

  synchronized void start(int maxFeeds) {
    this.maxFeeds = maxFeeds;
    nb.setContentTitle(context.getString(R.string.sync_running))
      .setOngoing(true)
      .setAutoCancel(false)
      .setProgress(0, 0, true);
    updateNotification();
  }

  synchronized void error(String message) {
    nb.setOngoing(false)
      .setAutoCancel(true)
      .setProgress(0, 0, false)
      .setContentTitle(context.getString(R.string.sync_failed))
      .setContentText(message);
    updateNotification();
    stopped = true;
  }

  synchronized void stop() {
    // don't keep "refreshed" notification user if main activity is on screen
    String currentActivity = Preferences.getInstance().getCurrentActivity(true);
    if (MainActivity.class.getSimpleName().equals(currentActivity) && errors == 0) {
      stopped = true;
      nm.cancel(NOTIFICATION_ID);
      return;
    }

    Cursor cursor = context.getContentResolver().query(
        Provider.episodeUri,
        null,
        Provider.K_ESTATE + " == " + Provider.ESTATE_NEW,
        null,
        null,
        null);
    StringBuilder stringBuilder = new StringBuilder();
    if (cursor == null) {
      stringBuilder.append(context.getString(R.string.sync_database_error));
    } else {
      int count = cursor.getCount();
      cursor.close();

      String newEpisodesCount;
      if (count == newEpisodes) {
        newEpisodesCount = Integer.toString(newEpisodes);
      } else {
        newEpisodesCount = Integer.toString(count);
        if (newEpisodes > 0) {
          newEpisodesCount += "(+" + newEpisodes + ")";
        }
      }
      stringBuilder.append(context.getString(R.string.sync_new_episodes, newEpisodesCount));

      if (parsed > 0) {
        stringBuilder.append(", ")
                     .append(context.getString(R.string.sync_feeds_synced, parsed));
      }
      if (errors > 0) {
        stringBuilder.append(", ")
                     .append(context.getString(R.string.sync_feeds_failed, errors));
      }
    }
    nb.setOngoing(false)
      .setAutoCancel(true)
      .setProgress(0, 0, false)
      .setContentTitle(context.getString(R.string.sync_finished))
      .setContentText(stringBuilder);
    updateNotification();
    stopped = true;
  }

  private synchronized void updateProgress(String message) {
    nb.setProgress(maxFeeds, errors + parsed, false);
    nb.setContentText(message);
    updateNotification();
  }


  synchronized void signalParseError(String feedTitle) {
    syncResult.stats.numSkippedEntries++;
    errors++;
    updateProgress(context.getString(R.string.sync_feed_parsing_failed, feedTitle));
  }

  synchronized void signalDbError(String feedTitle) {
    syncResult.databaseError = true;
    errors++;
    updateProgress(context.getString(R.string.sync_feed_db_error, feedTitle));
  }

  synchronized void signalIoError(String feedTitle) {
    syncResult.stats.numIoExceptions++;
    errors++;
    updateProgress(context.getString(R.string.sync_feed_io_error, feedTitle));
  }

  synchronized void signalFeedSuccess(@Nullable String feedTitle, int episodesAdded) {
    syncResult.stats.numUpdates++;
    parsed++;
    newEpisodes += episodesAdded;
    if (feedTitle != null) {
      updateProgress(context.getString(R.string.sync_feed_synced, feedTitle));
    }
  }

  private synchronized void updateNotification() {
    if (!stopped) {
      nm.notify(NOTIFICATION_ID, nb.build());
    }
  }
}
