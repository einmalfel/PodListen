package com.einmalfel.podlisten;


import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.CardView;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class PlayerFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
  private static final MainActivity.Pages activityPage = MainActivity.Pages.PLAYER;
  public static final String TAG = "PF";
  private MainActivity activity;
  private TextView tView;
  private TextView dView;
  private TextView uView;
  private CardView uViewCard;
  private CardView dViewCard;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
      savedInstanceState) {
    activity = (MainActivity) getActivity();
    View layout = inflater.inflate(R.layout.fragment_player, container, false);
    tView = (TextView) layout.findViewById(R.id.player_title);
    dView = (TextView) layout.findViewById(R.id.player_description);
    uView = (TextView) layout.findViewById(R.id.player_url);
    dViewCard = (CardView) layout.findViewById(R.id.player_description_card);
    uViewCard = (CardView) layout.findViewById(R.id.player_url_card);
    uView.setMovementMethod(LinkMovementMethod.getInstance());
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), Bundle.EMPTY, this);
    setText(null, null, null);
    return layout;
  }

  public void setText(String title, String htmlDescription, String url) {
    if (title != null) {
      tView.setText(title);
    }
    if (htmlDescription != null) {
      Spanned spanned = PodcastListAdapter.strToSpanned(htmlDescription);
      if (spanned.toString().trim().isEmpty()) {
        dViewCard.setVisibility(View.GONE);
      } else {
        dView.setText(spanned);
        dViewCard.setVisibility(View.VISIBLE);
      }
    } else {
      dViewCard.setVisibility(View.GONE);
    }
    if (url == null || url.trim().isEmpty()) {
      uViewCard.setVisibility(View.GONE);
    } else {
      uView.setText(url);
      uViewCard.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(activity,
        Provider.episodeUri,
        new String[]{Provider.K_ENAME, Provider.K_EDESCR, Provider.K_EURL},
        Provider.K_ESTATE + " = ? AND " + Provider.K_EDFIN + " = ?",
        new String[]{Integer.toString(Provider.ESTATE_IN_PLAYLIST), Integer.toString(100)},
        Provider.K_EDATE);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    if (activity.connection.service == null ||
        activity.connection.service.getState() == PlayerService.State.STOPPED) {
      if (data.moveToFirst()) {
        setText(
            data.getString(data.getColumnIndex(Provider.K_ENAME)),
            data.getString(data.getColumnIndex(Provider.K_EDESCR)),
            data.getString(data.getColumnIndex(Provider.K_EURL)));
      } else {
        setText(activity.getString(R.string.empty_playlist), null, null);
      }
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
  }
}
