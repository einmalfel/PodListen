package com.einmalfel.podlisten;


import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.einmalfel.podlisten.support.PredictiveAnimatiedLayoutManager;


public class SubscriptionsFragment extends DebuggableFragment implements
    LoaderManager.LoaderCallbacks<Cursor>, EpisodeListAdapter.ItemClickListener {
  private MainActivity activity;
  private static final MainActivity.Pages activityPage = MainActivity.Pages.SUBSCRIPTIONS;
  private static final String TAG = "SSF";
  private final PodcastListAdapter adapter = new PodcastListAdapter(null, this);

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
    activity = (MainActivity)getActivity();
    rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(activity));
    rv.setItemAnimator(new DefaultItemAnimator());
    rv.setAdapter(adapter);
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), null, this);
    return layout;
  }

  @Override
  public boolean onLongTap(final long pId) {
    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int id) {
        ImageManager.getInstance().deleteImage(pId);
        activity.getContentResolver().delete(Provider.getUri(Provider.T_PODCAST, pId), null, null);
      }
    });
    builder
        .setNegativeButton(R.string.cancel, null)
        .setTitle(activity.getString(R.string.podcast_delete_question))
        .create()
        .show();
    return true;
  }

  @Override
  public void onButtonTap(long id) {}

  static final String[] projection = new String[]{
      Provider.K_ID, Provider.K_PNAME, Provider.K_PDESCR, Provider.K_PFURL, Provider.K_PSTATE,
      Provider.K_PURL, Provider.K_PTSTAMP, Provider.K_PERROR, Provider.K_PSDESCR};
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(
        activity, Provider.podcastUri, projection, null, null, Provider.K_PATSTAMP + " DESC");
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
