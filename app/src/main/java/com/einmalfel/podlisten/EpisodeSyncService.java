package com.einmalfel.podlisten;

import android.content.Intent;
import android.os.IBinder;


public class EpisodeSyncService extends DebuggableService {
  private static EpisodesSyncAdapter adapter;

  @Override
  public void onCreate() {
    super.onCreate();
    if (adapter == null) {
      adapter = new EpisodesSyncAdapter(getApplicationContext(), true);
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return adapter.getSyncAdapterBinder();
  }
}
