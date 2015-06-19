package com.einmalfel.podlisten;

import android.database.Cursor;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class EpisodeListAdapter extends CursorRecyclerAdapter<EpisodeViewHolder> {
  private static final String TAG = "ELA";

  public EpisodeListAdapter(Cursor cursor) {
    super(cursor);
    setHasStableIds(true);
  }

  @Override
  public void onBindViewHolderCursor(EpisodeViewHolder holder, Cursor cursor) {
    holder.titleView.setText(cursor.getString(cursor.getColumnIndex(Provider.K_ENAME)));
    Spanned spannedText = Html.fromHtml(
    Spanned spannedText = PodcastListAdapter.strToSpanned(
        cursor.getString(cursor.getColumnIndex(Provider.K_EDESCR)));
    holder.descriptionView.setText(spannedText, TextView.BufferType.SPANNABLE);
    holder.downloadedImage.setVisibility(
        cursor.getInt(cursor.getColumnIndex(Provider.K_EDFIN)) == 100 ? View.VISIBLE : View.GONE);
  }

  @Override
  public EpisodeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.episode_list_element, parent, false);
    return new EpisodeViewHolder(v);
  }
}
