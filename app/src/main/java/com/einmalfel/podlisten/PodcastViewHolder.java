package com.einmalfel.podlisten;

import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
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
  private long id = 0;
  private boolean expanded = false;

  public PodcastViewHolder(View layout, final EpisodeListAdapter.ItemClickListener listener,
                           final PodcastListAdapter adapter) {
    super(layout);
    titleView = (TextView) layout.findViewById(R.id.podcast_title);
    descriptionView = (PatchedTextView) layout.findViewById(R.id.podcast_description);
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

  void bind(int state, String title, String description, String url, long id, boolean expanded) {
    titleView.setText(title);
    if (state == Provider.PSTATE_NEW) {
      statusView.setText("Feed isn't loaded yet");
    } else {
      titleView.setText(title == null ? "No title" : title);
      descriptionView.setText(description == null ? "No description" : Html.fromHtml(description),
                              TextView.BufferType.SPANNABLE);
      urlView.setText(url == null ? "No url" : url);
    }
    descriptionView.setSingleLine(!expanded);

    Bitmap image = ImageManager.getInstance().getImage(id);
    if (image == null) {
      imageView.getLayoutParams().width = PodcastHelper.getInstance().minImageWidthPX;
      imageView.setImageResource(R.drawable.main_icon);
    } else {
      imageView.getLayoutParams().width = PodcastHelper.getInstance().getListImageWidth(image);
      imageView.setImageBitmap(image);
    }

    this.id = id;
    this.expanded = expanded;
  }
}
