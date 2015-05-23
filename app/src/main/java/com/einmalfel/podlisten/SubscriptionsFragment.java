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

import com.einmalfel.podlisten.support.PredictiveAnimatiedLayoutManager;
import com.einmalfel.podlisten.support.RecyclerItemClickListener;

import java.util.ArrayList;
import java.util.Collections;


public class SubscriptionsFragment extends Fragment
    implements LoaderManager.LoaderCallbacks<Cursor>, RecyclerItemClickListener.OnItemClickListener {
  private MainActivity activity;
  private static final MainActivity.Pages activityPage = MainActivity.Pages.SUBSCRIPTIONS;
  private static final String TAG = "SSF";
  private static final String[] samples = {
      "http://podster.fm/rss.xml?pid=15",
      "http://www.radio-t.com/podcast.rss",
      "http://podster.fm/rss.xml?pid=33",
      "http://www.npr.org/rss/podcast.php?id=500005",
      "http://thepetesantillishow.com/feed/",
      "http://www.cbc.ca/podcasting/includes/hourlynews.xml",
      "http://runetologia.podfm.ru/rss/rss.xml"
  };
  private static ArrayList<String> sampleList = null;
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
          @Override
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
    Button sampleButton = (Button) layout.findViewById(R.id.sample_button);
    sampleButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (sampleList == null) {
          sampleList = new ArrayList<String>(samples.length);
          Collections.addAll(sampleList, samples);
        }
        if (sampleList.isEmpty()) {
          return;
        }
        String nextSample = sampleList.remove(0);
        addSubscription(nextSample);
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
    final long pID = adapter.getItemId(position);
    Log.d(TAG, "long tap " + Long.toString(pID));
    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int id) {
        activity.getContentResolver().delete(Provider.getUri(Provider.T_PODCAST, pID), null, null);
      }
    });
    builder
        .setNegativeButton(R.string.cancel, null)
        .setTitle(activity.getString(R.string.delete_subscription))
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
