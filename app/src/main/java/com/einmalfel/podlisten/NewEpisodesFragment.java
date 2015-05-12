package com.einmalfel.podlisten;


import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
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


public class NewEpisodesFragment extends Fragment
    implements LoaderManager.LoaderCallbacks<Cursor>, RecyclerItemClickListener.OnItemClickListener{
  private MainActivity activity;
  private static final String TAG = "NEF";
  private static final MainActivity.Pages activityPage = MainActivity.Pages.NEW_EPISODES;
  private EpisodeListAdapter adapter;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View layout = inflater.inflate(R.layout.fragment_new_episodes, container, false);
    RecyclerView rv = (RecyclerView) layout.findViewById(R.id.recycler_view);
    activity = (MainActivity) getActivity();
    rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(activity));
    rv.setItemAnimator(new DefaultItemAnimator());
    adapter = new EpisodeListAdapter(activity, null, MainActivity.Pages.NEW_EPISODES);
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), null, this);
    rv.setAdapter(adapter);
    rv.addOnItemTouchListener(new RecyclerItemClickListener(activity, rv, this));

    Button b = (Button) layout.findViewById(R.id.refresh_button);
    b.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        Account acc = ((MainActivity) getActivity()).getAccount();
        ContentResolver.requestSync(acc, acc.type, settingsBundle);
      }
    });

    return layout;
  }

  @Override
  public void onItemLongClick(View view, int position) {
    long id = adapter.getItemId(position);
    Log.d(TAG, "long tap " + Long.toString(id));
  }

  @Override
  public void onItemClick(View view, int position) {
    long id = adapter.getItemId(position);
    ContentValues val = new ContentValues();
    val.put(Provider.K_ESTATE, Provider.ESTATE_IN_PLAYLIST);
    activity.getContentResolver().update(Provider.getUri(Provider.T_EPISODE, id), val, null, null);
    Log.d(TAG, "tap " + Long.toString(id));
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(activity,
        Provider.episodeUri,
        new String[]{Provider.K_ID, Provider.K_ENAME, Provider.K_EDESCR, Provider.K_EDFIN},
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