package com.einmalfel.podlisten;

import android.app.Notification;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PlayerService extends DebuggableService implements MediaPlayer.OnSeekCompleteListener,
    MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener,
    AudioManager.OnAudioFocusChangeListener, Loader.OnLoadCompleteListener<Cursor> {


  enum State {
    STOPPED, STOPPED_ERROR, STOPPED_EMPTY, PLAYING, PAUSED, UPDATE_ME;

    public boolean isStopped() {
      return this == STOPPED || this == STOPPED_EMPTY || this == STOPPED_ERROR;
    }
  }

  private enum CallbackType {PROGRESS, STATE}

  interface PlayerStateListener {
    void progressUpdate(int position, int max);

    void stateUpdate(State state, long episodeId);
  }

  private class CallbackThread extends Thread {
    private final List<PlayerStateListener> listeners = new ArrayList<>(2);
    private final PlayerService service;
    private final BlockingQueue<CallbackType> queue = new LinkedBlockingQueue<>();
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
            case STATE:
              if (lastState != service.getState() || lastEpisode != currentId) {
                Log.d(TAG, "New playback state " + service.getState() + " id " + currentId);
                for (PlayerStateListener listener : listeners) {
                  listener.stateUpdate(service.state, currentId);
                }
                lastState = service.state;
                lastEpisode = currentId;
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

  private static final int NOTIFICATION_ID = 2;
  private static final String TAG = "PPS";
  private static final float NO_FOCUS_VOLUME = 0.2f;
  private static final int LOADER_ID = 10;

  private final CallbackThread callbackThread = new CallbackThread(this);
  private MediaPlayer player;
  private long currentId;
  private int startSeek;
  private int progress;
  private int length;
  private boolean preparing = false;
  private State state = State.STOPPED;
  private int focusMode;
  private float audioVolume = 1f;
  private CursorLoader playableEpisodesLoader;
  private Cursor playableEpisodes;

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
    playableEpisodesLoader = new CursorLoader(
        this,
        Provider.episodeUri, new String[]{Provider.K_ID},
        Provider.K_ESTATE + " == " + Integer.toString(Provider.ESTATE_IN_PLAYLIST) + " AND " +
            Provider.K_EDFIN + " == 100",
        null,
        Preferences.getInstance().getSortingMode().toSql());
    playableEpisodesLoader.registerListener(LOADER_ID, this);
    playableEpisodesLoader.startLoading();
    callbackThread.start();
    focusMode = AudioManager.AUDIOFOCUS_LOSS;
    if (Preferences.getInstance().getPlayerForeground()) {
      // Service process was crashed/killed while running foreground. Service is restarting now.
      // System has recovered our notification to last value passed to startForeground, but it
      // contains partial remote views and thus doesn't work, so instantiate WidgetHelper to fix it.
      state = State.STOPPED_ERROR;
      WidgetHelper.getInstance();
    }
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "Destroying service");
    stop();
    playableEpisodesLoader.unregisterListener(this);
    playableEpisodesLoader.cancelLoad();
    playableEpisodesLoader.stopLoading();
    callbackThread.interrupt();
    try {
      callbackThread.join();
    } catch (InterruptedException e) {
      Log.e(TAG, "unexpected interrupt ", e);
      Thread.currentThread().interrupt();
    }
    super.onDestroy();
  }

  @Override
  public synchronized void onLoadComplete(Loader<Cursor> loader, Cursor data) {
    if (data == null) {
      Log.wtf(TAG, "Unexpectedly got null from cursor loader", new AssertionError());
      return;
    }
    playableEpisodes = data;
    if (currentId == 0 && data.moveToFirst()) {
      currentId = data.getLong(data.getColumnIndexOrThrow(Provider.K_ID));
    }
    if (state == State.STOPPED_EMPTY && data.getCount() != 0) {
      state = State.STOPPED;
    } else if (state.isStopped() && data.getCount() == 0) {
      state = State.STOPPED_EMPTY;
    }
    callbackThread.post(CallbackType.STATE);
  }

  @Override
  public void onCompletion(MediaPlayer mp) {
    if (Preferences.getInstance().getCompleteAction() == Preferences.CompleteAction.DO_NOTHING) {
      pause();
    } else {
      playNext();
    }
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

  @Override
  public synchronized void onAudioFocusChange(int focusChange) {
    switch (focusChange) {
      case AudioManager.AUDIOFOCUS_GAIN:
        if (focusMode == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
          setVolume(1f);
        } else if (focusMode == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && state == State.PLAYING) {
          resume();
        }
        break;
      case AudioManager.AUDIOFOCUS_LOSS:
        stop();
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
        pause();
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
        setVolume(NO_FOCUS_VOLUME);
        break;
      default:
        Log.w(TAG, "Unhandled audio focus change: " + focusChange);
    }
    focusMode = focusChange;
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
    if (!state.isStopped() && !preparing) {
      progress = player.getCurrentPosition();
    }
    return progress;
  }

  public synchronized boolean jumpForward() {
    return seek(getProgress() + Preferences.getInstance().getJumpInterval().inMilliseconds());
  }

  public synchronized boolean jumpBackward() {
    return seek(getProgress() - Preferences.getInstance().getJumpInterval().inMilliseconds());
  }

  public synchronized void setVolume(float volume) {
    audioVolume = volume;
    if (player != null) {
      player.setVolume(volume, volume);
    }
  }

  public synchronized float getVolume() {
    return audioVolume;
  }

  public synchronized boolean seek(int timeMs) {
    if ((state == State.PLAYING && !preparing) || state == State.PAUSED) {
      if (timeMs < 0) {
        timeMs = 0;
        Log.d(TAG, "Attempting to seek with negative position. Seeking to zero");
      }
      if (length != 0 && timeMs > length) {
        if (Preferences.getInstance().getCompleteAction() == Preferences.CompleteAction.DO_NOTHING){
          seek(length);
          return true;
        } else {
          Log.d(TAG, "Attempting to seek past file end, playing next episode");
          return playNext();
        }
      } else {
        Log.d(TAG, "Seeking to " + timeMs);
        player.seekTo(timeMs);
        return true;
      }
    } else {
      Log.e(TAG, "Seek wrong state " + state + preparing);
      return false;
    }
  }

  private void releasePlayer() {
    if (player != null) {
      player.release();
      player = null;
    }
  }

  /**
   * Stop playback, release resources, callback clients, hide notification
   *
   * @return false if playback wasn't initialized before call
   */
  public synchronized boolean stop() {
    Log.d(TAG, "Stopping playback");
    MediaButtonReceiver.setService(null);
    releasePlayer();
    state = State.STOPPED;
    callbackThread.post(CallbackType.STATE);
    stopForeground(true);
    Preferences.getInstance().setPlayerForeground(false);
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
      Log.e(TAG, "pause wrong state " + state + " " + preparing);
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
      Log.e(TAG, "resume wrong state " + state + " " + preparing);
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
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    int res = am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      focusMode = AudioManager.AUDIOFOCUS_GAIN;
    } else {
      return false;
    }

    currentId = id;
    progress = 0;
    state = State.STOPPED_ERROR;

    initPlayer();

    synchronized (Preferences.getInstance()) {
      Storage storage = Preferences.getInstance().getStorage();
      File source = storage == null ? null : new File(storage.getPodcastDir(), Long.toString(id));
      if (source != null && source.exists() && storage.isAvailableRead()) {
        Log.d(TAG, "Launching playback of " + source.getAbsolutePath());
        try {
          player.setDataSource(this, Uri.fromFile(source));
          state = State.PLAYING;
          WidgetHelper.getInstance(); // ensure widget helper is up to handle player notification
        } catch (IOException e) {
          Log.e(TAG, "set source produced an exception, playback stopped: ", e);
        }
      } else {
        Log.e(TAG, "Failed to start playback, media is absent for episode " + id);
      }
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

    MediaButtonReceiver.setService(this);

    return state == State.PLAYING;
  }

  /** @return next ep. id according to complete action preference or 0 if there are no more eps */
  private static long getNext(@NonNull Cursor playableEpisodes, long currentId, boolean first) {
    long result = 0;

    if (playableEpisodes.getCount() > 0) {
      int idColumn = playableEpisodes.getColumnIndexOrThrow(Provider.K_ID);
      if (currentId != 0 && !first) {
        long prevId = 0;
        playableEpisodes.moveToFirst();
        do {
          if (prevId == currentId) {
            result = playableEpisodes.getLong(idColumn);
            break;
          }
          prevId = playableEpisodes.getLong(idColumn);
        } while (playableEpisodes.moveToNext());
      }

      if (result == 0) {
        playableEpisodes.moveToFirst();
        do {
          long id = playableEpisodes.getLong(idColumn);
          if (id != currentId) {
            result = id;
            break;
          }
        } while (playableEpisodes.moveToNext());
      }
    }

    return result;
  }

  /**
   * @return false if no more playable episodes available or playback launch caused an error,
   * otherwise true
   */
  public synchronized boolean playNext() {
    Preferences.CompleteAction completeAction = Preferences.getInstance().getCompleteAction();

    if (playableEpisodes == null) {
      Log.e(TAG, "Skip playNext, episodes cursor isn't loaded yet");
      return false;
    }

    // run getNext before deletion, cause we need to loop over playlist to find episode following
    // the current one
    long nextId = getNext(
        playableEpisodes, currentId, completeAction == Preferences.CompleteAction.PLAY_FIRST ||
            completeAction == Preferences.CompleteAction.DELETE_PLAY_FIRST);

    if (completeAction == Preferences.CompleteAction.DELETE_PLAY_FIRST ||
        completeAction == Preferences.CompleteAction.DELETE_PLAY_NEXT) {
      PodcastHelper.getInstance().markEpisodeGone(currentId);
    }

    if (nextId == 0) {
      Log.i(TAG, "No more playable episodes");
      releasePlayer();
      state = State.STOPPED_EMPTY;
      currentId = 0;
      progress = 0;
      callbackThread.post(CallbackType.STATE);
      callbackThread.post(CallbackType.PROGRESS);
      return false;
    } else {
      return playEpisode(nextId);
    }
  }

  public synchronized void playPauseResume() {
    switch (state) {
      case STOPPED:
      case STOPPED_ERROR:
        if (currentId != 0) {
          playEpisode(currentId);
        } else {
          playNext();
        }
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
      player.setVolume(audioVolume, audioVolume);
    } else {
      player.reset();
    }
  }

  public void updateNotification(@NonNull Notification notification) {
    startForeground(NOTIFICATION_ID, notification);
    if (!Preferences.getInstance().getPlayerForeground()) {
      Preferences.getInstance().setPlayerForeground(true);
    }
  }

}
