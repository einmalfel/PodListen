package com.einmalfel.podlisten.support;

import android.app.Application;
import android.content.Context;

public class InstanceAwareApplication extends Application {
  private static InstanceAwareApplication instance;

  public static InstanceAwareApplication getInstance() {
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
