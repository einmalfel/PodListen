package com.einmalfel.podlisten;

import android.support.v7.app.AppCompatActivity;

public class ScanActivity extends AppCompatActivity
    implements android.support.v7.widget.SearchView.OnQueryTextListener,
    SearchAdapter.SearchClickListener {

  @Override
  public boolean onQueryTextSubmit(String query) {
    return false;
  }

  @Override
  public boolean onQueryTextChange(String newText) {
    return false;
  }

  @Override
  public void onPodcastButtonTap(String rssUrl) {

  }
}
