package com.einmalfel.podlisten;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(
    mailTo = "einmalfel@gmail.com",
    logcatArguments = {"-t", "1000", "-v", "time", "*:D"},
    customReportContent = {
        ReportField.USER_CRASH_DATE, ReportField.AVAILABLE_MEM_SIZE, ReportField.TOTAL_MEM_SIZE,
        ReportField.BUILD, ReportField.BUILD_CONFIG, ReportField.DISPLAY,
        ReportField.CRASH_CONFIGURATION, ReportField.SHARED_PREFERENCES, ReportField.LOGCAT,
        ReportField.STACK_TRACE},
    mode = ReportingInteractionMode.DIALOG,
    resDialogText = R.string.crash_dialog_text,
    resDialogTitle = R.string.crash_dialog_title,
    resDialogOkToast = R.string.crash_dialog_ok_toast
)
public class PodListenApp extends DebuggableApp implements Application.ActivityLifecycleCallbacks {
  private static volatile PodListenApp instance;

  public static PodListenApp getInstance() {
    return instance;
  }

  public static Application getContext() {
    if (instance == null) {
      Log.wtf("APP", "Getting context before Application.onCreate()", new NullPointerException());
    }
    return instance;
  }

  @Override
  public void onCreate() {
    instance = this;
    ACRA.init(this);
    registerActivityLifecycleCallbacks(this);
    super.onCreate();
  }

  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

  @Override
  public void onActivityResumed(Activity activity) {
    Preferences.getInstance().setCurrentActivity(activity.getLocalClassName());
  }

  @Override
  public void onActivityStarted(Activity activity) {}

  @Override
  public void onActivityPaused(Activity activity) {
    Preferences.getInstance().setCurrentActivity(null);
  }

  @Override
  public void onActivityStopped(Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

  @Override
  public void onActivityDestroyed(Activity activity) {}

  // when user decides to send report on his own, disable reporting via crash dialog, send report,
  // re-enable crash dialog.
  // using commit here to make sure we are not leaving temporary preference value
  @SuppressLint("ApplySharedPref")
  static void sendLogs() {
    ACRA.getACRASharedPreferences()
        .edit()
        .putBoolean(ACRA.PREF_ALWAYS_ACCEPT, Boolean.TRUE)
        .apply();
    try {
      ACRA.getErrorReporter().handleException(null, false);
    } finally {
      ACRA.getACRASharedPreferences()
          .edit()
          .putBoolean(ACRA.PREF_ALWAYS_ACCEPT, Boolean.FALSE)
          .commit();
    }
  }
}
