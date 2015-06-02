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
  private static String htmlDescriptionField;
  private static String urlField;
  private static String titleField;
  private CardView uViewCard;
  private CardView tViewCard;
  private CardView dViewCard;
  private static PlayerFragment currentInstance = null;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    activity = (MainActivity) getActivity();
    View layout = inflater.inflate(R.layout.fragment_player, container, false);
    tView = (TextView) layout.findViewById(R.id.player_title);
    dView = (TextView) layout.findViewById(R.id.player_description);
    uView = (TextView) layout.findViewById(R.id.player_url);
    tViewCard = (CardView) layout.findViewById(R.id.player_title_card);
    dViewCard = (CardView) layout.findViewById(R.id.player_description_card);
    uViewCard = (CardView) layout.findViewById(R.id.player_url_card);
    uView.setMovementMethod(LinkMovementMethod.getInstance());
    activity.getSupportLoaderManager().initLoader(activityPage.ordinal(), Bundle.EMPTY, this);
    currentInstance = this;
    updateTextViews();
    return layout;
  }

  public static void updateTextViews() {
    if (currentInstance == null) {
      return;
    }
    if (titleField != null) {
      currentInstance.tView.setText(titleField);
    }
    if (htmlDescriptionField != null) {
      Spanned spanned = PodcastListAdapter.strToSpanned(htmlDescriptionField);
      if (spanned.toString().trim().isEmpty()) {
        currentInstance.dViewCard.setVisibility(View.GONE);
      } else {
        currentInstance.dView.setText(spanned);
        currentInstance.dViewCard.setVisibility(View.VISIBLE);
      }
    } else {
      currentInstance.dViewCard.setVisibility(View.GONE);
    }
    if (urlField == null || urlField.trim().isEmpty()) {
      currentInstance.uViewCard.setVisibility(View.GONE);
    } else {
      currentInstance.uView.setText(urlField);
      currentInstance.uViewCard.setVisibility(View.VISIBLE);
    }
  }

  public static void setText(String title, String htmlDescription, String url) {
    titleField = title;
    htmlDescriptionField = htmlDescription;
    urlField = url;
    updateTextViews();
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
