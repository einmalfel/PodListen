package com.einmalfel.podlisten;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;

public class PodlistenAccount {
  private static PodlistenAccount instance;

  private final Context context = PodListenApp.getContext();
  private final String appId = context.getResources().getString(R.string.app_id);
  // cannot derive from Account - instance will be send to sync framework in form of parcel
  private final Account account = new Account(appId, appId);

  public static PodlistenAccount getInstance() {
    if (instance == null) {
      synchronized (PodlistenAccount.class) {
        if (instance == null) {
          instance = new PodlistenAccount();
        }
      }
    }
    return instance;
  }

  private PodlistenAccount() {
    AccountManager aManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
    aManager.addAccountExplicitly(account, null, null);
  }

  void refresh() {
    Bundle settingsBundle = new Bundle();
    settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
    settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
    ContentResolver.requestSync(account, appId, settingsBundle);
  }

  void setupSync(int pollPeriod) {
    ContentResolver.addPeriodicSync(account, appId, Bundle.EMPTY, pollPeriod);
    ContentResolver.setSyncAutomatically(account, appId, true);
  }
}
