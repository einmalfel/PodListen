package com.einmalfel.podlisten;

import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;

import com.einmalfel.podlisten.CursorRecyclerAdapter;

import java.util.HashSet;
import java.util.Set;

public abstract class BaseCursorRecyclerAdapter<T extends RecyclerView.ViewHolder> extends CursorRecyclerAdapter<T> {
    protected final Set<Long> expandedElements = new HashSet<>(10);

    public BaseCursorRecyclerAdapter(Cursor cursor) {
        super(cursor);
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
