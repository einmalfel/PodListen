package com.einmalfel.podlisten;

import android.content.Context;
import android.util.Log;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(
    mailTo = "einmalfel@gmail.com",
    logcatArguments = {"-t", "1000", "-v", "time", "*:S",
                       "PLA:V", "DLR:V", "ELA:V", "SSA:V", "EVH:V", "IMG:V", "MAC:V", "NEF:V",
                       "PLC:V", "PPS:V", "PLF:V", "EPM:V", "PLP:V", "SSF:V", "SWK:V", "WGH:V",
                       "SDF:V", "STB:V", "MBR:V", "PAC:V", "STR:V", "PRF:V",
                       "AndroidRuntime:V", "ACRA:V"},
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
public class PodListenApp extends DebuggableApp {
  private static PodListenApp instance;

  public static PodListenApp getInstance() {
    return instance;
  }

  public static Context getContext() {
    if (instance == null) {
      Log.wtf("APP", "Getting context before Application.onCreate()", new NullPointerException());
    }
    return instance;
  }

  @Override
  public void onCreate() {
    instance = this;
    ACRA.init(this);
    super.onCreate();
  }

  static void sendLogs() {
    ACRA.getErrorReporter().handleException(null);
  }
}
