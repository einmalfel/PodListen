package com.einmalfel.podlisten;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public class PlayerLocalConnection implements ServiceConnection {
  private static final String TAG = "PLC";

  public PlayerService service;

  private final PlayerService.PlayerStateListener listener;
  private final Context context = PodListenApp.getContext();
  private boolean clientExpectBind = false;

  public PlayerLocalConnection(PlayerService.PlayerStateListener listener) {
    this.listener = listener;
  }

  public synchronized void bind() {
    if (clientExpectBind || service != null) {
      return;
    }
    clientExpectBind = true;
    Intent intent = new Intent(context, PlayerService.class);
    context.startService(intent); //need this to make player service remain even if clients unbind
    context.bindService(intent, this, Context.BIND_AUTO_CREATE);
  }

  public synchronized void unbind() {
    if (clientExpectBind) {
      clientExpectBind = false;
    }
    if (service != null) {
      service.rmListener(listener);
      service = null;
      context.unbindService(this);
    }
  }

  // Callbacks below seem to be unpredictable. Both might not be called at all (e.g. in case of
  // frequent orientation changes) and might be called after owner calls unbind()

  @Override
  public void onServiceConnected(ComponentName name, IBinder service) {
    synchronized (this) {
      this.service = ((PlayerService.LocalBinder) service).getService();
      this.service.addListener(listener);
      if (!clientExpectBind) {
        unbind();
      }
    }
  }

  @Override
  public void onServiceDisconnected(ComponentName name) {}
}
