package com.einmalfel.podlisten;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EpisodesSyncAdapter extends AbstractThreadedSyncAdapter {
  static final String FEED_ID_EXTRA_OPTION = "com.einmalfel.podlisten.FEED_ID";

  private static final String TAG = "SSA";

  /**
   * This much episodes will be added as new episodes when user adds subscription
   */
  private static final int MAX_NEW_EPISODES_NEW_FEED = 3;
  /**
   * This much episodes will be added as new episodes when user refreshes subscription
   */
  private static final int MAX_NEW_EPISODES_OLD_FEED = 50;

  private static final int WORKERS_NUMBER = 3;

  private static final int SYNC_TIMEOUT = 30 * 60; // [s]

  /**
   * don't update feeds more often than once per 5 minutes if sync wasn't started manually.
   * Thus, automatic sync immediately retried after soft error will process only failed feeds.
   * First retry occurs after 30 seconds, following ones double backoff (see SyncManager.java).
   */
  private static final int MINIMUM_SYNC_INTERVAL_MS = 5 * 60 * 1000;

  public EpisodesSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult) {
    Boolean manualSync = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
    Long requestedId = extras.getLong(FEED_ID_EXTRA_OPTION, 0);
    SyncState syncState = new SyncState(getContext(), syncResult);

    Cursor c = null;
    try {
      c = provider.query(
          requestedId == 0 ? Provider.podcastUri : Provider.getUri(Provider.T_PODCAST, requestedId),
          new String[]{Provider.K_ID, Provider.K_PFURL, Provider.K_PSTATE, Provider.K_PTSTAMP},
          null, null, null);
    } catch (RemoteException exception) {
      Log.e(TAG, "Failed to query podcast db", exception);
    }
    if (c == null) {
      syncResult.databaseError = true;
      syncState.error("DB error");
      return;
    }
    if (c.getCount() == 0) {
      Log.i(TAG, "No subscriptions, skipping sync");
      c.close();
      return;
    }

    syncState.start(c.getCount());

    ExecutorService executorService = Executors.newFixedThreadPool(WORKERS_NUMBER);
    while (c.moveToNext()) {
      long id = c.getLong(c.getColumnIndexOrThrow(Provider.K_ID));
      boolean newFeed = c.getInt(c.getColumnIndexOrThrow(Provider.K_PSTATE)) == Provider.PSTATE_NEW;
      String url = c.getString(c.getColumnIndexOrThrow(Provider.K_PFURL));
      long feedTimestamp = c.getLong(c.getColumnIndexOrThrow(Provider.K_PTSTAMP));

      if (!manualSync && (new Date().getTime() - feedTimestamp < MINIMUM_SYNC_INTERVAL_MS)) {
        Log.i(TAG, "Skipping feed refresh (syncing to often): " + id);
        syncState.signalFeedSuccess(null, 0);
        continue;
      }

      executorService.execute(new SyncWorker(
          id,
          url,
          provider,
          syncState,
          newFeed ? MAX_NEW_EPISODES_NEW_FEED : MAX_NEW_EPISODES_OLD_FEED));
    }
    c.close();

    executorService.shutdown();
    boolean workersDone = false;
    try {
      workersDone = executorService.awaitTermination(SYNC_TIMEOUT, TimeUnit.SECONDS);
    } catch (InterruptedException interrupt) {
      syncState.error("Refresh interrupted by system");
      // sync cancelled. Discard queue, try to interrupt workers and wait for them again
      executorService.shutdownNow();
      try {
        workersDone = executorService.awaitTermination(SYNC_TIMEOUT, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {}
    }
    if (!workersDone) {
      Log.e(TAG, "Some of workers hanged during sync");
    }

    syncState.stop();
  }
}
