package com.einmalfel.podlisten;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.util.Log;

public class PreferencesActivity extends AppCompatActivity {
  private static final String TAG = "PAC";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getSupportFragmentManager()
        .beginTransaction()
        .replace(android.R.id.content, new PreferenceFragmentCompat() {
          @Override
          public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.preferences);
          }
        })
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
