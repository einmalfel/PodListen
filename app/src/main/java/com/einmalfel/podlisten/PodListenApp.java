package com.einmalfel.podlisten;

import android.content.Context;

public class PodListenApp extends DebuggableApp {
  private static PodListenApp instance;

  public static PodListenApp getInstance() {
    return instance;
  }

  public static Context getContext() {
    return instance.getApplicationContext();
  }

  @Override
  public void onCreate() {
    instance = this;
    super.onCreate();
  }
}
