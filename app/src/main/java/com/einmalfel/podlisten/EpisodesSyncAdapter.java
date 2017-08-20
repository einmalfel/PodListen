package com.einmalfel.podlisten;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
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

  private static final int WORKERS_NUMBER = 3;

  private static final int SYNC_TIMEOUT = 30 * 60; // [s]

  private static final String[] queryColumns = new String[]{
      Provider.K_ID, Provider.K_PFURL, Provider.K_PSTATE, Provider.K_PTSTAMP, Provider.K_PRMODE};

  public EpisodesSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult) {
    Boolean manualSync = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
    Long requestedId = extras.getLong(FEED_ID_EXTRA_OPTION, 0);
    syncResult.tooManyRetries = extras.getBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, false);
    SyncState syncState = new SyncState(getContext(), syncResult);

    Cursor cursor = null;
    try {
      cursor = provider.query(
          requestedId == 0 ? Provider.podcastUri : Provider.getUri(Provider.T_PODCAST, requestedId),
          queryColumns,
          null, null, null);
    } catch (RemoteException exception) {
      Log.e(TAG, "Failed to query podcast db", exception);
    }
    if (cursor == null) {
      syncResult.databaseError = true;
      syncState.error(getContext().getString(R.string.sync_database_error));
      return;
    }
    if (cursor.getCount() == 0) {
      Log.i(TAG, "No subscriptions, skipping sync");
      cursor.close();
      return;
    }

    syncState.start(cursor.getCount());

    ExecutorService executorService = Executors.newFixedThreadPool(WORKERS_NUMBER);
    while (cursor.moveToNext()) {
      long id = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_ID));
      String url = cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_PFURL));
      long feedTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_PTSTAMP));
      RefreshMode refreshMode = RefreshMode.values()[cursor.getInt(
          cursor.getColumnIndexOrThrow(Provider.K_PRMODE))];

      // If auto-sync is invoked more often then once in sync interval, it's sync retry and sync
      // adapter should process only feeds that failed to refresh on previous run.
      long syncPeriodMs = Preferences.getInstance().getRefreshInterval().periodSeconds * 1000;
      if (!manualSync && (new Date().getTime() - feedTimestamp < syncPeriodMs)) {
        Log.i(TAG, "Skipping feed refresh (syncing to often): " + id);
        syncState.signalFeedSuccess(null, 0);
        continue;
      }

      executorService.execute(new SyncWorker(id, url, provider, syncState, refreshMode));
    }
    cursor.close();

    executorService.shutdown();
    boolean workersDone = false;
    try {
      workersDone = executorService.awaitTermination(SYNC_TIMEOUT, TimeUnit.SECONDS);
    } catch (InterruptedException interrupt) {
      syncState.error(getContext().getString(R.string.sync_interrupted_by_system));
      // sync cancelled. Discard queue, try to interrupt workers and wait for them again
      executorService.shutdownNow();
      try {
        workersDone = executorService.awaitTermination(SYNC_TIMEOUT, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
        Log.e(TAG, "Failed to interrupt workers");
      }
    }
    if (!workersDone) {
      Log.e(TAG, "Some of workers hanged during sync");
    } else {
      getContext().sendBroadcast(new Intent(DownloadReceiver.UPDATE_QUEUE_ACTION));
    }

    syncState.stop();
  }
}
