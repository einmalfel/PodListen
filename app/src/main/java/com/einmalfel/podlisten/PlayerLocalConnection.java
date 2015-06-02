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

  public PlayerLocalConnection(PlayerService.PlayerStateListener listener) {
    this.listener = listener;
  }

  public void bind() {
    Intent intent = new Intent(context, PlayerService.class);
    context.startService(intent); //need this to make player service remain even if clients unbind
    context.bindService(intent, this, Context.BIND_AUTO_CREATE);
  }

  public void unbind() {
    context.unbindService(this);
  }

  @Override
  public void onServiceConnected(ComponentName name, IBinder service) {
    this.service = ((PlayerService.LocalBinder) service).getService();
    this.service.addListener(listener);
  }

  @Override
  public void onServiceDisconnected(ComponentName name) {
    service.rmListener(listener);
    service = null;
  }
}
