package com.einmalfel.podlisten;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;

import com.einmalfel.podlisten.support.PredictiveAnimatiedLayoutManager;

public class SearchActivity extends AppCompatActivity
    implements android.support.v7.widget.SearchView.OnQueryTextListener,
    SearchAdapter.SearchClickListener {

  private class InitDbAsync extends AsyncTask<Context, Void, Void> {
    PodcastCatalogue catalogue;

    @Override
    protected Void doInBackground(Context... params) {
      catalogue = new PodcastCatalogue(params[0]);
      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      SearchActivity.this.catalogue = this.catalogue;
      SearchActivity.this.setContentView(R.layout.common_list);
      RecyclerView rv = (RecyclerView) findViewById(R.id.recycler_view);
      rv.setLayoutManager(new PredictiveAnimatiedLayoutManager(SearchActivity.this));
      rv.setItemAnimator(new DefaultItemAnimator());
      rv.setAdapter(adapter);
      if (pendingQuery != null) {
        onQueryTextChange(pendingQuery);
      }
    }
  }

  static final String RSS_URL_EXTRA = "rss_url";
  private static final String TAG = "SAC";

  private PodcastCatalogue catalogue;
  private SearchAdapter adapter;
  private String pendingQuery;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.search_splash);
    //    setContentView(R.layout.common_list);
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setBackgroundDrawable(
          new ColorDrawable(ContextCompat.getColor(this, R.color.background_primary)));
      SearchView searchView = new SearchView(this);
      searchView.setIconified(false);
      searchView.setOnQueryTextListener(this);
      searchView.requestFocus();
      actionBar.setCustomView(searchView, new ActionBar.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      actionBar.setDisplayShowCustomEnabled(true);
      actionBar.setDisplayShowTitleEnabled(false);
    } else {
      Log.wtf(TAG, "Should never get here: failed to get action bar of preference activity");
    }

    new InitDbAsync().execute(this);
    adapter = new SearchAdapter(this);
  }

  @Override
  public boolean onQueryTextSubmit(String query) {
    return false;
  }

  @Override
  public boolean onQueryTextChange(String newText) {
    if (catalogue == null) {
      pendingQuery = newText;
      return true;
    }

    int longest = 0;
    String[] terms = newText.split(" ");
    for (String term : terms) {
      if (term.length() > longest) {
        longest = term.length();
      }
    }

    // optimization: don't sort results if number of terms is big (slow WHERE processing) or
    // all terms are short (i.e. a lots of results)
    String query = "SELECT " + PodcastCatalogue.CAT_NAME + ".* FROM " + PodcastCatalogue.FTS_NAME +
        " JOIN " + PodcastCatalogue.CAT_NAME + " WHERE " + PodcastCatalogue.FTS_NAME + "." +
        PodcastCatalogue.K_DOCID + " == " + PodcastCatalogue.CAT_NAME + "." +
        PodcastCatalogue.K_ID + " AND " + PodcastCatalogue.FTS_NAME + " match ?";
    if (longest - terms.length > 5) {
      query += " ORDER BY length(offsets(catalogue_fts)) DESC";
    }

    Cursor c = catalogue.db.rawQuery(query, new String[]{TextUtils.join("* ", terms) + "*"});
    adapter.swapCursor(c);

    return true;
  }

  @Override
  public void onPodcastButtonTap(String rss_url) {
    Intent intent = new Intent();
    intent.putExtra(RSS_URL_EXTRA, rss_url);
    setResult(Activity.RESULT_OK, intent);
    finish();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.e(TAG, intent.toString());
  }
}
