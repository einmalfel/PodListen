package com.einmalfel.podlisten;

import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

class FeedHistoryAdapter extends BaseCursorRecyclerAdapter {
  public interface HistoryEpisodeListener {
    void onEpisodeButtonTap(long id, int state);
  }

  static final String[] COLUMNS_NEEDED = new String[]{
      Provider.K_ENAME, Provider.K_EURL, Provider.K_EDATE, Provider.K_EDESCR, Provider.K_ESDESCR,
      Provider.K_ID, Provider.K_ESTATE, Provider.K_EPLAYED};

  private final HistoryEpisodeListener listener;

  public FeedHistoryAdapter(HistoryEpisodeListener listener) {
    super(null);
    this.listener = listener;
  }

  @Override
  public void onBindViewHolderCursor(RecyclerView.ViewHolder holder, Cursor cursor) {
    long id = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_ID));
    HistoryElementHolder sHolder = (HistoryElementHolder) holder;
    sHolder.bind(cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_ENAME)),
                 cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_EDESCR)),
                 cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_ESDESCR)),
                 cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_EURL)),
                 cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EDATE)),
                 cursor.getInt(cursor.getColumnIndexOrThrow(Provider.K_ESTATE)),
                 cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EPLAYED)),
                 id,
                 expandedElements.contains(id));
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    ViewGroup v = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(
        R.layout.history_list_element, parent, false);
    return new HistoryElementHolder(v, listener, this);
  }
}
