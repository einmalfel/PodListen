package com.einmalfel.podlisten;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

public class WidgetProvider extends AppWidgetProvider {
  private static String TAG = "WGP";
  private final WidgetHelper helper = WidgetHelper.getInstance();

  @Override
  public void onReceive(@NonNull Context context, @NonNull Intent intent) {
    if (!helper.processIntent(intent)) {
      super.onReceive(context, intent);
    }
  }

  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    helper.updateWidgetsFull(appWidgetIds);
  }
}
