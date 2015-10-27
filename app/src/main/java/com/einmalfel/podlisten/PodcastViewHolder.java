package com.einmalfel.podlisten;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.einmalfel.podlisten.support.PatchedTextView;

public class PodcastViewHolder extends RecyclerView.ViewHolder {
  private final PatchedTextView descriptionView;
  private final TextView titleView;
  private final TextView urlView;
  private final TextView statusView;
  private final ImageView imageView;
  private final Context context;
  private final View dividerBottom;
  private long id = 0;
  private boolean expanded = false;

  public PodcastViewHolder(View layout, final EpisodeListAdapter.ItemClickListener listener,
                           final PodcastListAdapter adapter) {
    super(layout);
    context = layout.getContext();
    titleView = (TextView) layout.findViewById(R.id.podcast_title);
    descriptionView = (PatchedTextView) layout.findViewById(R.id.podcast_description);
    dividerBottom = layout.findViewById(R.id.description_divider);
    urlView = (TextView) layout.findViewById(R.id.podcast_url);
    statusView = (TextView) layout.findViewById(R.id.podcast_status);
    imageView = (ImageView) layout.findViewById(R.id.podcast_image);
    RelativeLayout relativeLayout = (RelativeLayout) layout.findViewById(R.id.card_layout);
    relativeLayout.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        adapter.setExpanded(id, !expanded, getAdapterPosition());
      }
    });
    relativeLayout.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        return listener.onLongTap(id);
      }
    });
  }

  void bind(int state, String title, String description, String url, String podcastPage, long id,
            String shortDescr, String error, long timestamp, boolean expanded) {
    titleView.setText(title);
    titleView.setText(title == null ? context.getString(R.string.podcast_no_title) : title);
    urlView.setText(podcastPage == null ? url : podcastPage);
    if (state == Provider.PSTATE_LAST_REFRESH_FAILED) {
      statusView.setText(context.getString(R.string.podcast_refresh_failed, error));
      statusView.setTextColor(ContextCompat.getColor(context, R.color.accent_secondary));
    } else {
      statusView.setTextColor(ContextCompat.getColor(context, R.color.text));
      if (state == Provider.PSTATE_NEW) {
        statusView.setText(R.string.podcast_not_loaded_yet);
      } else {
        statusView.setText(context.getString(R.string.podcast_refresh_time,
                                             DateUtils.getRelativeTimeSpanString(timestamp)));
      }
    }

    if (description == null || description.isEmpty()) {
      descriptionView.setVisibility(View.GONE);
      dividerBottom.setVisibility(View.GONE);
    } else {
      if (expanded) {
        descriptionView.setText(Html.fromHtml(description), TextView.BufferType.SPANNABLE);
      } else {
        descriptionView.setText(shortDescr, TextView.BufferType.NORMAL);
      }
      descriptionView.setVisibility(View.VISIBLE);
      dividerBottom.setVisibility(View.VISIBLE);
    }
    descriptionView.setSingleLine(!expanded);

    Bitmap image = ImageManager.getInstance().getImage(id);
    if (image == null) {
      imageView.getLayoutParams().width = PodcastHelper.getInstance().minImageWidthPX;
      imageView.setImageResource(R.drawable.logo);
    } else {
      imageView.getLayoutParams().width = PodcastHelper.getInstance().getListImageWidth(image);
      imageView.setImageBitmap(image);
    }

    this.id = id;
    this.expanded = expanded;
  }
}
