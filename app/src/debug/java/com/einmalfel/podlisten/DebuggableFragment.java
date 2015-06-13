package com.einmalfel.podlisten;

import android.support.v4.app.Fragment;

public class DebuggableFragment extends Fragment {
  @Override
  public void onDestroy() {
    super.onDestroy();
    PodListenApp.getInstance().refWatcher.watch(this);
  }
}
