package com.einmalfel.podlisten;


import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
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
  enum WidgetAction {PLAY_PAUSE, SEEK_FORWARD, SEEK_BACKWARD, NEXT_EPISODE}

  private static final String TAG = "WGH";
  private static final Class rxClass = WidgetProvider.class;

  private static WidgetHelper instance;

  private final PlayerLocalConnection connection = new PlayerLocalConnection(this);
  private final Context context = PodListenApp.getContext();
  private final ComponentName receiverComponent = new ComponentName(context, rxClass);
  private final AppWidgetManager awm = AppWidgetManager.getInstance(context);
  private final NotificationManagerCompat nm = NotificationManagerCompat.from(context);
  private final NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
  private final RemoteViews rvFull = new RemoteViews(context.getPackageName(), R.layout.player);

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

  private WidgetHelper() {
    PendingIntent[] intents = new PendingIntent[WidgetAction.values().length];
    for (WidgetAction action : WidgetAction.values()) {
      Intent intent = new Intent(context, rxClass);
      intent.setAction(action.name());
      int WidgetAction_id = action.ordinal();
      intents[WidgetAction_id] = PendingIntent.getBroadcast(context, WidgetAction_id, intent, 0);
    }
    Intent intent = new Intent(context, MainActivity.class);
    intent.putExtra(MainActivity.PAGE_LAUNCH_OPTION, MainActivity.Pages.PLAYLIST.ordinal());
    PendingIntent pendingIntent = PendingIntent.getActivity(
        context, PlayerService.NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    rvFull.setOnClickPendingIntent(R.id.player, pendingIntent);
    rvFull.setOnClickPendingIntent(R.id.play_button, intents[WidgetAction.PLAY_PAUSE.ordinal()]);
    rvFull.setOnClickPendingIntent(R.id.next_button, intents[WidgetAction.NEXT_EPISODE.ordinal()]);
    rvFull.setOnClickPendingIntent(R.id.fb_button, intents[WidgetAction.SEEK_BACKWARD.ordinal()]);
    rvFull.setOnClickPendingIntent(R.id.ff_button, intents[WidgetAction.SEEK_FORWARD.ordinal()]);
    builder.setSmallIcon(R.drawable.main_icon).setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).setContent(rvFull);
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
    }
  }

  public void updateWidgetsFull(int[] appWidgetIds) {
    awm.updateAppWidget(appWidgetIds, rvFull);
  }

  private void updateWidgetsPartial(RemoteViews rv) {
    awm.partiallyUpdateAppWidget(awm.getAppWidgetIds(receiverComponent), rv);
  }

  private void updateNotification(RemoteViews rv) {
    if (connection.service == null || connection.service.getState() == PlayerService.State
        .STOPPED) {
      return;
    }
    nm.notify(PlayerService.NOTIFICATION_ID, builder.setContent(rv).build());
  }

  public void progressUpdateRV(int position, int max, RemoteViews rv) {
    rv.setProgressBar(R.id.play_progress, max, position, false);
  }

  @Override
  public void progressUpdate(int position, int max) {
    RemoteViews rvPartial = new RemoteViews(context.getPackageName(), R.layout.player);
    progressUpdateRV(position, max, rvPartial);
    progressUpdateRV(position, max, rvFull);
    updateWidgetsPartial(rvPartial);
    updateNotification(rvFull);
  }

  private void stateUpdateRV(PlayerService.State state, RemoteViews rv) {
    boolean seekable = state == PlayerService.State.PLAYING || state == PlayerService.State.PAUSED;
    rv.setBoolean(R.id.ff_button, "setEnabled", seekable);
    rv.setBoolean(R.id.fb_button, "setEnabled", seekable);
    rv.setInt(R.id.ff_button, "setColorFilter", seekable ? 0 : Color.GRAY);
    rv.setInt(R.id.fb_button, "setColorFilter", seekable ? 0 : Color.GRAY);
    if (state == PlayerService.State.PLAYING) {
      rv.setImageViewResource(R.id.play_button, R.mipmap.ic_pause_white_36dp);
    } else {
      rv.setImageViewResource(R.id.play_button, R.mipmap.ic_play_arrow_white_36dp);
    }
  }

  @Override
  public void stateUpdate(PlayerService.State state) {
    RemoteViews rvPartial = new RemoteViews(context.getPackageName(), R.layout.player);
    stateUpdateRV(state, rvPartial);
    stateUpdateRV(state, rvFull);
    updateWidgetsPartial(rvPartial);
    updateNotification(rvFull);
  }

  private void episodeUpdateRV(@Nullable Bitmap image, @NonNull String title,
                               @NonNull RemoteViews rv) {
    if (image == null) {
      rv.setImageViewResource(R.id.play_episode_image, R.drawable.main_icon);
    } else {
      rv.setImageViewBitmap(R.id.play_episode_image, image);
    }
    rv.setTextViewText(R.id.play_title, title);
  }

  @Override
  public void episodeUpdate(long id) {
    Cursor c = context.getContentResolver().query(
        Provider.getUri(Provider.T_EPISODE, id),
        new String[]{Provider.K_ENAME, Provider.K_EPID},
        null, null, null);
    String title;
    Bitmap image = ImageManager.getInstance().getImage(id);
    if (c.moveToFirst()) {
      title = c.getString(c.getColumnIndex(Provider.K_ENAME));
      if (image == null) {
        image = ImageManager.getInstance().getImage(c.getLong(c.getColumnIndex(Provider.K_EPID)));
      }
    } else {
      title = "Episode " + id + " doesn't exist";
    }
    c.close();
    RemoteViews rvPartial = new RemoteViews(context.getPackageName(), R.layout.player);
    episodeUpdateRV(image, title, rvFull);
    episodeUpdateRV(image, title, rvPartial);
    updateWidgetsPartial(rvPartial);
    updateNotification(rvFull);
  }
}
