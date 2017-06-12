package com.einmalfel.podlisten;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.einmalfel.podlisten.support.PredictiveAnimatiedLayoutManager;

public class SearchActivity extends AppCompatActivity
    implements android.support.v7.widget.SearchView.OnQueryTextListener,
    SearchAdapter.SearchClickListener, CatalogueFragment.CatalogueListener {

  static final String RSS_URL_EXTRA = "rss_url";
  private static final String TAG = "SAC";

  private CatalogueFragment catalogue;
  private SearchAdapter adapter;
  private ProgressBar progressBar;
  private SearchView searchView;

  @Override
  public void onLoadProgress(int progress) {
    if (progress == 100) {
      setContentView(R.layout.common_list);
      RecyclerView rv = (RecyclerView) findViewById(R.id.recycler_view);
      rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(SearchActivity.this));
      rv.setItemAnimator(new DefaultItemAnimator());
      rv.setAdapter(adapter);
    } else {
      progressBar.setIndeterminate(false);
      progressBar.setProgress(progress);
    }
  }

  @Override
  public void onQueryComplete(Cursor cursor) {
    adapter.swapCursor(cursor);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.search_splash);
    progressBar = (ProgressBar) findViewById(R.id.splash_progress);
    PorterDuffColorFilter progressFilter = new PorterDuffColorFilter(
        ContextCompat.getColor(this, R.color.accent_secondary), PorterDuff.Mode.MULTIPLY);
    progressBar.getProgressDrawable().setColorFilter(progressFilter);
    progressBar.getIndeterminateDrawable().setColorFilter(progressFilter);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      searchView = new SearchView(this);
      searchView.setIconified(false);
      searchView.setOnQueryTextListener(this);
      searchView.requestFocus();
      actionBar.setCustomView(searchView, new ActionBar.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      actionBar.setDisplayShowCustomEnabled(true);
      actionBar.setDisplayShowTitleEnabled(false);
    } else {
      Log.wtf(TAG, "Should never get here: failed to get action bar of search activity");
    }

    adapter = new SearchAdapter(this);

    FragmentManager fm = getSupportFragmentManager();
    catalogue = (CatalogueFragment) fm.findFragmentByTag("catalogue");
    if (catalogue == null) {
      catalogue = new CatalogueFragment();
      catalogue.setRetainInstance(true);
      fm.beginTransaction().add(catalogue, "catalogue").commit();
    } else {
      onLoadProgress(catalogue.getLoadProgress());
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    catalogue.setListener(null);
  }

  @Override
  protected void onResume() {
    super.onResume();
    catalogue.setListener(this);
    catalogue.query(searchView.getQuery().toString().split(" "));
  }

  @Override
  public boolean onQueryTextSubmit(String query) {
    return false;
  }

  @Override
  public boolean onQueryTextChange(String newText) {
    catalogue.query(newText.split(" "));
    return true;
  }

  @Override
  public void onPodcastButtonTap(String rssUrl) {
    Intent intent = new Intent();
    intent.putExtra(RSS_URL_EXTRA, rssUrl);
    setResult(Activity.RESULT_OK, intent);
    finish();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.e(TAG, intent.toString());
  }
}
