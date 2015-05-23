package com.einmalfel.podlisten.support;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class StubAuthenticatorService extends Service {
  private StubAuthenticator mAuthenticator;

  @Override
  public void onCreate() {
    mAuthenticator = new StubAuthenticator(this);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mAuthenticator.getIBinder();
  }
}
