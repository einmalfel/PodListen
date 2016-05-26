package com.einmalfel.podlisten.support;

import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.einmalfel.podlisten.PodListenApp;

public class UnitConverter {
  private static UnitConverter instance;
  private final DisplayMetrics displayMetrics;

  private UnitConverter() {
    displayMetrics = PodListenApp.getContext().getResources().getDisplayMetrics();
  }

  public static UnitConverter getInstance() {
    if (instance == null) {
      synchronized (UnitConverter.class) {
        if (instance == null) {
          instance = new UnitConverter();
        }
      }
    }
    return instance;
  }

  public int spToPx(int dp) {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, dp, displayMetrics));
  }

  public int dpToPx(int dp) {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics));
  }

  public int pxToDp(int px) {
    return Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
  }

}
