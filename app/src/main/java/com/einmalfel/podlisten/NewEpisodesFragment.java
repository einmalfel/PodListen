package com.einmalfel.podlisten;


import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.einmalfel.podlisten.support.PredictiveAnimatiedLayoutManager;


public class NewEpisodesFragment extends DebuggableFragment implements LoaderManager
    .LoaderCallbacks<Cursor>, EpisodeListAdapter.ItemClickListener {
  private MainActivity activity;
  private static final String TAG = "NEF";
  private static final MainActivity.Pages activityPage = MainActivity.Pages.NEW_EPISODES;
  private final EpisodeListAdapter adapter = new EpisodeListAdapter(null, this);

  @Override
  public void onDestroy() {
    adapter.swapCursor(null);
    super.onDestroy();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
      savedInstanceState) {
    View layout = inflater.inflate(R.layout.common_list, container, false);
    RecyclerView rv = (RecyclerView) layout.findViewById(R.id.recycler_view);
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
    ContentValues val = new ContentValues(1);
    val.put(Provider.K_ESTATE, Provider.ESTATE_IN_PLAYLIST);
    activity.getContentResolver().update(Provider.getUri(Provider.T_EPISODE, id), val, null, null);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(activity,
        Provider.episodeJoinPodcastUri,
        new String[]{Provider.K_EID, Provider.K_ENAME, Provider.K_EDESCR, Provider.K_EDFIN,
            Provider.K_ESIZE, Provider.K_ESTATE, Provider.K_PNAME, Provider.K_EPLAYED,
            Provider.K_ELENGTH, Provider.K_EDATE, Provider.K_EPID},
        Provider.K_ESTATE + " = ?",
        new String[]{Integer.toString(Provider.ESTATE_NEW)},
        Provider.K_EDATE);
  }

  @Override
  public void onLoadFinished(Loader loader, Cursor data) {
    activity.newEpisodesNumber = data.getCount();
    activity.updateFAB();
    adapter.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader loader) {
    adapter.swapCursor(null);
  }
}
