package com.einmalfel.podlisten;

import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EpisodeViewHolder extends RecyclerView.ViewHolder {
  private static final String TAG = "EVH";
  private final TextView descriptionText;
  private final TextView titleText;
  private final TextView feedTitleText;
  private final ImageView playImage;
  private final ImageView addImage;
  private final ImageView episodeImage;
  private final View dividerBottom;
  private final TextView dateText;
  private final TextView timeSizeText;
  private long id = 0;
  private boolean isExpanded = false;

  boolean getExpanded() {
    return isExpanded;
  }

  void setExpanded(boolean expanded) {
    isExpanded = expanded;
    descriptionText.setSingleLine(!expanded);
  }

  long getId() {
    return id;
  }

  public EpisodeViewHolder(final View layout, final EpisodeListAdapter.EpisodeClickListener listener) {
    super(layout);
    this.id = 0;
    titleText = (TextView) layout.findViewById(R.id.episode_title);
    descriptionText = (TextView) layout.findViewById(R.id.episode_description);
    dividerBottom = layout.findViewById(R.id.description_divider);
    LinearLayout playAddFrame = (LinearLayout) layout.findViewById(R.id.play_add_frame);
    dateText = (TextView) layout.findViewById(R.id.date);
    feedTitleText = (TextView) layout.findViewById(R.id.feed_title);
    descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
    playImage = (ImageView) layout.findViewById(R.id.play_image);
    addImage = (ImageView) layout.findViewById(R.id.add_image);
    timeSizeText = (TextView) layout.findViewById(R.id.time_size);
    episodeImage = (ImageView) layout.findViewById(R.id.episode_image);
    layout.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        setExpanded(!getExpanded());
      }
    });
    layout.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        return listener.onLongTap(EpisodeViewHolder.this.id);
      }
    });
    playAddFrame.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onButtonTap(EpisodeViewHolder.this.id);
      }
    });
  }

  public void bindEpisode(String title, String description, long id, long pid, long size, int state,
                          String feedTitle, long played, long length, long date, long downloaded) {
    titleText.setText(title);
    if (description.trim().isEmpty()) {
      dividerBottom.setVisibility(View.GONE);
      descriptionText.setVisibility(View.GONE);
    } else {
      dividerBottom.setVisibility(View.VISIBLE);
      descriptionText.setVisibility(View.VISIBLE);
    }
    descriptionText.setText(Html.fromHtml(description), TextView.BufferType.SPANNABLE);
    feedTitleText.setText(feedTitle);

    playImage.setVisibility(View.INVISIBLE);
    addImage.setVisibility(View.INVISIBLE);
    if (state == Provider.PSTATE_NEW) {
      addImage.setVisibility(View.VISIBLE);
    } else if (downloaded == 100) {
      playImage.setVisibility(View.VISIBLE);
    }

    if (length != 0) {
      timeSizeText.setText(DateUtils.formatElapsedTime(length / 1000) + " " +
          PodcastHelper.humanReadableByteCount(size, true));
    } else {
      timeSizeText.setText(PodcastHelper.humanReadableByteCount(size, true));
    }
    dateText.setText(DateUtils.getRelativeTimeSpanString(date));
    episodeImage.setImageBitmap(ImageManager.getInstance().getImage(pid));
    this.id = id;
  }
}
