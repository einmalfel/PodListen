package com.einmalfel.podlisten;

import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashSet;
import java.util.Set;

public class EpisodeListAdapter extends CursorRecyclerAdapter<EpisodeViewHolder> {
  public interface EpisodeClickListener {
    /**
     * @return true if event was consumed
     */
    boolean onLongTap(long id);

    void onButtonTap(long id);
  }

  private static final String TAG = "ELA";
  private final EpisodeClickListener listener;
  private final Set<Long> expandedElements = new HashSet<Long>(10);
  private long currentPlayingId = 0;
  private PlayerService.State currentState = PlayerService.State.STOPPED;

  public EpisodeListAdapter(Cursor cursor, EpisodeClickListener listener) {
    super(cursor);
    this.listener = listener;
    setHasStableIds(true);
  }

  void setCurrentIdState(long id, PlayerService.State state) {
    if (id != currentPlayingId || currentState != state) {
      currentPlayingId = id;
      currentState = state;
      new Handler(Looper.getMainLooper()).post(new Runnable() {
        @Override
        public void run() {
          notifyDataSetChanged();
        }
      });
    }
  }

  @Override
  public void onBindViewHolderCursor(EpisodeViewHolder holder, Cursor cursor) {
    if (holder.getExpanded()) {
      expandedElements.add(holder.getId());
    } else {
      expandedElements.remove(holder.getId());
    }
    long id = cursor.getLong(cursor.getColumnIndex(Provider.K_ID));
    holder.bindEpisode(
        cursor.getString(cursor.getColumnIndex(Provider.K_ENAME)),
        cursor.getString(cursor.getColumnIndex(Provider.K_EDESCR)),
        id,
        cursor.getLong(cursor.getColumnIndex(Provider.K_EPID)),
        cursor.getLong(cursor.getColumnIndex(Provider.K_ESIZE)),
        cursor.getInt(cursor.getColumnIndex(Provider.K_ESTATE)),
        cursor.getString(cursor.getColumnIndex(Provider.K_PNAME)),
        cursor.getLong(cursor.getColumnIndex(Provider.K_EPLAYED)),
        cursor.getLong(cursor.getColumnIndex(Provider.K_ELENGTH)),
        cursor.getLong(cursor.getColumnIndex(Provider.K_EDATE)),
        cursor.getLong(cursor.getColumnIndex(Provider.K_EDFIN)),
        currentPlayingId == id ? currentState : PlayerService.State.STOPPED);
    holder.setExpanded(expandedElements.contains(id));
  }

  @Override
  public EpisodeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.episode_list_element, parent, false);
    return new EpisodeViewHolder(v, listener);
  }
}
