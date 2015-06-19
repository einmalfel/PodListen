package com.einmalfel.podlisten;

import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class EpisodeListAdapter extends CursorRecyclerAdapter<EpisodeViewHolder> {
  private static final String TAG = "ELA";

  public EpisodeListAdapter(Cursor cursor) {
    super(cursor);
    setHasStableIds(true);
  }

  @Override
  public void onBindViewHolderCursor(EpisodeViewHolder holder, Cursor cursor) {
    holder.bindEpisode(
        cursor.getString(cursor.getColumnIndex(Provider.K_ENAME)),
        cursor.getString(cursor.getColumnIndex(Provider.K_EDESCR)),
        cursor.getInt(cursor.getColumnIndex(Provider.K_EDFIN)));
  }

  @Override
  public EpisodeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.episode_list_element, parent, false);
    return new EpisodeViewHolder(v);
  }
}
