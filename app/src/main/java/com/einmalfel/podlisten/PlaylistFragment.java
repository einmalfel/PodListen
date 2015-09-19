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
  public boolean onLongTap(long id) {
    PodcastHelper.deleteEpisodeDialog(id, activity);
    return true;
  }

  @Override
  public void onButtonTap(long id) {
    if (activity.widgetHelper == null) {
      // before playback launches, widget helper should be up and bound
      activity.widgetHelper = WidgetHelper.getInstance();
    }
    if (conn.service != null) {
      if (id == conn.service.getEpisodeId()) {
        conn.service.playPauseResume();
      } else {
        conn.service.playEpisode(id);
      }
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(activity,
        Provider.episodeJoinPodcastUri,
        new String[]{Provider.K_EID, Provider.K_ENAME, Provider.K_EDESCR, Provider.K_EDFIN,
            Provider.K_ESIZE, Provider.K_ESTATE, Provider.K_PNAME, Provider.K_EPLAYED,
            Provider.K_ELENGTH, Provider.K_EDATE, Provider.K_EPID},
        Provider.K_ESTATE + " = ?",
        new String[]{Integer.toString(Provider.ESTATE_IN_PLAYLIST)},
        Provider.K_EDATE);
  }

  @Override
  public void onLoadFinished(Loader loader, Cursor data) {
    adapter.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader loader) {
    adapter.swapCursor(null);
  }

  @Override
  public void progressUpdate(int position, int max) {}

  @Override
  public void stateUpdate(PlayerService.State state) {
    adapter.setCurrentIdState(conn.service.getEpisodeId(), state);
  }

  @Override
  public void episodeUpdate(long id) {
    adapter.setCurrentIdState(id, conn.service.getState());
  }

  void showEpisode(long id) {
    for (int pos = 0; pos < adapter.getItemCount(); pos++) {
      if (adapter.getItemId(pos) == id) {
        Log.d(TAG, "scrolling to " + pos + " id " + id);
        rv.smoothScrollToPosition(pos);
        adapter.setExpanded(id, true, pos);
        return;
      }
    }
  }
}
