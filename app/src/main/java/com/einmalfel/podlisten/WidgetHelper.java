package com.einmalfel.podlisten;


import android.app.Notification;
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
 * - MainActivity launched
 */
public class WidgetHelper implements PlayerService.PlayerStateListener {
  enum WidgetAction {PLAY_PAUSE, SEEK_FORWARD, SEEK_BACKWARD, NEXT_EPISODE, STOP}

  private static final String TAG = "WGH";
  private static final int INTENT_ID_LAUNCH_ACTIVITY = 100;
  private static final int INTENT_ID_BASE = 101; //ids 100-104 will be used for notification buttons

  private static WidgetHelper instance;

  private final PlayerLocalConnection connection = new PlayerLocalConnection(this);
  private final Context context = PodListenApp.getContext();
  private final ComponentName receiverComponent = new ComponentName(context, WidgetProvider.class);
  private final AppWidgetManager awm = AppWidgetManager.getInstance(context);
  private final NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
  private final RemoteViews rvFull = new RemoteViews(context.getPackageName(), R.layout.player);
  private final Intent activityIntent = new Intent(context, MainActivity.class);

  private boolean notificationNeverUpdated = true;

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

  private WidgetHelper() {
    activityIntent.putExtra(MainActivity.PAGE_LAUNCH_OPTION, MainActivity.Pages.PLAYLIST.ordinal());
    rvFull.setOnClickPendingIntent(R.id.play_button, getIntent(context, WidgetAction.PLAY_PAUSE));
    rvFull.setOnClickPendingIntent(R.id.next_button, getIntent(context, WidgetAction.NEXT_EPISODE));
    rvFull.setOnClickPendingIntent(R.id.fb_button, getIntent(context, WidgetAction.SEEK_BACKWARD));
    rvFull.setOnClickPendingIntent(R.id.ff_button, getIntent(context, WidgetAction.SEEK_FORWARD));
    rvFull.setOnClickPendingIntent(R.id.play_options, getIntent(context, WidgetAction.STOP));
    rvFull.setImageViewResource(R.id.play_options, R.mipmap.ic_close_white_36dp);
    builder.setSmallIcon(R.drawable.main_icon).setPriority(NotificationCompat.PRIORITY_LOW)
           .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE);
    connection.bind();
  }

  public boolean processIntent(Intent intent) {
    WidgetAction action = null;

    try {
      action = WidgetAction.valueOf(intent.getAction());
    } catch (IllegalArgumentException|NullPointerException ignored) {}

    if (action == null) {
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
        notificationNeverUpdated = true;
        connection.service.stop();
        break;
    }
  }

  public void updateWidgetsFull(int[] appWidgetIds) {
    awm.updateAppWidget(appWidgetIds, rvFull);
  }

  private void updateWidgetsPartial(RemoteViews rv) {
    awm.partiallyUpdateAppWidget(awm.getAppWidgetIds(receiverComponent), rv);
  }

  private void updateNotification(RemoteViews rv) {
    if (connection.service != null &&
        connection.service.getState() != PlayerService.State.STOPPED) {
      if (notificationNeverUpdated) {
        connection.service.updateNotification(builder.setContent(rvFull).build());
        notificationNeverUpdated = false;
      }
      connection.service.updateNotification(builder.setContent(rv).build());
    }
  }

  @Override
  public void progressUpdate(int position, int max) {
    RemoteViews rvPartial = new RemoteViews(context.getPackageName(), R.layout.player);
    rvPartial.setProgressBar(R.id.play_progress, max, position, false);
    updateWidgetsPartial(rvPartial);
    updateNotification(rvPartial);
  }

  private void setButtonEnabled(boolean enabled, @NonNull RemoteViews rv, @IdRes int id) {
    rv.setBoolean(id, "setEnabled", enabled);
    rv.setInt(id, "setColorFilter", enabled ? 0 : Color.GRAY);
  }

  @Override
  public void stateUpdate(PlayerService.State state) {
    RemoteViews rvPartial = new RemoteViews(context.getPackageName(), R.layout.player);
    boolean seekable = state == PlayerService.State.PLAYING || state == PlayerService.State.PAUSED;
    setButtonEnabled(seekable, rvPartial, R.id.ff_button);
    setButtonEnabled(seekable, rvPartial, R.id.fb_button);
    setButtonEnabled(state != PlayerService.State.STOPPED, rvPartial, R.id.play_options);
    if (state == PlayerService.State.PLAYING) {
      rvPartial.setImageViewResource(R.id.play_button, R.mipmap.ic_pause_white_36dp);
    } else {
      rvPartial.setImageViewResource(R.id.play_button, R.mipmap.ic_play_arrow_white_36dp);
    }
    if (state == PlayerService.State.STOPPED_ERROR || state == PlayerService.State.STOPPED) {
      rvPartial.setTextViewText(R.id.play_title, context.getString(R.string.player_stopped));
    } else if (state == PlayerService.State.STOPPED_EMPTY) {
      rvPartial.setTextViewText(R.id.play_title, context.getString(R.string.player_empty));
    }
    updateWidgetsPartial(rvPartial);
    updateNotification(rvPartial);
  }

  @Override
  public void episodeUpdate(long id) {
    String title = null;
    Bitmap image = null;
    if (id != 0) {
      Cursor c = context.getContentResolver().query(
          Provider.getUri(Provider.T_EPISODE, id),
          new String[]{Provider.K_ENAME, Provider.K_EPID},
          null, null, null);
      if (c != null) {
        if (c.moveToFirst()) {
          image = ImageManager.getInstance().getImage(id);
          title = c.getString(c.getColumnIndex(Provider.K_ENAME));
          if (image == null) {
            image = ImageManager.getInstance()
                                .getImage(c.getLong(c.getColumnIndex(Provider.K_EPID)));
          }
        } else {
          title = "Episode " + id + " doesn't exist";
        }
        c.close();
      }
    }
    RemoteViews rvPartial = new RemoteViews(context.getPackageName(), R.layout.player);
    if (image == null) {
      rvPartial.setImageViewResource(R.id.play_episode_image, R.drawable.main_icon);
    } else {
      rvPartial.setImageViewBitmap(R.id.play_episode_image, image);
    }
    if (title != null) {
      rvPartial.setTextViewText(R.id.play_title, title);
    }
    if (id == 0) {
      activityIntent.removeExtra(MainActivity.EPISODE_ID_OPTION);
    } else {
      activityIntent.putExtra(MainActivity.EPISODE_ID_OPTION, id);
    }
    PendingIntent pendingIntent = PendingIntent.getActivity(
        context, INTENT_ID_LAUNCH_ACTIVITY, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    rvPartial.setOnClickPendingIntent(R.id.player, pendingIntent);
    updateWidgetsPartial(rvPartial);
    updateNotification(rvPartial);
  }
}
