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
import com.einmalfel.podlisten.support.RecyclerItemClickListener;


public class PlaylistFragment extends DebuggableFragment implements LoaderManager
    .LoaderCallbacks<Cursor>, RecyclerItemClickListener.OnItemClickListener {
  private MainActivity activity;
  private static final String TAG = "PLF";
  private static final MainActivity.Pages activityPage = MainActivity.Pages.PLAYLIST;
  private final EpisodeListAdapter adapter = new EpisodeListAdapter(null);

  @Override
  public void onDestroy() {
    adapter.swapCursor(null);
    super.onDestroy();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
      savedInstanceState) {
    View layout = inflater.inflate(R.layout.fragment_playlist, container, false);
    RecyclerView rv = (RecyclerView) layout.findViewById(R.id.recycler_view);
    activity = (MainActivity) getActivity();
    rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(activity));
    rv.setItemAnimator(new DefaultItemAnimator());
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), null, this);
    rv.setAdapter(adapter);
    rv.addOnItemTouchListener(new RecyclerItemClickListener(activity, rv, this));
    return layout;
  }

  @Override
  public void onItemLongClick(View view, int position) {
    long episodeId = adapter.getItemId(position);
    Log.d(TAG, "long tap " + Long.toString(episodeId));
    PodcastHelper.deleteEpisodeDialog(episodeId, activity);
  }

  @Override
  public void onItemClick(View view, int position) {
    long id = adapter.getItemId(position);
    Log.d(TAG, "tap " + Long.toString(id));
    if (activity.connection.service != null) {
      activity.connection.service.playEpisode(id);
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(activity,
        Provider.episodeUri,
        new String[]{Provider.K_ID, Provider.K_ENAME, Provider.K_EDESCR, Provider.K_EDFIN},
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
}
