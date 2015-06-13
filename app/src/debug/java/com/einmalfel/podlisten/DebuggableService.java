package com.einmalfel.podlisten;

import android.app.Service;

public abstract class DebuggableService extends Service {
  @Override
  public void onDestroy() {
    super.onDestroy();
    PodListenApp.getInstance().refWatcher.watch(this);
  }
}
