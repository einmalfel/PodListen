package com.einmalfel.podlisten;

import android.app.Application;
import android.util.Log;

import com.facebook.stetho.Stetho;

public class PodListenApp extends Application {
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
