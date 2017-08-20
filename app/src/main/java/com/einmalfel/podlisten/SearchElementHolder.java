package com.einmalfel.podlisten;

import android.content.Context;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.einmalfel.podlisten.support.UnitConverter;

class SearchElementHolder extends RecyclerView.ViewHolder {

  private final TextView titleView;
  private final TextView descriptionView;
  private final TextView urlView;
  private final TextView frequencyView;
  private final View dividerBottom;
  private final CardView cardView;
  private final Context context;

  private String rssUrl;
  private boolean expanded;
  private long id = -1;

  public SearchElementHolder(ViewGroup layout,
                             final SearchAdapter.SearchClickListener listener,
                             final SearchAdapter adapter) {
    super(layout);
    context = layout.getContext();
    titleView = (TextView) layout.findViewById(R.id.podcast_title);
    urlView = (TextView) layout.findViewById(R.id.podcast_url);
    frequencyView = (TextView) layout.findViewById(R.id.podcast_frequency);
    descriptionView = (TextView) layout.findViewById(R.id.podcast_description);
    dividerBottom = layout.findViewById(R.id.description_divider);
    cardView = (CardView) layout.findViewById(R.id.card);
    ImageButton addButton = (ImageButton) layout.findViewById(R.id.podcast_button);
    addButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        listener.onPodcastButtonTap(rssUrl);
      }
    });
    View relativeLayout = layout.findViewById(R.id.card_layout);
    relativeLayout.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        adapter.setExpanded(id, !expanded, getAdapterPosition());
      }
    });

  }

  void bind(String title, String description, String rssUrl, String url, long period, long id,
            boolean expanded) {
    if (this.id != id) {
      titleView.setText(title);
      descriptionView.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);
      dividerBottom.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);
      descriptionView.setText(description);
      urlView.setText(url != null && !url.isEmpty() ? url : rssUrl);
      double freq = 30d * 24 * 60 * 60 * 1000 / period;
      frequencyView.setText(context.getString(R.string.search_frequency, freq));
      this.rssUrl = rssUrl;
      this.id = id;
    }

    if (this.expanded != expanded) {
      if (expanded) {
        cardView.setCardElevation(UnitConverter.getInstance().dpToPx(8));
      } else {
        cardView.setCardElevation(UnitConverter.getInstance().dpToPx(2));
      }
      descriptionView.setSingleLine(!expanded);
      // without next line TextView still ellipsize first line when single line mode is turned off
      descriptionView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      titleView.setSingleLine(!expanded);
      titleView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      urlView.setSingleLine(!expanded);
      urlView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      frequencyView.setSingleLine(!expanded);
      frequencyView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      this.expanded = expanded;
    }
  }
}
