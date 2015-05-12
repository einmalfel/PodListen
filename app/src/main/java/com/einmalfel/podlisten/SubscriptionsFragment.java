package com.einmalfel.podlisten;


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


public class SubscriptionsFragment extends Fragment
    implements LoaderManager.LoaderCallbacks<Cursor>, RecyclerItemClickListener.OnItemClickListener {
  private MainActivity activity;
  private static final MainActivity.Pages activityPage = MainActivity.Pages.SUBSCRIPTIONS;
  private static final String TAG = "SSF";
  private PodcastListAdapter adapter;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View layout = inflater.inflate(R.layout.fragment_subscriptions, container, false);
    RecyclerView rv = (RecyclerView) layout.findViewById(R.id.recycler_view);
    activity = (MainActivity)getActivity();
    rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(activity));
    rv.setItemAnimator(new DefaultItemAnimator());
    adapter = new PodcastListAdapter(null);
    rv.setAdapter(adapter);
    rv.addOnItemTouchListener(new RecyclerItemClickListener(activity, rv, this));
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), null, this);
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
    Log.d(TAG, "tap " + Long.toString(id));
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(activity, Provider.podcastUri,
        new String[]{Provider.K_ID, Provider.K_PNAME, Provider.K_PDESCR}, null, null, null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    adapter.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    adapter.swapCursor(null);
  }
}
