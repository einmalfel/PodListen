package com.einmalfel.podlisten;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.einmalfel.podlisten.support.UnitConverter;

public class EpisodeViewHolder extends RecyclerView.ViewHolder {
  private static final String TAG = "EVH";

  private static Context context = null;
  private static PorterDuffColorFilter loadingFilter;
  private static PorterDuffColorFilter playingFilter;
  private static PorterDuffColorFilter loadedFilter;
  private static PorterDuffColorFilter playedFilter;
  private static Drawable playButtonDrawable;
  private static Drawable loadingButtonDrawable;
  private static Drawable pauseButtonDrawable;
  private static Drawable addButtonDrawable;
  private static Drawable loadButtonDrawable;

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
  private final CardView cardView;
  private final TextView episdoeUrlView;
  private long id = 0;
  private boolean expanded = false;
  private int downloaded = -1;
  private int state;
  private String title;
  private String aURL;

  public EpisodeViewHolder(final View layout,
                           final EpisodeListAdapter.ItemClickListener listener,
                           final EpisodeListAdapter adapter) {
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
    cardView = (CardView) layout.findViewById(R.id.card);
    episdoeUrlView = (TextView) layout.findViewById(R.id.episode_url);
    View relativeLayout = layout.findViewById(R.id.card_layout);
    relativeLayout.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        adapter.setExpanded(id, !expanded, getAdapterPosition());
      }
    });
    relativeLayout.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        return listener.onLongTap(
            EpisodeViewHolder.this.id, EpisodeViewHolder.this.title, EpisodeViewHolder.this.state,
            EpisodeViewHolder.this.aURL, EpisodeViewHolder.this.downloaded);
      }
    });
    playAddFrame.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onButtonTap(
            EpisodeViewHolder.this.id, EpisodeViewHolder.this.title, EpisodeViewHolder.this.state,
            EpisodeViewHolder.this.aURL, EpisodeViewHolder.this.downloaded);
      }
    });

    // on S galaxy A3 Lollipop ProgressBar skips drawable update on setProgress(0), cause mProgress
    // doesn't change, it's 0 initially. As drawable is never updated, it doesn't effectively mutate
    // and shares it's state with other ProgressBar drawables.
    progressBar.setMax(Integer.MAX_VALUE);
    progressBar.setProgress(Integer.MAX_VALUE);

    if (context == null) {
      context = PodListenApp.getContext();
      loadingFilter = new PorterDuffColorFilter(
          ContextCompat.getColor(context, R.color.accent_primary), PorterDuff.Mode.MULTIPLY);
      loadedFilter = new PorterDuffColorFilter(
          ContextCompat.getColor(context, R.color.accent_primary_dim), PorterDuff.Mode.MULTIPLY);
      playingFilter = new PorterDuffColorFilter(
          ContextCompat.getColor(context, R.color.accent_secondary), PorterDuff.Mode.MULTIPLY);
      playedFilter = new PorterDuffColorFilter(
          ContextCompat.getColor(context, R.color.accent_secondary_dim), PorterDuff.Mode.MULTIPLY);
      playButtonDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_play_arrow_white_36dp);
      pauseButtonDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_pause_white_36dp);
      addButtonDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_playlist_add_white_36dp);
      loadingButtonDrawable = ContextCompat.getDrawable(context,
                                                        R.mipmap.ic_file_download_white_36dp);
      loadingButtonDrawable.mutate().setColorFilter(MainActivity.disabledFilter);
      loadButtonDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_file_download_white_36dp);
    }
  }

  long getId() {
    return id;
  }

  private void setTextColor(int color) {
    feedTitleText.setTextColor(color);
    descriptionText.setTextColor(color);
    timeSizeText.setTextColor(color);
    dateText.setTextColor(color);
  }

  public void bindEpisode(String title, String description, long id, long pid, long size, int state,
                          String feedTitle, long played, long length, long date, int downloaded,
                          String shortDescr, String errorMessage, PlayerService.State playerState,
                          String url, long downloadId, String aURL, boolean expanded) {
    if (errorMessage == null) {
      episdoeUrlView.setText(TextUtils.isEmpty(url) ? aURL : url);
      episdoeUrlView.setTextColor(ContextCompat.getColor(
          context, playerState.isStopped() ? R.color.text : R.color.text_bright));
    } else {
      episdoeUrlView.setTextColor(ContextCompat.getColor(context, R.color.accent_secondary));
      episdoeUrlView.setText(errorMessage);
    }

    if (id != this.id || expanded != this.expanded) {
      titleText.setText(title);
      if (description == null || description.isEmpty()) {
        dividerBottom.setVisibility(View.GONE);
        descriptionText.setVisibility(View.GONE);
      } else {
        if (expanded) {
          descriptionText.setText(Html.fromHtml(description), TextView.BufferType.SPANNABLE);
        } else {
          descriptionText.setText(shortDescr, TextView.BufferType.NORMAL);
        }
        dividerBottom.setVisibility(View.VISIBLE);
        descriptionText.setVisibility(View.VISIBLE);
      }
      if (feedTitle == null) {
        feedTitleText.setText(R.string.episode_deleted_subscription);
      } else {
        feedTitleText.setText(feedTitle);
      }
    }

    if (expanded != this.expanded) {
      cardView.setCardElevation(UnitConverter.getInstance().dpToPx(expanded ? 8 : 2));
      descriptionText.setSingleLine(!expanded);
      descriptionText.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      feedTitleText.setSingleLine(!expanded);
      feedTitleText.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      episdoeUrlView.setSingleLine(!expanded);
      episdoeUrlView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      titleText.setMaxLines(expanded ? Integer.MAX_VALUE : 2);
      titleText.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
    }

    if (downloaded == Provider.EDFIN_COMPLETE && state != Provider.ESTATE_NEW) {
      if (playerState.isStopped()) {
        progressBar.getProgressDrawable().setColorFilter(playedFilter);
        setTextColor(ContextCompat.getColor(context, R.color.text));
      } else {
        progressBar.getProgressDrawable().setColorFilter(playingFilter);
        setTextColor(ContextCompat.getColor(context, R.color.text_bright));
      }
      progressBar.setIndeterminate(false);
      progressBar.setMax(length < 0 ? 0 : (int) length);
      progressBar.setProgress((int) played);
    } else {
      setTextColor(ContextCompat.getColor(context, R.color.text));
      if (downloaded == Provider.EDFIN_COMPLETE) {
        progressBar.getProgressDrawable().setColorFilter(loadedFilter);
      } else {
        progressBar.getProgressDrawable().setColorFilter(loadingFilter);
      }
      if (downloaded == Provider.EDFIN_MOVING || downloaded == Provider.EDFIN_PROCESSING ||
          (downloadId != 0 && downloaded == 0)) {
        progressBar.setIndeterminate(true);
      } else {
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgress(downloaded == Provider.EDFIN_ERROR ? 0 : (int) downloaded);
      }
    }

    if (downloaded != Provider.EDFIN_COMPLETE && state != Provider.ESTATE_NEW) {
      buttonImage.setContentDescription(context.getString(R.string.episode_action_download));
      if (downloadId == 0) {
        playAddFrame.setEnabled(true);
        buttonImage.setImageDrawable(loadButtonDrawable);
      } else {
        playAddFrame.setEnabled(false);
        buttonImage.setImageDrawable(loadingButtonDrawable);
      }
    } else {
      playAddFrame.setEnabled(true);
      if (state == Provider.ESTATE_NEW) {
        buttonImage.setImageDrawable(addButtonDrawable);
        buttonImage.setContentDescription(context.getString(R.string.episode_action_add));
      } else if (playerState == PlayerService.State.PLAYING) {
        buttonImage.setImageDrawable(pauseButtonDrawable);
        buttonImage.setContentDescription(context.getString(R.string.episode_action_pause));
      } else {
        buttonImage.setImageDrawable(playButtonDrawable);
        buttonImage.setContentDescription(context.getString(R.string.episode_action_play));
      }
    }

    if ((downloaded == Provider.EDFIN_COMPLETE && this.downloaded != Provider.EDFIN_COMPLETE) ||
        id != this.id) {
      StringBuilder timeSize = new StringBuilder();
      if (length > 0) {
        timeSize.append(PodcastHelper.getInstance().shortFormatDurationMs(length));
        timeSize.append(" ");
      }
      if (size > 1024) {
        timeSize.append(PodcastHelper.humanReadableByteCount(size, true));
      }
      timeSizeText.setText(timeSize);
      dateText.setText(PodcastHelper.shortDateFormat(date));
    }

    // use feed image if there is no episode image
    Bitmap image = ImageManager.getInstance().getImage(id);
    if (image == null) {
      image = ImageManager.getInstance().getImage(pid);
    }
    if (image == null) {
      episodeImage.setImageResource(R.drawable.logo);
    } else {
      episodeImage.setImageBitmap(image);
    }

    this.id = id;
    this.expanded = expanded;
    this.downloaded = downloaded;
    this.title = title;
    this.state = state;
    this.aURL = aURL;
  }
}
