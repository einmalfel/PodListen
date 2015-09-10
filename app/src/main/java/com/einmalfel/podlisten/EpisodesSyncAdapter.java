package com.einmalfel.podlisten;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EpisodesSyncAdapter extends AbstractThreadedSyncAdapter {
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


  public EpisodesSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult) {
    SyncState syncState = new SyncState(getContext(), syncResult);

    Cursor c = null;
    try {
      c = provider.query(Provider.podcastUri,
                         new String[]{Provider.K_ID, Provider.K_PFURL, Provider.K_PSTATE},
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
      boolean newFeed = c.getInt(c.getColumnIndexOrThrow(Provider.K_PSTATE)) == Provider.PSTATE_NEW;
      executorService.execute(new SyncWorker(
          c.getLong(c.getColumnIndexOrThrow(Provider.K_ID)),
          c.getString(c.getColumnIndexOrThrow(Provider.K_PFURL)),
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
