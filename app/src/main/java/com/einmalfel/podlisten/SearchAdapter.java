package com.einmalfel.podlisten;

import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.einmalfel.podlisten.thirdparty.CursorRecyclerAdapter;

import java.util.HashSet;
import java.util.Set;

class SearchAdapter extends CursorRecyclerAdapter {
  public interface SearchClickListener {
    void onPodcastButtonTap(String rssUrl);
  }

  private final Set<Long> expandedElements = new HashSet<>(10);
  private final SearchClickListener listener;

  public SearchAdapter(SearchClickListener listener) {
    super(null);
    this.listener = listener;
  }

  @Override
  public void onBindViewHolderCursor(RecyclerView.ViewHolder holder, Cursor cursor) {
    long id = cursor.getLong(cursor.getColumnIndexOrThrow("_ID"));
    SearchElementHolder searchHolder = (SearchElementHolder) holder;
    searchHolder.bind(cursor.getString(cursor.getColumnIndexOrThrow("title")),
                 cursor.getString(cursor.getColumnIndexOrThrow("description")),
                 cursor.getString(cursor.getColumnIndexOrThrow("rss_url")),
                 cursor.getString(cursor.getColumnIndexOrThrow("web_url")),
                 cursor.getLong(cursor.getColumnIndexOrThrow("period")),
                 id,
                 expandedElements.contains(id));
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    ViewGroup view = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(
        R.layout.search_list_element, parent, false);
    return new SearchElementHolder(view, listener, this);
  }

  void setExpanded(long id, boolean expanded, final int position) {
    if (!expandedElements.contains(id) && expanded) {
      expandedElements.add(id);
      new Handler(Looper.getMainLooper()).post(new Runnable() {
        @Override
        public void run() {
          notifyItemChanged(position);
        }
      });
    } else if (expandedElements.contains(id) && !expanded) {
      expandedElements.remove(id);
      new Handler(Looper.getMainLooper()).post(new Runnable() {
        @Override
        public void run() {
          notifyItemChanged(position);
        }
      });
    }
  }
}
