package com.einmalfel.podlisten.support;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;

public class SnackbarController {
  private static final String TAG = "SCT";

  private final View parent;
  private final int color;
  private Snackbar snackbar;
  private Snackbar.Callback snackbarCallback;

  public SnackbarController(View parent, int color) {
    this.parent = parent;
    this.color = color;
  }

  public void showSnackbar(@NonNull String text, int duration, @Nullable String action,
                           @Nullable final Snackbar.Callback callback) {
    if (snackbar == null || !snackbar.isShownOrQueued()) {
      // On some devices (e.g. Galaxy TAB 4 7.0) single snackbar instance cannot be showed
      // multiple times, so reinstantiate it
      if (snackbar != null) {
        snackbar.dismiss();
      }
      snackbar = Snackbar.make(parent, text, duration);
      snackbar.getView().setBackgroundColor(color);
    } else {
      // In genymotion (and therefore probably on some devices) snackbar queue glitches, so update
      // current snackbar instead of enqueuing it
      if (snackbarCallback != null) {
        Log.d(TAG, "Replacing snackbar with " + text + ". Emulating dismiss callback");
        snackbarCallback.onDismissed(snackbar, Snackbar.Callback.DISMISS_EVENT_TIMEOUT);
      }
      snackbar.setText(text);
      snackbar.setDuration(duration);
    }
    snackbar.setAction(action, callback == null ? null : new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        callback.onDismissed(snackbar, Snackbar.Callback.DISMISS_EVENT_ACTION);
      }
    });
    snackbar.setCallback(callback);
    snackbarCallback = callback;
    snackbar.show();
  }
}
