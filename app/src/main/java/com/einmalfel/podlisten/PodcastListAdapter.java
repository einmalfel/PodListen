package com.einmalfel.podlisten;


import android.database.Cursor;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.xml.sax.XMLReader;

public class PodcastListAdapter extends CursorRecyclerAdapter<PodcastViewHolder> {
  private static final String TAG = "PLA";

  public PodcastListAdapter(Cursor cursor) {
    super(cursor);
    setHasStableIds(true);
  }

  @Override
  public void onBindViewHolderCursor(PodcastViewHolder holder, Cursor cursor) {
    holder.titleView.setText(cursor.getString(cursor.getColumnIndex(Provider.K_PNAME)));
    Spanned spannedText = strToSpanned(cursor.getString(cursor.getColumnIndex(Provider.K_PDESCR)));
    holder.descriptionView.setText(spannedText, TextView.BufferType.SPANNABLE);
  }

  @Override
  public PodcastViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.podcast_list_element, parent, false);
    return new PodcastViewHolder(v);
  }

  public static Spanned strToSpanned(String text) {
    // TODO rework this shit
    String stringNoImg = text
        .replaceAll("<img.+?>", "")
        .replaceAll("<br.*?/>\\s*<br.*/>", "<br/>")
        .replaceAll("<p/?>", "");
    return  Html.fromHtml(stringNoImg, null, new Html.TagHandler() {
      @Override
      public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
        if (tag.equalsIgnoreCase("li")) {
          output.append(opening ? "\u2022 " : "\n");
        }
      }
    });
  }
}
