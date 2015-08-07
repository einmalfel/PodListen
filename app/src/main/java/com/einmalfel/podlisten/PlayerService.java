package com.einmalfel.podlisten;

import android.app.Notification;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PlayerService extends DebuggableService implements MediaPlayer.OnSeekCompleteListener,
    MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener {

  enum State {STOPPED, STOPPED_ERROR, PLAYING, PAUSED, UPDATE_ME}

  private enum CallbackType {PROGRESS, STATE, EPISODE}

  interface PlayerStateListener {
    void progressUpdate(int position, int max);

    void stateUpdate(State state);

    void episodeUpdate(long id);
  }

  private class CallbackThread extends Thread {
    private final List<PlayerStateListener> listeners = new ArrayList<PlayerStateListener>(2);
    private final PlayerService service;
    private final BlockingQueue<CallbackType> queue = new LinkedBlockingQueue<CallbackType>();
    private int lastLength = -1;
    private int lastProgress = -1;
    private PlayerService.State lastState = PlayerService.State.UPDATE_ME;
    private long lastEpisode = -1;

    @Override
    public void run() {
      Log.d(TAG, "Starting callback thread");
      while (!isInterrupted()) {
        CallbackType ct;
        try {
          // report progress every 500ms if playing and queue is empty
          if (service.getState() == PlayerService.State.PLAYING) {
            ct = queue.poll(500, TimeUnit.MILLISECONDS);
          } else {
            ct = queue.take();
          }
          if (ct == null) {
            ct = CallbackType.PROGRESS;
          }
        } catch (InterruptedException ignored) {
          break;
        }
        // Other methods of callbackThread could be called from PlayerService only.
        // Syncing on service, not on this, to prevent deadlocking
        synchronized (service) {
          switch (ct) {
            case EPISODE:
              if (lastEpisode != service.currentId) {
                Log.d(TAG, "Playing new episode " + service.currentId);
                for (PlayerStateListener listener : listeners) {
                  listener.episodeUpdate(service.currentId);
                }
                lastEpisode = service.currentId;
              }
              break;
            case STATE:
              if (lastState != service.getState()) {
                Log.d(TAG, "New playback state " + service.getState());
                for (PlayerStateListener listener : listeners) {
                  listener.stateUpdate(service.state);
                }
                lastState = service.state;
              }
              break;
            case PROGRESS:
              if (lastLength != service.length || lastProgress != service.getProgress()) {
                for (PlayerStateListener listener : listeners) {
                  listener.progressUpdate(service.progress, service.length);
                }
                ContentValues values = new ContentValues(2);
                values.put(Provider.K_EPLAYED, service.progress);
                values.put(Provider.K_ELENGTH, service.length);
                service.getContentResolver().update(
                    Provider.getUri(Provider.T_EPISODE, service.currentId), values, null, null);
                lastLength = service.length;
                lastProgress = service.progress;
              }
              break;
          }
        }
      }
      Log.d(TAG, "Finishing callback thread");
    }

    CallbackThread(PlayerService service) {
      this.service = service;
    }

    void addListener(PlayerStateListener listener) {
      synchronized (service) {
        listeners.add(listener);
        lastLength = -1;
        lastProgress = -1;
        lastState = PlayerService.State.UPDATE_ME;
        lastEpisode = -1;
        post(CallbackType.EPISODE);
        post(CallbackType.STATE);
        post(CallbackType.PROGRESS);
      }
    }

    void rmListener(PlayerStateListener listener) {
      synchronized (service) {
        listeners.remove(listener);
      }
    }

    void post(CallbackType callback) {
      synchronized (service) {
        queue.add(callback);
      }
    }
  }

  static final int NOTIFICATION_ID = 2;
  private static final String TAG = "PPS";
  private static final int JUMP_INTERVAL = 30000;

  private final CallbackThread callbackThread = new CallbackThread(this);
  private MediaPlayer player;
  private long currentId;
  private int startSeek;
  private int progress;
  private int length;
  private boolean preparing = false;
  private State state = State.STOPPED;

  class LocalBinder extends Binder {
    PlayerService getService() {
      return PlayerService.this;
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.d(TAG, "Binding");
    return new LocalBinder();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "Creating service");
    initPlayer();
    callbackThread.start();
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "Destroying service");
    stop();
    callbackThread.interrupt();
    try {
      callbackThread.join();
    } catch (InterruptedException e) {
      Log.e(TAG, "unexpected interrupt " + e.toString());
      Thread.currentThread().interrupt();
    }
    super.onDestroy();
  }

  @Override
  public void onCompletion(MediaPlayer mp) {
    playNext();
  }

  @Override
  public void onSeekComplete(MediaPlayer mp) {
    synchronized (this) {
      progress = mp.getCurrentPosition();
      callbackThread.post(CallbackType.PROGRESS);
      Log.d(TAG, "Seek done. Position " + progress);
    }
  }

  @Override
  public boolean onError(MediaPlayer mp, int what, int extra) {
    synchronized (this) {
      Log.e(TAG, "MP error, codes " + what + " " + extra);
      state = State.STOPPED_ERROR;
      preparing = false;
      callbackThread.post(CallbackType.STATE);
    }
    return true;
  }

  @Override
  public void onPrepared(MediaPlayer mp) {
    Log.e(TAG, "onprepared");
    synchronized (this) {
      preparing = false;
      length = mp.getDuration();
      Log.d(TAG, "Playback prepared (length " + length + "), starting..");
      if (startSeek > 0) {
        mp.seekTo(startSeek); // progress will be reported in seek callback
      } else {
        callbackThread.post(CallbackType.PROGRESS);
      }
      mp.start();
    }
  }

  public synchronized void addListener(PlayerStateListener listener) {
    callbackThread.addListener(listener);
  }

  public synchronized void rmListener(PlayerStateListener listener) {
    callbackThread.rmListener(listener);
  }

  public synchronized State getState() {
    return state;
  }

  public synchronized long getEpisodeId() {
    return currentId;
  }

  public synchronized int getProgress() {
    if (state != State.STOPPED && state != State.STOPPED_ERROR && !preparing) {
      progress = player.getCurrentPosition();
    }
    return progress;
  }

  public synchronized boolean jumpForward() {
    return seek(getProgress() + JUMP_INTERVAL);
  }

  public synchronized boolean jumpBackward() {
    return seek(getProgress() - JUMP_INTERVAL);
  }

  public synchronized boolean seek(int timeMs) {
    if ((state == State.PLAYING && !preparing) || state == State.PAUSED) {
      Log.d(TAG, "Seeking to " + timeMs);
      player.seekTo(timeMs);
      return true;
    } else {
      Log.e(TAG, "Seek wrong state " + state.toString() + Boolean.toString(preparing));
      return false;
    }
  }

  /**
   * Stop playback, release resources, callback clients, hide notification
   *
   * @return false if playback wasn't initialized before call
   */
  public synchronized boolean stop() {
    Log.d(TAG, "Stopping playback");
    if (player != null) {
      player.release();
      player = null;
    }
    state = State.STOPPED;
    currentId = 0;
    progress = 0;
    callbackThread.post(CallbackType.STATE);
    callbackThread.post(CallbackType.PROGRESS);
    callbackThread.post(CallbackType.EPISODE);
    stopForeground(true);
    return true;
  }

  /**
   * @return false if pause isn't possible: e.g. playback initialization in progress. True if
   * successfully paused or playback was already in pause state.
   */
  public synchronized boolean pause() {
    if (state == State.PLAYING && !preparing) {
      Log.d(TAG, "Pausing playback " + currentId);
      player.pause();
      state = State.PAUSED;
      callbackThread.post(CallbackType.STATE);
      return true;
    } else {
      Log.e(TAG, "pause wrong state " + state.toString() + " " + Boolean.toString(preparing));
      return false;
    }
  }

  /**
   * @return false if playback was not in paused stated before call
   */
  public synchronized boolean resume() {
    if (state == State.PAUSED) {
      Log.d(TAG, "Resuming playback " + currentId);
      player.start();
      state = State.PLAYING;
      callbackThread.post(CallbackType.STATE);
      return true;
    } else {
      Log.e(TAG, "resume wrong state " + state.toString() + " " + Boolean.toString(preparing));
      return false;
    }
  }

  /**
   * Starts playback of episode id regardless of previous state of the player.
   * If this is first playback launch after player service was stopped, it will be switched to
   * foreground mode with dummy notification (actual one is managed by WidgetHelper).
   *
   * @param id of episode
   * @return false if something wrong with media (not downloaded yet, sdcard ejected, wrong format)
   */
  public synchronized boolean playEpisode(long id) {
    currentId = id;
    progress = 0;
    File source = PodcastHelper.getInstance().getEpisodeFile(id);

    callbackThread.post(CallbackType.EPISODE);

    if (state == State.STOPPED) {
      startForeground(NOTIFICATION_ID, new Notification.Builder(this).build());
    }

    state = State.STOPPED_ERROR;

    initPlayer();
    if (source != null && source.exists()) {
      Log.d(TAG, "Launching playback of " + source.getAbsolutePath());
      try {
        player.setDataSource(this, Uri.fromFile(source));
        state = State.PLAYING;
      } catch (IOException e) {
        Log.e(TAG, "set source produced an exception, playback stopped: " + e.toString());
      }
    } else {
      Log.e(TAG, "Failed to start playback, media is absent for episode " + id);
    }
    if (state == State.PLAYING) {
      preparing = true;
      player.prepareAsync();
      // while playback is being prepared, check if episode was previously played to some position
      Cursor c = getContentResolver().query(
          Provider.getUri(Provider.T_EPISODE, id),
          new String[]{Provider.K_EPLAYED},
          null, null, null);
      startSeek = c.moveToFirst() ? c.getInt(c.getColumnIndexOrThrow(Provider.K_EPLAYED)) : 0;
      c.close();
    }
    callbackThread.post(CallbackType.STATE);
    return state == State.PLAYING;
  }

  /**
   * @return false if no more playable episodes available or playback launch caused an error,
   * otherwise true
   */
  public synchronized boolean playNext() {
    if (currentId > 0) {
      PodcastHelper.getInstance().markEpisodeGone(currentId);
    }
    boolean result;
    Cursor c = getContentResolver().query(Provider.episodeUri, new String[]{Provider.K_ID},
        Provider.K_ESTATE + " == ? AND " + Provider.K_EDFIN + " == 100", new String[]{Integer
            .toString(Provider.ESTATE_IN_PLAYLIST)}, Provider.K_EDATE);
    if (c.moveToFirst()) {
      result = playEpisode(c.getLong(c.getColumnIndex(Provider.K_ID)));
    } else {
      Log.i(TAG, "No more playable episodes");
      stop();
      result = false;
    }
    c.close();
    return result;
  }

  public synchronized void playPauseResume() {
    switch (state) {
      case STOPPED:
      case STOPPED_ERROR:
        playNext();
        break;
      case PLAYING:
        pause();
        break;
      case PAUSED:
        resume();
        break;
    }
  }

  private void initPlayer() {
    if (player == null) {
      player = new MediaPlayer();
      player.setOnPreparedListener(this);
      player.setOnCompletionListener(this);
      player.setOnErrorListener(this);
      player.setOnSeekCompleteListener(this);
    } else {
      player.reset();
    }
  }
}
