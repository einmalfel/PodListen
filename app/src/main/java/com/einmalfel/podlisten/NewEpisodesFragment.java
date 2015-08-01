package com.einmalfel.podlisten;


import android.content.ContentValues;
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
import android.widget.Button;

import com.einmalfel.podlisten.support.PredictiveAnimatiedLayoutManager;


public class NewEpisodesFragment extends DebuggableFragment implements LoaderManager
    .LoaderCallbacks<Cursor>, EpisodeListAdapter.EpisodeClickListener {
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
    View layout = inflater.inflate(R.layout.fragment_new_episodes, container, false);
    RecyclerView rv = (RecyclerView) layout.findViewById(R.id.recycler_view);
    activity = (MainActivity) getActivity();
    rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(activity));
    rv.setItemAnimator(new DefaultItemAnimator());
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), null, this);
    rv.setAdapter(adapter);

    Button b = (Button) layout.findViewById(R.id.refresh_button);
    b.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        activity.refresh();
      }
    });
    b = (Button) layout.findViewById(R.id.clear_button);
    b.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Cursor c = activity.getContentResolver().query(
            Provider.episodeUri,
            new String[]{Provider.K_ID},
            Provider.K_ESTATE + " = ?",
            new String[]{Integer.toString(Provider.ESTATE_NEW)},
            null);
        while (c.moveToNext()) {
          PodcastHelper.getInstance().markEpisodeGone(
              c.getLong(c.getColumnIndex(Provider.K_ID)), true);
        }
        c.close();
      }
    });

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
    adapter.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader loader) {
    adapter.swapCursor(null);
  }
}
