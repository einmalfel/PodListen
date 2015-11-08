package com.einmalfel.podlisten;

import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

class SearchElementHolder extends RecyclerView.ViewHolder {

  private final TextView titleView;
  private final TextView descriptionView;
  private final TextView urlView;
  private final TextView frequencyView;
  private final View dividerBottom;

  private String rss_url;
  private boolean expanded;
  private long id = -1;

  public SearchElementHolder(ViewGroup layout,
                             final SearchAdapter.SearchClickListener listener,
                             final SearchAdapter adapter) {
    super(layout);
    titleView = (TextView) layout.findViewById(R.id.podcast_title);
    urlView = (TextView) layout.findViewById(R.id.podcast_url);
    frequencyView = (TextView) layout.findViewById(R.id.podcast_frequency);
    descriptionView = (TextView) layout.findViewById(R.id.podcast_description);
    dividerBottom = layout.findViewById(R.id.description_divider);
    ImageButton addButton = (ImageButton) layout.findViewById(R.id.podcast_button);
    addButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onPodcastButtonTap(rss_url);
      }
    });
    View relativeLayout = layout.findViewById(R.id.card_layout);
    relativeLayout.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        adapter.setExpanded(id, !expanded, getAdapterPosition());
      }
    });

  }

  void bind(String title, String description, String rss_url, String url, long period, long id,
            boolean expanded) {
    if (this.id != id) {
      titleView.setText(title);
      descriptionView.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);
      dividerBottom.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);
      descriptionView.setText(description);
      urlView.setText(url != null && !url.isEmpty() ? url : rss_url);
      double freq = 30d * 24 * 60 * 60 * 1000 / period;
      frequencyView.setText(String.format("Frequency: %.1f episodes/month", freq));
      this.rss_url = rss_url;
      this.id = id;
    }

    if (this.expanded != expanded) {
      descriptionView.setSingleLine(!expanded);
      // without next line TextView still ellipsize first line when single line mode is turned off
      descriptionView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      this.expanded = expanded;
    }
  }
}
