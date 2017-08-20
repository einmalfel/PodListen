package com.einmalfel.podlisten;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.einmalfel.podlisten.support.PredictiveAnimatiedLayoutManager;
import com.einmalfel.podlisten.support.UnitConverter;

public class FeedHistoryFragment extends DialogFragment implements
    LoaderManager.LoaderCallbacks<Cursor>, FeedHistoryAdapter.HistoryEpisodeListener {
  private static final String TAG = "FHF";
  private static final String ARG_PODCAST_ID = "Podcast_ID";
  private static final String ARG_PODCAST_TITLE = "Podcast_Title";
  private static final int LOADER_ID = 20;

  private long podcastId;
  private String podcastTitle;
  private FeedHistoryAdapter adapter = new FeedHistoryAdapter(this);
  private ViewGroup container;

  public FeedHistoryFragment() {
  }

  public static FeedHistoryFragment newInstance(long podcastId, String title) {
    FeedHistoryFragment fragment = new FeedHistoryFragment();
    Bundle args = new Bundle();
    args.putLong(ARG_PODCAST_ID, podcastId);
    args.putString(ARG_PODCAST_TITLE, title);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (getArguments() != null) {
      podcastId = getArguments().getLong(ARG_PODCAST_ID);
      podcastTitle = getArguments().getString(ARG_PODCAST_TITLE);
      getLoaderManager().initLoader(LOADER_ID, null, this);
    } else {
      Log.wtf(TAG, "Empty fragment arguments");
    }

  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    this.container = container;
    return null;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    View layout = getActivity().getLayoutInflater().inflate(R.layout.common_list, container);
    RecyclerView rv = (RecyclerView) layout.findViewById(R.id.recycler_view);
    rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(getContext()));
    rv.setItemAnimator(new DefaultItemAnimator());
    rv.setAdapter(adapter);

    TextView titleView = new TextView(getContext());
    int eightDpInPx = UnitConverter.getInstance().dpToPx(8);
    titleView.setText(podcastTitle);
    titleView.setPadding(eightDpInPx, eightDpInPx, eightDpInPx, eightDpInPx);
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    titleView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_bright));
    titleView.setSingleLine();
    titleView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.background_contrast));
    titleView.setGravity(Gravity.CENTER);

    return new AlertDialog.Builder(getContext()).setView(layout).setCustomTitle(titleView).create();
  }

  @Override
  public void onEpisodeButtonTap(long id, int state) {
    if (state == Provider.ESTATE_IN_PLAYLIST) {
      ContentValues cv = new ContentValues(1);
      cv.put(Provider.K_ESTATE, Provider.ESTATE_GONE);
      getContext().getContentResolver().update(
          Provider.getUri(Provider.T_EPISODE, id), cv, null, null);
      BackgroundOperations.startCleanupEpisodes(getContext(), Provider.ESTATE_GONE);
    } else {
      ContentValues cv = new ContentValues(1);
      cv.put(Provider.K_ESTATE, Provider.ESTATE_IN_PLAYLIST);
      getContext().getContentResolver().update(
          Provider.getUri(Provider.T_EPISODE, id), cv, null, null);
      if (Preferences.getInstance().getAutoDownloadMode() != Preferences.AutoDownloadMode.NEVER) {
        getContext().sendBroadcast(new Intent(DownloadReceiver.UPDATE_QUEUE_ACTION));
      }
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(getContext(),
                            Provider.episodeUri,
                            FeedHistoryAdapter.COLUMNS_NEEDED,
                            Provider.K_EPID + " == " + podcastId,
                            null,
                            Provider.K_EDATE + " DESC");
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
