package com.einmalfel.podlisten;

import android.content.Context;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(
    mailTo = "einmalfel@gmail.com",
    logcatArguments = {"-t", "1000", "-v", "time", "*:S",
                       "PLA:V", "DSR:V", "ELA:V", "SSA:V", "EVH:V", "IMG:V", "MAC:V", "NEF:V",
                       "PLC:V", "PPS:V", "PLF:V", "EPM:V", "PLP:V", "SSF:V", "SWK:V", "WGH:V"},
    customReportContent = {
        ReportField.USER_CRASH_DATE, ReportField.AVAILABLE_MEM_SIZE, ReportField.TOTAL_MEM_SIZE,
        ReportField.BUILD, ReportField.BUILD_CONFIG, ReportField.DISPLAY,
        ReportField.CRASH_CONFIGURATION, ReportField.SHARED_PREFERENCES, ReportField.LOGCAT,
        ReportField.STACK_TRACE}
)
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
    ACRA.init(this);
    super.onCreate();
  }

  static void sendLogs() {
    ACRA.getErrorReporter().handleException(null);
  }
}
