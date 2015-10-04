package com.einmalfel.podlisten;

import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

/** This class is intended to encapsulate preferences names and default values */
public class Preferences implements SharedPreferences.OnSharedPreferenceChangeListener {
  enum Key {
  }

  private static final String TAG = "PRF";
  private static Preferences instance = null;


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
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Log.i(TAG, "Preference changed " + key + ", values: " + sharedPreferences.getAll().toString());
    readPreference(sharedPreferences, Key.valueOf(key));
  }
}
