package com.einmalfel.podlisten;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.util.Log;

public class PreferencesActivity extends AppCompatActivity {
  private static final String TAG = "PAC";
  private final PreferenceFragmentCompat prefsFragment = new PreferenceFragmentCompat() {
    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
      addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      ListPreference maxDownloadsLP = (ListPreference) prefsFragment.findPreference(
          Preferences.Key.MAX_DOWNLOADS.toString());
      String[] maxDLEntries = new String[Preferences.MaxDownloadsOption.values().length];
      String[] maxDLEntryValues = new String[Preferences.MaxDownloadsOption.values().length];
      for (Preferences.MaxDownloadsOption option : Preferences.MaxDownloadsOption.values()) {
        maxDLEntries[option.ordinal()] = option.toString();
        maxDLEntryValues[option.ordinal()] = Integer.toString(option.ordinal());
      }
      maxDownloadsLP.setEntries(maxDLEntries);
      maxDownloadsLP.setEntryValues(maxDLEntryValues);
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefsFragment.setRetainInstance(true);
    getSupportFragmentManager()
        .beginTransaction()
        .add(android.R.id.content, prefsFragment)
        .commit();

    setTitle(getString(R.string.preferences_title));
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    } else {
      Log.wtf(TAG, "Should never get here: failed to get action bar of preference activity");
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    switch (intent.getAction()) {
      case "com.einmalfel.podlisten.SEND_BUG_REPORT":
        PodListenApp.sendLogs();
        break;
    }
  }
}
