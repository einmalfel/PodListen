package com.einmalfel.podlisten;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class EpisodeListAdapter extends CursorRecyclerAdapter<EpisodeViewHolder>
    implements View.OnClickListener {
  private final Context context;
  private static final String TAG = "ELA";
  private final MainActivity.Pages hostingPage;


  public EpisodeListAdapter(Context activityContext, Cursor cursor, MainActivity.Pages page) {
    super(cursor);
    context = activityContext;
    hostingPage = page;
    setHasStableIds(true);
  }

  @Override
  public void onBindViewHolderCursor(EpisodeViewHolder holder, Cursor cursor) {
    holder.titleView.setText(cursor.getString(cursor.getColumnIndex(Provider.K_ENAME)));
    Spanned spannedText = PodcastListAdapter.strToSpanned(
        cursor.getString(cursor.getColumnIndex(Provider.K_EDESCR)));
    holder.descriptionView.setText(spannedText, TextView.BufferType.SPANNABLE);
    holder.downloadedImage.setVisibility(
        cursor.getInt(cursor.getColumnIndex(Provider.K_EDFIN)) == 100 ? View.VISIBLE : View.GONE);
  }

  @Override
  public void onClick(View v) {
    Log.d(TAG, "Clicked ID " + (Long) v.getTag());
    switch (hostingPage) {
      case NEW_EPISODES:
        ContentValues values = new ContentValues();
        values.put(Provider.K_ESTATE, Provider.ESTATE_IN_PLAYLIST);
        int result = context.getContentResolver().update(Provider.getUri(
            Provider.T_EPISODE, (Long) v.getTag()), values, null, null);
        Log.e(TAG, Integer.toString(result));
        break;
      case PLAYLIST:
        //play
        break;
      default:
        Log.e(TAG, "Unexpected Page " + hostingPage);
    }
  }

  @Override
  public EpisodeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.episode_list_element, parent, false);
    return new EpisodeViewHolder(v);
  }
}
