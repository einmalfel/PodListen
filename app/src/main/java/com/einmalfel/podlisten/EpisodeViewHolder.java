package com.einmalfel.podlisten;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class EpisodeViewHolder extends RecyclerView.ViewHolder {
  private static final String TAG = "EVH";

  private static Context context = null;
  private static PorterDuffColorFilter loadingFilter;
  private static PorterDuffColorFilter playingFilter;
  private static PorterDuffColorFilter loadedFilter;
  private static Drawable playButtonDrawable;
  private static Drawable loadingButtonDrawable;
  private static Drawable pauseButtonDrawable;
  private static Drawable addButtonDrawable;

  private final TextView descriptionText;
  private final TextView titleText;
  private final TextView feedTitleText;
  private final ImageView buttonImage;
  private final ImageView episodeImage;
  private final View dividerBottom;
  private final TextView dateText;
  private final TextView timeSizeText;
  private final ProgressBar progressBar;
  private final FrameLayout playAddFrame;
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
    playAddFrame = (FrameLayout) layout.findViewById(R.id.play_add_frame);
    dateText = (TextView) layout.findViewById(R.id.date);
    feedTitleText = (TextView) layout.findViewById(R.id.feed_title);
    descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
    buttonImage = (ImageView) layout.findViewById(R.id.play_add_image);
    timeSizeText = (TextView) layout.findViewById(R.id.time_size);
    episodeImage = (ImageView) layout.findViewById(R.id.episode_image);
    progressBar = (ProgressBar) layout.findViewById(R.id.play_load_progress);
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

    if (context == null) {
      context = PodListenApp.getContext();
      loadingFilter = new PorterDuffColorFilter(
          context.getResources().getColor(R.color.download_progress), PorterDuff.Mode.MULTIPLY);
      loadedFilter = new PorterDuffColorFilter(
          context.getResources().getColor(R.color.downloaded_progress), PorterDuff.Mode.MULTIPLY);
      playingFilter = new PorterDuffColorFilter(
          context.getResources().getColor(R.color.play_progress), PorterDuff.Mode.MULTIPLY);
      playButtonDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_play_arrow_white_36dp);
      pauseButtonDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_pause_white_36dp);
      addButtonDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_playlist_add_white_36dp);
      loadingButtonDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_play_arrow_white_36dp);
      loadingButtonDrawable.mutate().setColorFilter(MainActivity.disabledFilter);
    }
  }

  public void bindEpisode(String title, String description, long id, long pid, long size, int state,
                          String feedTitle, long played, long length, long date, long downloaded,
                          boolean playingNow) {
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

    if (downloaded == 100 && state != Provider.ESTATE_NEW) {
      progressBar.getProgressDrawable().setColorFilter(playingFilter);
      progressBar.setMax((int) length);
      progressBar.setProgress((int) played);
    } else {
      if (downloaded == 100) {
        progressBar.getProgressDrawable().setColorFilter(loadedFilter);
      } else {
        progressBar.getProgressDrawable().setColorFilter(loadingFilter);
      }
      progressBar.setMax(100);
      progressBar.setProgress((int) downloaded);
    }

    if (downloaded != 100 && state != Provider.ESTATE_NEW) {
      playAddFrame.setEnabled(false);
      buttonImage.setImageDrawable(loadingButtonDrawable);
    } else {
      playAddFrame.setEnabled(true);
      if (state == Provider.ESTATE_NEW) {
        buttonImage.setImageDrawable(addButtonDrawable);
      } else if (playingNow) {
        buttonImage.setImageDrawable(pauseButtonDrawable);
      } else {
        buttonImage.setImageDrawable(playButtonDrawable);
      }
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
