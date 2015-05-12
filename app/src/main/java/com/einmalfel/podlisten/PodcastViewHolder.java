package com.einmalfel.podlisten;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

public class PodcastViewHolder extends RecyclerView.ViewHolder {
  public final TextView descriptionView;
  public final TextView titleView;

  public PodcastViewHolder(View layout) {
    super(layout);
    titleView = (TextView) layout.findViewById(R.id.podcast_title);
    descriptionView = (TextView) layout.findViewById(R.id.podcast_description);
  }
}
