package com.einmalfel.podlisten;


import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;


public class PlaylistFragment extends Fragment
    implements LoaderManager.LoaderCallbacks<Cursor>, RecyclerItemClickListener.OnItemClickListener {
  private MainActivity activity;
  private static final String TAG = "PLF";
  private static final MainActivity.Pages activityPage = MainActivity.Pages.PLAYLIST;
  private EpisodeListAdapter adapter;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View layout = inflater.inflate(R.layout.fragment_playlist, container, false);
    RecyclerView rv = (RecyclerView) layout.findViewById(R.id.recycler_view);
    activity = (MainActivity) getActivity();
    rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(activity));
    rv.setItemAnimator(new DefaultItemAnimator());
    adapter = new EpisodeListAdapter(activity, null, MainActivity.Pages.PLAYLIST);
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), null, this);
    rv.setAdapter(adapter);
    rv.addOnItemTouchListener(new RecyclerItemClickListener(activity, rv, this));
    return layout;
  }

  public void deleteEpisode(long id) {
    new File(activity.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), Long.toString(id)).delete();
    ContentValues val = new ContentValues();
    val.put(Provider.K_ESTATE, Provider.ESTATE_GONE);
    activity.getContentResolver().update(Provider.getUri(Provider.T_EPISODE, id), val, null, null);
  }

  @Override
  public void onItemLongClick(View view, int position) {
    final long episodeId = adapter.getItemId(position);
    Log.d(TAG, "long tap " + Long.toString(episodeId));
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        deleteEpisode(episodeId);
      }
    });
    builder
        .setNegativeButton(R.string.cancel, null)
        .setTitle(activity.getString(R.string.delete_episode))
        .create()
        .show();
  }

  @Override
  public void onItemClick(View view, int position) {
    long id = adapter.getItemId(position);
    Log.d(TAG, "tap " + Long.toString(id));
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
    Log.d(TAG, "Finished loading cursor " + data.getCount());
    adapter.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader loader) {
    adapter.swapCursor(null);
  }
}
