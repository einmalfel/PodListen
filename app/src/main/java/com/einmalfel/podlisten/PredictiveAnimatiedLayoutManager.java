package com.einmalfel.podlisten;


import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;


public class PredictiveAnimatiedLayoutManager extends LinearLayoutManager {
  public PredictiveAnimatiedLayoutManager(Context context) {
    super(context);
  }

  @Override
  public boolean supportsPredictiveItemAnimations() {
    return true;
  }
}
