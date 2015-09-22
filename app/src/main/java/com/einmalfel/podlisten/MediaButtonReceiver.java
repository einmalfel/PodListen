package com.einmalfel.podlisten;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Headset media button handler, controlled by PlayerService.
 * It subscribes to media button broadcast when PlayerService is started.
 */
public class MediaButtonReceiver extends BroadcastReceiver {
  private static final String TAG = "MBR";
  //  private static final MediaButtonReceiver instance = new MediaButtonReceiver();
  private static PlayerService service = null;
  private static MediaSessionCompat session = null;

  static synchronized void setService(PlayerService playerService) {
    Log.e(TAG, "Set service " + playerService);
    if (session == null && playerService != null) {
      ComponentName eventReceiver = new ComponentName(playerService.getPackageName(),
                                                      MediaButtonReceiver.class.getName());
      session = new MediaSessionCompat(playerService, TAG, eventReceiver, null);
      session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);
      session.setCallback(new MediaSessionCompat.Callback() {
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
          return handleButton(mediaButtonEvent) || super.onMediaButtonEvent(mediaButtonEvent);
        }
      });
      session.setActive(true);
    } else if (session != null && playerService == null) {
      session.release();
      session = null;
    }
    service = playerService;
  }

  public MediaButtonReceiver() {
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (handleButton(intent)) {
      abortBroadcast();
    }
  }

  static private synchronized boolean handleButton(Intent intent) {
    KeyEvent ev = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
    if (service == null || ev.getAction() != KeyEvent.ACTION_DOWN || ev.getRepeatCount() != 0) {
      return false;
    }
    Log.d(TAG, "Processing media button: " + ev);
    switch (ev.getKeyCode()) {
      case KeyEvent.KEYCODE_MEDIA_PLAY: // sometimes this event means play/pause pressed during play
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
        service.playPauseResume();
        break;
      case KeyEvent.KEYCODE_MEDIA_PAUSE:
        service.pause();
        break;
      case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
      case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
      case KeyEvent.KEYCODE_MEDIA_STEP_FORWARD:
      case KeyEvent.KEYCODE_MEDIA_NEXT:
        service.jumpForward();
        break;
      case KeyEvent.KEYCODE_MEDIA_REWIND:
      case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
      case KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD:
      case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
        service.jumpBackward();
        break;
      case KeyEvent.KEYCODE_MEDIA_STOP:
        service.stop();
        break;
      default:
        return false;
    }
    return true;
  }

}
