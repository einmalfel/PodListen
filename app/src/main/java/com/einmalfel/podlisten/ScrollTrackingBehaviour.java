package com.einmalfel.podlisten;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

public class ScrollTrackingBehaviour extends FloatingActionButton.Behavior {
  private static final String TAG = "STB";

  /** Need this constructor to reference ScrollTrackingBehaviour from xml */
  public ScrollTrackingBehaviour(Context context, AttributeSet attrs) {
    super();
  }

  @Override
  public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout,
                                     FloatingActionButton child, View directTargetChild,
                                     View target, int nestedScrollAxes) {
    if (target instanceof RecyclerView && nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL) {
      return true;
    } else {
      return super.onStartNestedScroll(
          coordinatorLayout, child, directTargetChild, target, nestedScrollAxes);
    }
  }

  @Override
  public void onNestedScroll(CoordinatorLayout coordinatorLayout, FloatingActionButton child,
                             View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
                             int dyUnconsumed) {
    super.onNestedScroll(
        coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);

    // hide FAB if scrolling down fast or if we are near the end of list
    if (dyConsumed < 0) {
      child.show();
    } else if (dyConsumed > 50 || approximatedVerticalScrollRatio((RecyclerView) target) > 0.75) {
      child.hide();
    }
  }

  /** @return approximated equivalent of scrollPosition divided by scrollRange */
  float approximatedVerticalScrollRatio(RecyclerView recyclerView) {
    LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
    int count = linearLayoutManager.getItemCount();
    int first = linearLayoutManager.findFirstCompletelyVisibleItemPosition();
    int last = linearLayoutManager.findLastCompletelyVisibleItemPosition();
    int visible = (last - first + 1);
    return (count == visible || count == 0) ? 0f : (float) first / (count - visible);
  }
}
