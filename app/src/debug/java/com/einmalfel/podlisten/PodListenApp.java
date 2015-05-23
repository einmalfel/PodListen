package com.einmalfel.podlisten;

import android.util.Log;

import com.einmalfel.podlisten.support.InstanceAwareApplication;
import com.facebook.stetho.Stetho;

public class PodListenApp extends InstanceAwareApplication {
  private static final String TAG = "PLA";
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "Initalizing stetho...");
    Stetho.initialize(Stetho.newInitializerBuilder(this)
        .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
        .enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
        .build());
  }
}
