package com.einmalfel.podlisten;

import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class EpisodeListAdapter extends BaseCursorRecyclerAdapter<EpisodeViewHolder> {
  public interface ItemClickListener {
    /**
     * @return true if event was consumed
     */
    boolean onLongTap(long id, String title, int state, String audioUrl, int downloaded);

    void onButtonTap(long id, String title, int state, String audioUrl, int downloaded);
  }

  private static final String TAG = "ELA";
  static final String[] REQUIRED_DB_COLUMNS = new String[]{
      Provider.K_EID, Provider.K_ENAME, Provider.K_EDESCR, Provider.K_EDFIN, Provider.K_ESIZE,
      Provider.K_ESTATE, Provider.K_PNAME, Provider.K_EPLAYED, Provider.K_ELENGTH, Provider.K_EDATE,
      Provider.K_EPID, Provider.K_ESDESCR, Provider.K_EERROR, Provider.K_EDID, Provider.K_EURL,
      Provider.K_EAURL};
  private final ItemClickListener listener;
  private long currentPlayingId = 0;
  private PlayerService.State currentState = PlayerService.State.STOPPED;

  public EpisodeListAdapter(Cursor cursor, ItemClickListener listener) {
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
    long id = cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_ID));
    holder.bindEpisode(
        cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_ENAME)),
        cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_EDESCR)),
        id,
        cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EPID)),
        cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_ESIZE)),
        cursor.getInt(cursor.getColumnIndexOrThrow(Provider.K_ESTATE)),
        cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_PNAME)),
        cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EPLAYED)),
        cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_ELENGTH)),
        cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EDATE)),
        cursor.getInt(cursor.getColumnIndexOrThrow(Provider.K_EDFIN)),
        cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_ESDESCR)),
        cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_EERROR)),
        currentPlayingId == id ? currentState : PlayerService.State.STOPPED,
        cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_EURL)),
        cursor.getLong(cursor.getColumnIndexOrThrow(Provider.K_EDID)),
        cursor.getString(cursor.getColumnIndexOrThrow(Provider.K_EAURL)),
        expandedElements.contains(id));
  }

  @Override
  public EpisodeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.episode_list_element, parent, false);
    return new EpisodeViewHolder(view, listener, this);
  }
}
