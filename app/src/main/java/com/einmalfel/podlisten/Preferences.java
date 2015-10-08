package com.einmalfel.podlisten;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

/** This class is intended to encapsulate preferences names and default values */
public class Preferences implements SharedPreferences.OnSharedPreferenceChangeListener {
  enum Key {
    MAX_DOWNLOADS,
  }

  enum MaxDownloadsOption {
    ONE, TWO, THREE, FOUR, FIVE, TEN, UNLIMITED;

    public int toInt() {
      switch (this) {
        case TEN:
          return 10;
        case UNLIMITED:
          return Integer.MAX_VALUE;
        default:
          return ordinal() + 1;
      }
    }

    @Override
    public String toString() {
      if (this == UNLIMITED) {
        return PodListenApp.getContext().getString(R.string.preferences_max_downloads_unlimited);
      } else {
        return Integer.toString(toInt());
      }
    }
  }

  private static final String TAG = "PRF";
  private static final MaxDownloadsOption DEFAULT_MAX_DOWNLOADS = MaxDownloadsOption.TWO;
  private static Preferences instance = null;

  // fields below could be changed from readPreference() only
  private MaxDownloadsOption maxDownloads;

  public static Preferences getInstance() {
    if (instance == null) {
      synchronized (Preferences.class) {
        if (instance == null) {
          instance = new Preferences();
        }
      }
    }
    return instance;
  }

  public Preferences() {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PodListenApp.getContext());
    sp.registerOnSharedPreferenceChangeListener(this);
    for (Key key : Key.values()) {
      readPreference(sp, key);
    }
  }

  private synchronized void readPreference(SharedPreferences sPrefs, Key key) {
    switch (key) {
      case MAX_DOWNLOADS:
        try {
          int maxDownloadsOrdinal = Integer.valueOf(sPrefs.getString(
              Key.MAX_DOWNLOADS.toString(), "-1"));
          if (maxDownloadsOrdinal == -1) {
            sPrefs.edit().putString(Key.MAX_DOWNLOADS.toString(),
                                    Integer.toString(DEFAULT_MAX_DOWNLOADS.ordinal())).commit();
          } else {
            maxDownloads = MaxDownloadsOption.values()[maxDownloadsOrdinal];
          }
        } catch (NumberFormatException exception) {
          Log.e(TAG, "Failed to parse max downloads preference, value remains " + maxDownloads);
        }
        break;
    }
  }

  @NonNull
  public MaxDownloadsOption getMaxDownloads() {
    return maxDownloads;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Log.i(TAG, "Preference changed " + key + ", values: " + sharedPreferences.getAll().toString());
    readPreference(sharedPreferences, Key.valueOf(key));
  }
}
