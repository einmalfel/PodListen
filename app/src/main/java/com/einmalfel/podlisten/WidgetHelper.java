package com.einmalfel.podlisten;


import android.app.Application;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * This singleton is intended to transfer commands and status updates between app widgets
 * (including widget in notification bar) and player service.
 * Should already be instantiated by the moment of playback launch, so it is set up when:
 * - there is active app widget (WidgetProvider.onEnabled)
 * - PlayService is in foreground
 * PodListen widget contains episode image. I consider it a bad practice to
 * serialize-send-deserialize the image on each widget update, cause this happens at least at 2 Hz
 * (playback progress updates).
 * Widget in notification is able to accumulate updates sent in form of partial RemoteViews in
 * same way as home screen widgets do. But in case system-ui process restarts, there is nothing
 * like
 * WidgetProvider.onUpdate to be called to notify app that full widget update is needed. Instead
 * system-ui will rebuilt widget with last RemoteViews it has received (partial).
 * What WidgetHelper do, trying to minimize traffic to widget and notification managers:
 * 1 on WidgetProvider.onUpdate:
 * - send full update to given home screen widgets
 * 2 on progress update:
 * - send minimal update to all home screen widgets
 * - send full update except for image to notification
 * 3 on state update:
 * - send minimal update to all home screen widgets
 * - send full update to notification
 * In worst case, after system-ui restart user will get no image in notification widget, but widget
 * itself will be functional.
 */
public class WidgetHelper implements PlayerService.PlayerStateListener {
  enum WidgetAction {
    PLAY_PAUSE, SEEK_FORWARD, SEEK_BACKWARD, NEXT_EPISODE, STOP
  }

  private static final String TAG = "WGH";
  private static final int INTENT_ID_LAUNCH_ACTIVITY = 100;
  private static final int INTENT_ID_BASE = 101; //ids 101-105 will be used for notification buttons

  private static WidgetHelper instance;

  private final PlayerLocalConnection connection = new PlayerLocalConnection(this);
  private final Application context = PodListenApp.getContext();
  private final ComponentName receiverComponent = new ComponentName(context, WidgetProvider.class);
  private final AppWidgetManager awm = AppWidgetManager.getInstance(context);
  private final NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
  private final RemoteViews rvPartial = new RemoteViews(context.getPackageName(), R.layout.player);
  private final RemoteViews rvFull = new RemoteViews(context.getPackageName(), R.layout.player);
  private final Intent activityIntent = new Intent(context, MainActivity.class);

  private long episodeId;
  private String title;
  private long podcastId;
  private PlayerService.State state = PlayerService.State.UPDATE_ME;
  private int position;
  private int max;

  private WidgetHelper() {
    activityIntent.putExtra(MainActivity.PAGE_LAUNCH_OPTION, MainActivity.Pages.PLAYLIST.ordinal());
    rvFull.setOnClickPendingIntent(R.id.next_button, getIntent(context, WidgetAction.NEXT_EPISODE));
    rvFull.setOnClickPendingIntent(R.id.fb_button, getIntent(context, WidgetAction.SEEK_BACKWARD));
    rvFull.setOnClickPendingIntent(R.id.ff_button, getIntent(context, WidgetAction.SEEK_FORWARD));
    rvFull.setOnClickPendingIntent(R.id.play_button, getIntent(context, WidgetAction.PLAY_PAUSE));
    rvFull.setOnClickPendingIntent(R.id.play_options, getIntent(context, WidgetAction.STOP));
    rvFull.setImageViewResource(R.id.play_options, R.mipmap.ic_close_white_36dp);
    builder.setSmallIcon(R.drawable.logo).setPriority(NotificationCompat.PRIORITY_LOW)
           .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE);
    connection.bind();
  }

  static WidgetHelper getInstance() {
    if (instance == null) {
      synchronized (WidgetHelper.class) {
        if (instance == null) {
          instance = new WidgetHelper();
        }
      }
    }
    return instance;
  }

  @NonNull
  private static PendingIntent getIntent(@NonNull Context context,
                                         @NonNull WidgetAction widgetAction) {
    Intent intent = new Intent(context, WidgetProvider.class);
    intent.setAction(widgetAction.name());
    return PendingIntent.getBroadcast(context, INTENT_ID_BASE + widgetAction.ordinal(), intent, 0);
  }

  private void rvApplyState(RemoteViews rv) {
    setButtonEnabled(!state.isStopped(), rv, R.id.ff_button);
    setButtonEnabled(!state.isStopped(), rv, R.id.fb_button);
    setButtonEnabled(state != PlayerService.State.STOPPED, rv, R.id.play_options);
    if (state == PlayerService.State.PLAYING) {
      rv.setImageViewResource(R.id.play_button, R.mipmap.ic_pause_white_36dp);
    } else {
      rv.setImageViewResource(R.id.play_button, R.mipmap.ic_play_arrow_white_36dp);
    }

    if (episodeId == 0) {
      rv.setTextViewText(R.id.play_title,
                         context.getString(state == PlayerService.State.STOPPED_EMPTY
                                               ? R.string.player_empty : R.string.player_stopped));
      activityIntent.removeExtra(MainActivity.EPISODE_ID_OPTION);
    } else {
      activityIntent.putExtra(MainActivity.EPISODE_ID_OPTION, episodeId);
      rv.setTextViewText(R.id.play_title, title);
    }
    PendingIntent pendingIntent = PendingIntent.getActivity(
        context, INTENT_ID_LAUNCH_ACTIVITY, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    rv.setOnClickPendingIntent(R.id.player, pendingIntent);
  }

  private void rvApplyImage(RemoteViews rv) {
    Bitmap img = ImageManager.getInstance().getImage(episodeId);
    if (img == null) {
      img = ImageManager.getInstance().getImage(podcastId);
    }
    if (img == null) {
      rv.setImageViewResource(R.id.episode_image, R.drawable.logo);
    } else {
      rv.setImageViewBitmap(R.id.play_episode_image, img);
    }
  }

  private void rvApplyProgress(RemoteViews rv) {
    rv.setProgressBar(R.id.play_progress, max, position, false);
  }

  public boolean processIntent(Intent intent) {
    WidgetAction action;

    try {
      action = WidgetAction.valueOf(intent.getAction());
    } catch (IllegalArgumentException | NullPointerException ignored) {
      return false;
    }

    processAction(action);
    return false;
  }

  private void processAction(@NonNull WidgetAction action) {
    if (connection.service == null) {
      // skip action if not bound to player yet. This is quite unlikely
      Log.e(TAG, "Skipping " + action + ". Service is not ready yet");
      return;
    }
    switch (action) {
      case PLAY_PAUSE:
        connection.service.playPauseResume();
        break;
      case NEXT_EPISODE:
        connection.service.playNext();
        break;
      case SEEK_FORWARD:
        connection.service.jumpForward();
        break;
      case SEEK_BACKWARD:
        connection.service.jumpBackward();
        break;
      case STOP:
        connection.service.stop();
        break;
      default:
        throw new AssertionError("Action " + action);
    }
  }

  public void updateWidgetsFull(int[] appWidgetIds) {
    RemoteViews rv = rvFull.clone();
    rvApplyImage(rv);
    rvApplyProgress(rv);
    rvApplyState(rv);
    awm.updateAppWidget(appWidgetIds, rv);
  }

  private void updateWidgetsPartial(RemoteViews rv) {
    awm.partiallyUpdateAppWidget(awm.getAppWidgetIds(receiverComponent), rv);
  }

  private void updateNotification(RemoteViews rv) {
    if (connection.service != null
        && connection.service.getState() != PlayerService.State.STOPPED) {
      connection.service.updateNotification(builder.setContent(rv).build());
    }
  }

  @Override
  public void progressUpdate(int position, int max) {
    if (this.position != position || this.max != max) {
      this.position = position;
      this.max = max;
      RemoteViews rv = rvPartial.clone();
      rvApplyProgress(rv);
      updateWidgetsPartial(rv);
      rv = rvFull.clone();
      rvApplyState(rv);
      rvApplyProgress(rv);
      updateNotification(rv);
    }
  }

  private void setButtonEnabled(boolean enabled, @NonNull RemoteViews rv, @IdRes int id) {
    rv.setBoolean(id, "setEnabled", enabled);
    rv.setInt(id, "setColorFilter", enabled ? 0 : Color.GRAY);
  }

  @Override
  public void stateUpdate(PlayerService.State state, long episodeId) {
    if (this.episodeId != episodeId) {
      getEpisodeInfo(episodeId);
    }
    if (this.episodeId != episodeId || this.state != state) {
      this.state = state;
      RemoteViews rv = rvPartial.clone();
      if (this.episodeId != episodeId) {
        this.episodeId = episodeId;
        rvApplyImage(rv);
      }
      rvApplyState(rv);
      updateWidgetsPartial(rv);
      rv = rvFull.clone();
      rvApplyImage(rv);
      rvApplyProgress(rv);
      rvApplyState(rv);
      updateNotification(rv);
    }
  }

  private void getEpisodeInfo(long episodeId) {
    if (episodeId == 0) {
      title = "";
      podcastId = 0;
    } else {
      Cursor cursor = context.getContentResolver().query(
          Provider.getUri(Provider.T_EPISODE, episodeId),
          new String[]{Provider.K_ENAME, Provider.K_EPID},
          null, null, null);
      if (cursor != null) {
        if (cursor.moveToFirst()) {
          title = cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_ENAME));
          podcastId = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EPID));
        } else {
          title = context.getString(R.string.player_episode_does_not_exist, episodeId);
          podcastId = 0;
        }
        cursor.close();
      } else {
        Log.wtf(TAG, "Unexpectedly got null cursor from content provider", new AssertionError());
      }
    }
  }
}
