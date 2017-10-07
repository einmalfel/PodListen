package com.einmalfel.podlisten;


import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.einmalfel.podlisten.support.PredictiveAnimatiedLayoutManager;


public class PlaylistFragment extends DebuggableFragment implements
    LoaderManager.LoaderCallbacks<Cursor>, EpisodeListAdapter.ItemClickListener,
    PlayerService.PlayerStateListener {
  private MainActivity activity;
  private static final String TAG = "PLF";
  private static final MainActivity.Pages activityPage = MainActivity.Pages.PLAYLIST;
  private final EpisodeListAdapter adapter = new EpisodeListAdapter(null, this);
  private PlayerLocalConnection conn;
  private RecyclerView rv;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    conn = new PlayerLocalConnection(this);
  }

  @Override
  public void onDestroy() {
    adapter.swapCursor(null);
    super.onDestroy();
  }

  @Override
  public void onPause() {
    super.onPause();
    conn.unbind();
  }

  @Override
  public void onResume() {
    super.onResume();
    conn.bind();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
      savedInstanceState) {
    View layout = inflater.inflate(R.layout.common_list, container, false);
    rv = (RecyclerView) layout.findViewById(R.id.recycler_view);
    activity = (MainActivity) getActivity();
    rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(activity));
    rv.setItemAnimator(new DefaultItemAnimator());
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), null, this);
    rv.setAdapter(adapter);

    return layout;
  }

  @Override
  public boolean onLongTap(long id, String title, int state, String audioUrl, int downloaded) {
    activity.deleteEpisodeDialog(id, state, title);
    return true;
  }

  @Override
  public void onButtonTap(long id, String title, int state, String audioUrl, int downloaded) {
    // episode button in playlist is enabled in two cases:
    // - episode is downloaded, button is used for play/pause
    // - episode isn't downloaded, isn't being download (downloadId == 0), button stats download

    if (downloaded != Provider.EDFIN_COMPLETE) {
      PodListenApp.getContext().sendBroadcast(DownloadReceiver.getDownloadEpisodeIntent(
          PodListenApp.getContext(), audioUrl, title, id));
    } else {
      if (conn.service != null) {
        if (id == conn.service.getEpisodeId()) {
          conn.service.playPauseResume();
        } else {
          conn.service.playEpisode(id);
        }
      }
    }
  }

  public void reloadList() {
    activity.getSupportLoaderManager().restartLoader(
        MainActivity.Pages.PLAYLIST.ordinal(), null, this);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(activity,
                            Provider.episodeJoinPodcastUri,
                            EpisodeListAdapter.REQUIRED_DB_COLUMNS,
                            Provider.K_ESTATE + " = " + Provider.ESTATE_IN_PLAYLIST,
                            null,
                            Preferences.getInstance().getSortingMode().toSql());
  }

  @Override
  public void onLoadFinished(Loader loader, Cursor data) {
    adapter.swapCursor(data);
    if (activity.pendingScrollId != 0) {
      for (int pos = 0; pos < adapter.getItemCount(); pos++) {
        if (adapter.getItemId(pos) == activity.pendingScrollId) {
          Log.d(TAG, "scrolling to " + pos + " id " + activity.pendingScrollId);
          rv.smoothScrollToPosition(pos);
          adapter.setExpanded(activity.pendingScrollId, true, pos);
          activity.pendingScrollId = 0;
          return;
        }
      }
    }
  }

  @Override
  public void onLoaderReset(Loader loader) {
    adapter.swapCursor(null);
  }

  @Override
  public void progressUpdate(int position, int max) {}

  @Override
  public void stateUpdate(PlayerService.State state, long episodeId) {
    adapter.setCurrentIdState(episodeId, state);
  }
}
