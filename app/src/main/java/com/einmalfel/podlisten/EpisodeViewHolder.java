package com.einmalfel.podlisten;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class EpisodeViewHolder extends RecyclerView.ViewHolder {
  public final TextView descriptionView;
  public final TextView titleView;
  public final ImageView downloadedImage;

  public EpisodeViewHolder(View layout) {
    super(layout);
    titleView = (TextView) layout.findViewById(R.id.episode_title);
    descriptionView = (TextView) layout.findViewById(R.id.episode_description);
    downloadedImage = (ImageView) layout.findViewById(R.id.downloaded_image);
  }
}
