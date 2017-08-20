package com.einmalfel.podlisten;

import android.content.Context;
import android.support.annotation.NonNull;

public enum RefreshMode {
  WEEK(R.string.refresh_mode_week),
  MONTH(R.string.refresh_mode_month),
  YEAR(R.string.refresh_mode_year),
  ALL(R.string.refresh_mode_all),
  NONE(R.string.refresh_mode_none),
  LAST(R.string.refresh_mode_last),
  LAST_2(R.string.refresh_mode_last_2),
  LAST_3(R.string.refresh_mode_last_3),
  LAST_4(R.string.refresh_mode_last_4),
  LAST_5(R.string.refresh_mode_last_5),
  LAST_10(R.string.refresh_mode_last_10),
  LAST_20(R.string.refresh_mode_last_20),
  LAST_50(R.string.refresh_mode_last_50),
  LAST_100(R.string.refresh_mode_last_100);

  private static final Context context = PodListenApp.getContext();
  private final int stringId;


  RefreshMode(int stringId) {
    this.stringId = stringId;
  }

  @Override
  @NonNull
  public String toString() {
    return context.getResources().getString(stringId);
  }

  /**
   * @return new episodes quantity limit
   */
  public int getCount() {
    switch (this) {
      case LAST_10:
        return 10;
      case LAST_20:
        return 20;
      case LAST_50:
        return 50;
      case LAST_100:
        return 100;
      default:
        return ordinal() < NONE.ordinal() ? Integer.MAX_VALUE : ordinal() - NONE.ordinal();
    }
  }

  /**
   * @return maximum age of new episode in milliseconds
   */
  public long getMaxAge() {
    final long dayMilliseconds = 1000 * 60 * 60 * 24L;
    switch (this) {
      case WEEK:
        return dayMilliseconds * 7;
      case MONTH:
        return dayMilliseconds * 30;
      case YEAR:
        return dayMilliseconds * 365;
      default:
        return Long.MAX_VALUE;
    }
  }
}
