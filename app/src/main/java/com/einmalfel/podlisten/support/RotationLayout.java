package com.einmalfel.podlisten.support;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.einmalfel.podlisten.R;

import java.util.ArrayList;

/**
 * This layout correctly handles rotation of nested views.
 * Rotation is specified via layout_rotation attribute (available values are 0, 90, 180, 270),
 * which overrides view's rotation attribute.
 * Padding is supported, while layout_margins aren't.
 * Partially based on FrameLayout code.
 * TODO: extract into separate library
 */
public class RotationLayout extends ViewGroup {
  private final ArrayList<View> mMatchParentChildren = new ArrayList<View>(1);

  public RotationLayout(Context context) {
    super(context);
  }

  public RotationLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public RotationLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    boolean measureMatchParentChildren =
        MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY ||
            MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;
    mMatchParentChildren.clear();
    int hPadding = getPaddingLeft() + getPaddingRight();
    int vPadding = getPaddingTop() + getPaddingBottom();
    int maxHeight = 0;
    int maxWidth = 0;
    int cState = 0;

    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.layout_rotation == 90 || lp.layout_rotation == 270) {
          child.measure(
              getChildMeasureSpec(heightMeasureSpec, vPadding, lp.width),
              getChildMeasureSpec(widthMeasureSpec, hPadding, lp.height));
          maxHeight = Math.max(maxHeight, child.getMeasuredWidth());
          maxWidth = Math.max(maxWidth, child.getMeasuredHeight());
        } else {
          child.measure(
              getChildMeasureSpec(widthMeasureSpec, hPadding, lp.width),
              getChildMeasureSpec(heightMeasureSpec, vPadding, lp.height));
          maxWidth = Math.max(maxWidth, child.getMeasuredWidth());
          maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
        }
        cState = combineMeasuredStates(cState, child.getMeasuredState());
        if (measureMatchParentChildren) {
          if (lp.width == LayoutParams.MATCH_PARENT || lp.height == LayoutParams.MATCH_PARENT) {
            mMatchParentChildren.add(child);
          }
        }

        child.setPivotY(0);
        child.setPivotX(0);
        child.setRotation(lp.layout_rotation);
        if (lp.layout_rotation == 90) {
          child.setX(getPaddingLeft() + child.getMeasuredHeight());
        } else if (lp.layout_rotation == 180) {
          child.setX(getPaddingLeft() + child.getMeasuredWidth());
          child.setY(getPaddingTop() + child.getMeasuredHeight());
        } else if (lp.layout_rotation == 270) {
          child.setY(getPaddingTop() + child.getMeasuredWidth());
        }
      }
    }

    // Account for padding too
    maxWidth += hPadding;
    maxHeight += vPadding;

    // Check against our minimum height and width
    maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
    maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

    setMeasuredDimension(
        resolveSizeAndState(maxWidth, widthMeasureSpec, cState),
        resolveSizeAndState(maxHeight, heightMeasureSpec, cState << MEASURED_HEIGHT_STATE_SHIFT));

    // in case our layout didn't had exact size initially (wrap_content) and some of children don't
    // have it neither (match_parent), we need to remeasure those children now, when the layout
    // has already obtained its dimensions
    if (mMatchParentChildren.size() > 1) {
      for (int i = 0; i < mMatchParentChildren.size(); i++) {
        View child = mMatchParentChildren.get(i);
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        int childW;
        int childH;
        if (lp.width == LayoutParams.MATCH_PARENT) {
          childW = MeasureSpec.makeMeasureSpec(getMeasuredWidth() - hPadding, MeasureSpec.EXACTLY);
        } else {
          childW = getChildMeasureSpec(widthMeasureSpec, hPadding, lp.width);
        }
        if (lp.height == LayoutParams.MATCH_PARENT) {
          childH = MeasureSpec.makeMeasureSpec(getMeasuredHeight() - vPadding, MeasureSpec.EXACTLY);
        } else {
          childH = getChildMeasureSpec(heightMeasureSpec, vPadding, lp.height);
        }
        child.measure(childW, childH);
      }
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        child.layout(
            getPaddingLeft(),
            getPaddingTop(),
            getPaddingLeft() + child.getMeasuredWidth(),
            getPaddingTop() + child.getMeasuredHeight());
      }
    }
  }

  public static class LayoutParams extends ViewGroup.LayoutParams {
    public final int layout_rotation;

    public LayoutParams(int width, int height, int rotation) {
      this(new ViewGroup.LayoutParams(width, height), rotation);
    }

    public LayoutParams(ViewGroup.LayoutParams source, int rotation) {
      super(source);
      if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
        // layout inflater may suppress the exception, so scream in log
        String message =
            "Invalid layout_rotation value (" + rotation + "). Available values: 0, 90, 180, 270.";
        Log.e("RotationLayout", message);
        throw new IllegalArgumentException(message);
      }
      layout_rotation = rotation;
    }
  }

  protected LayoutParams generateDefaultLayoutParams() {
    return new LayoutParams(super.generateDefaultLayoutParams(), 0);
  }

  public LayoutParams generateLayoutParams(AttributeSet attrs) {
    TypedArray array = getContext().obtainStyledAttributes(
        attrs, R.styleable.RotationLayout_LayoutParams);
    int rotation = array.getInt(R.styleable.RotationLayout_LayoutParams_layout_rotation, 0);
    array.recycle();
    return new LayoutParams(new ViewGroup.LayoutParams(getContext(), attrs), rotation);
  }

  protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
    return p instanceof LayoutParams;
  }
}
