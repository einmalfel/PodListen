package com.einmalfel.podlisten;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import javax.annotation.Nonnull;

/**
 * This class keeps records on sync errors and manages sync notification
 */
public class SyncState {
  private static final int NOTIFICATION_ID = 0;
  private final SyncResult syncResult;
  private final NotificationManagerCompat nm;
  private final NotificationCompat.Builder nb;
  private int maxFeeds = 0;
  private int errors = 0;
  private int parsed = 0;
  private int newEpisodes = 0;
  private boolean stopped = false;

  SyncState(@Nonnull Context context, @Nonnull SyncResult syncResult) {
    this.syncResult = syncResult;
    nm = NotificationManagerCompat.from(context);
    nb = new NotificationCompat.Builder(context);
    Intent intent = new Intent(context, MainActivity.class);
    intent.putExtra(MainActivity.PAGE_LAUNCH_OPTION, MainActivity.Pages.NEW_EPISODES.ordinal());
    PendingIntent pendingIntent = PendingIntent.getActivity(
        context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    nb.setSmallIcon(R.mipmap.ic_sync_green_24dp)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(pendingIntent);
  }

  synchronized void start(int maxFeeds) {
    this.maxFeeds = maxFeeds;
    nb.setContentTitle("Refreshing PodListen..")
      .setOngoing(true)
      .setAutoCancel(false)
      .setProgress(0, 0, true);
    updateNotification();
  }

  synchronized void error(String message) {
    nb.setOngoing(false)
      .setAutoCancel(true)
      .setProgress(0, 0, false)
      .setContentTitle("Refresh failed")
      .setContentText(message);
    updateNotification();
    stopped = true;
  }

  synchronized void stop() {
    StringBuilder stringBuilder = new StringBuilder(newEpisodes + " episode(s) added");
    if (parsed > 0) {
      stringBuilder.append(", ").append(parsed).append(" feed(s) refreshed");
    }
    if (errors > 0) {
      stringBuilder.append(", ").append(errors).append(" feed(s) failed to refresh");
    }
    nb.setOngoing(false)
      .setAutoCancel(true)
      .setProgress(0, 0, false)
      .setContentTitle("Podlisten refreshed")
      .setContentText(stringBuilder);
    updateNotification();
    stopped = true;
  }

  synchronized private void updateProgress(String message) {
    nb.setProgress(maxFeeds, errors + parsed, false);
    nb.setContentText(message);
    updateNotification();
  }


  synchronized void signalParseError(String feedTitle) {
    syncResult.stats.numSkippedEntries++;
    errors++;
    updateProgress("Parsing failed: " + feedTitle);
  }

  synchronized void signalDBError(String feedTitle) {
    syncResult.databaseError = true;
    errors++;
    updateProgress("DB error: " + feedTitle);
  }

  synchronized void signalIOError(String feedTitle) {
    syncResult.stats.numIoExceptions++;
    errors++;
    updateProgress("IO error: " + feedTitle);
  }

  synchronized void signalFeedSuccess(String feedTitle, int episodesAdded) {
    syncResult.stats.numUpdates++;
    parsed++;
    newEpisodes += episodesAdded;
    updateProgress("Loaded: " + feedTitle);
  }

  synchronized private void updateNotification() {
    if (!stopped) {
      nm.notify(NOTIFICATION_ID, nb.build());
    }
  }
}
