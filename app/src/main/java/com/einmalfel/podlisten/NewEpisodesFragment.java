package com.einmalfel.podlisten;


import android.content.ContentValues;
import android.content.Intent;
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
    activity.deleteEpisodeDialog(id);
    return true;
  }

  @Override
  public void onButtonTap(long id) {
    ContentValues val = new ContentValues(1);
    val.put(Provider.K_ESTATE, Provider.ESTATE_IN_PLAYLIST);
    activity.getContentResolver().update(Provider.getUri(Provider.T_EPISODE, id), val, null, null);
    if (Preferences.getInstance().getAutoDownloadMode() == Preferences.AutoDownloadMode.PLAYLIST) {
      activity.sendBroadcast(new Intent(DownloadReceiver.UPDATE_QUEUE_ACTION));
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(activity,
        Provider.episodeJoinPodcastUri,
        EpisodeListAdapter.REQUIRED_DB_COLUMNS,
        Provider.K_ESTATE + " = " + Provider.ESTATE_NEW,
        null,
        Provider.K_EDATE);
  }

  @Override
  public void onLoadFinished(Loader loader, Cursor data) {
    activity.updateFAB(data.getCount());
    adapter.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader loader) {
    adapter.swapCursor(null);
  }
}
