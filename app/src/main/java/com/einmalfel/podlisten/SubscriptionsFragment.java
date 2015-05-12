package com.einmalfel.podlisten;


import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


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
    Button b = (Button) layout.findViewById(R.id.subscribe_button);
    b.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final EditText input = new EditText(activity);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
            String url = input.getText().toString().trim();
            if (url.isEmpty()) {
              Toast.makeText(activity, R.string.subscribe_failed_empty, Toast.LENGTH_SHORT).show();
            } else {
              addSubscription(url);
            }
          }
        });
        builder
            .setNegativeButton(R.string.cancel, null)
            .setTitle(activity.getString(R.string.enter_feed_url))
            .setView(input)
            .create()
            .show();
      }
    });
    return layout;
  }

  public boolean addSubscription(String url) {
    ContentValues values = new ContentValues();
    values.put(Provider.K_PFURL, url);
    values.put(Provider.K_ID, (long) url.hashCode() - Integer.MIN_VALUE);
    Uri result = activity.getContentResolver().insert(Provider.podcastUri, values);
    if (result == null) {
      Toast.makeText(activity, R.string.already_subscribed, Toast.LENGTH_SHORT).show();
      return true;
    } else {
      activity.refresh();
      return false;
    }
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
        new String[]{Provider.K_ID, Provider.K_PNAME, Provider.K_PDESCR, Provider.K_PFURL, Provider.K_PSTATE}, null, null, null);
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
