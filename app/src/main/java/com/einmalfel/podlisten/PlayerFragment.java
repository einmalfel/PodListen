package com.einmalfel.podlisten;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


public class PlayerFragment extends Fragment {
  private static final MainActivity.Pages activityPage = MainActivity.Pages.PLAYER;

  public PlayerFragment() {
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_player, container, false);
  }


}
