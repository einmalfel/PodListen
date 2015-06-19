package com.einmalfel.podlisten;

import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class EpisodeViewHolder extends RecyclerView.ViewHolder {
  private final TextView descriptionView;
  private final TextView titleView;
  private final ImageView downloadedImage;

  public EpisodeViewHolder(View layout) {
    super(layout);
    titleView = (TextView) layout.findViewById(R.id.episode_title);
    descriptionView = (TextView) layout.findViewById(R.id.episode_description);
    downloadedImage = (ImageView) layout.findViewById(R.id.downloaded_image);
  }

  public void bindEpisode(String title, String description, int downloaded) {
    titleView.setText(title);
    descriptionView.setText(Html.fromHtml(description), TextView.BufferType.SPANNABLE);
    downloadedImage.setVisibility(downloaded == 100 ? View.VISIBLE : View.GONE);
  }
}
