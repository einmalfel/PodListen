package com.einmalfel.podlisten;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import java.util.List;

public class PreferencesFragment extends PreferenceFragmentCompat {
  @Override
  public void onCreatePreferences(Bundle bundle, String screenKey) {
    addPreferencesFromResource(R.xml.preferences);
  }

  private static <T extends Enum<T>> void bindEnumToList(@NonNull ListPreference preference,
                                                         @NonNull Class<T> enumType) {
    int length = enumType.getEnumConstants().length;
    String[] entries = new String[length];
    String[] entryValues = new String[length];
    for (T value : enumType.getEnumConstants()) {
      int ordinal = value.ordinal();
      entries[ordinal] = value.toString();
      entryValues[ordinal] = Integer.toString(ordinal);
    }
    preference.setEntries(entries);
    preference.setEntryValues(entryValues);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ListPreference storageListPref = (ListPreference) findPreference(
        Preferences.Key.STORAGE_PATH.toString());
    List<Storage> storageOptions = Storage.getWritableStorages();
    String[] optionStrings = new String[storageOptions.size()];
    for (int i = 0; i < storageOptions.size(); i++) {
      optionStrings[i] = storageOptions.get(i).toString();
    }
    storageListPref.setEntries(optionStrings);
    storageListPref.setEntryValues(optionStrings);

    ListPreference refreshIntervalListPref = (ListPreference) findPreference(
        Preferences.Key.REFRESH_INTERVAL.toString());
    bindEnumToList(refreshIntervalListPref, Preferences.RefreshIntervalOption.class);

    ListPreference maxDownloadsListPref = (ListPreference) findPreference(
        Preferences.Key.MAX_DOWNLOADS.toString());
    bindEnumToList(maxDownloadsListPref, Preferences.MaxDownloadsOption.class);

    ListPreference autoDownloadListPref = (ListPreference) findPreference(
        Preferences.Key.AUTO_DOWNLOAD.toString());
    bindEnumToList(autoDownloadListPref, Preferences.AutoDownloadMode.class);

    ListPreference downloadNetworkListPref = (ListPreference) findPreference(
        Preferences.Key.DOWNLOAD_NETWORK.toString());
    bindEnumToList(downloadNetworkListPref, Preferences.DownloadNetwork.class);

    ListPreference onCompleteListPref = (ListPreference) findPreference(
        Preferences.Key.COMPLETE_ACTION.toString());
    bindEnumToList(onCompleteListPref, Preferences.CompleteAction.class);

    ListPreference jumpIntervalListPref = (ListPreference) findPreference(
        Preferences.Key.JUMP_INTERVAL.toString());
    bindEnumToList(jumpIntervalListPref, Preferences.JumpInterval.class);

    // if there is no mail app installed, disable send bug-report option
    Intent testEmailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "", null));
    if (testEmailIntent.resolveActivity(getActivity().getPackageManager()) == null) {
      Preference sendBugReportPreference = findPreference("SEND_REPORT");
      sendBugReportPreference.setSummary(R.string.preferences_send_bug_report_summary_disabled);
      sendBugReportPreference.setEnabled(false);
    }

    Cursor cursor = getActivity().getContentResolver().query(
        Provider.podcastUri, null, null, null, null);
    if (cursor == null || cursor.getCount() == 0) {
      Preference opmlExportPreference = findPreference("OPML_EXPORT");
      opmlExportPreference.setSummary(R.string.preferences_opml_export_summary_disabled);
      opmlExportPreference.setEnabled(false);
    }
    if (cursor != null) {
      cursor.close();
    }
  }
}
