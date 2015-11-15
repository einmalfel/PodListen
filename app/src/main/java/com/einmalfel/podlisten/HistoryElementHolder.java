package com.einmalfel.podlisten;

import android.content.Context;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.einmalfel.podlisten.support.UnitConverter;

public class HistoryElementHolder extends RecyclerView.ViewHolder {
  private final TextView titleView;
  private final TextView urlView;
  private final TextView dateView;
  private final TextView descriptionView;
  private final View dividerBottom;
  private final CardView cardView;
  private final ImageButton button;
  private long id;
  private boolean expanded;
  private Context context = PodListenApp.getContext();
  private int state = -1;

  public HistoryElementHolder(
      ViewGroup layout, final FeedHistoryAdapter.HistoryEpisodeListener listener,
      final FeedHistoryAdapter adapter) {
    super(layout);
    titleView = (TextView) layout.findViewById(R.id.episode_title);
    urlView = (TextView) layout.findViewById(R.id.episode_url);
    dateView = (TextView) layout.findViewById(R.id.pub_date);
    descriptionView = (TextView) layout.findViewById(R.id.episode_description);
    dividerBottom = layout.findViewById(R.id.description_divider);
    cardView = (CardView) layout.findViewById(R.id.card);
    button = (ImageButton) layout.findViewById(R.id.episode_button);
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onEpisodeButtonTap(id, state);
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

  public void bind(String title, String description, String shortDescr, String url, long date,
                   int state, long id, boolean expanded) {
    if (id != this.id || expanded != this.expanded) {
      if (expanded) {
        descriptionView.setText(Html.fromHtml(description), TextView.BufferType.SPANNABLE);
      } else {
        descriptionView.setText(shortDescr, TextView.BufferType.NORMAL);
      }
    }

    if (id != this.id) {
      titleView.setText(title);
      urlView.setText(url);
      descriptionView.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);
      dividerBottom.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);
      dateView.setText(
          context.getString(R.string.episode_published, PodcastHelper.shortDateFormat(date)));
      this.id = id;
    }

    if (this.state != state) {
      button.setImageResource(state == Provider.ESTATE_IN_PLAYLIST ? R.mipmap.ic_delete_white_48dp :
                                  R.mipmap.ic_add_white_48dp);
      this.state = state;
    }

    if (expanded != this.expanded) {
      cardView.setCardElevation(UnitConverter.getInstance().dpToPx(expanded ? 8 : 2));
      descriptionView.setSingleLine(!expanded);
      descriptionView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      titleView.setSingleLine(!expanded);
      titleView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      urlView.setSingleLine(!expanded);
      urlView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      dateView.setSingleLine(!expanded);
      dateView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
      this.expanded = expanded;
    }
  }
}
