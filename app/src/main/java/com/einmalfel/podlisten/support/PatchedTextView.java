package com.einmalfel.podlisten.support;

// silencing glitch in TextView
// https://code.google.com/p/android/issues/detail?id=35466


import android.content.Context;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;


public class PatchedTextView extends AppCompatTextView {
  public PatchedTextView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public PatchedTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public PatchedTextView(Context context) {
    super(context);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    try {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    } catch (ArrayIndexOutOfBoundsException toSuppress) {
      setText(getText().toString());
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  @Override
  public void setGravity(int gravity) {
    try {
      super.setGravity(gravity);
    } catch (ArrayIndexOutOfBoundsException toSuppress) {
      setText(getText().toString());
      super.setGravity(gravity);
    }
  }

  @Override
  public void setText(CharSequence text, BufferType type) {
    try {
      super.setText(text, type);
    } catch (ArrayIndexOutOfBoundsException toSuppress) {
      setText(text.toString());
    }
  }
}
