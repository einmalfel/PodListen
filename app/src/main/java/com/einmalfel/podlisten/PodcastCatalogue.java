package com.einmalfel.podlisten;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

public class PodcastCatalogue {
  public class CatalogueHelper extends SQLiteAssetHelper {
    public CatalogueHelper(Context context) {
      super(context, "PodcastCatalogue.sqlite", null, 1);
    }
  }

  private static final String TAG = "PCT";

  final SQLiteDatabase db;

  public PodcastCatalogue(Context context) {
    db = new CatalogueHelper(context).getWritableDatabase();
    db.execSQL(
        "CREATE VIRTUAL TABLE IF NOT EXISTS catalogue_fts USING fts4(content='catalogue', " +
            "_ID, title, description, rss_url, period, web_url)");
    db.execSQL(
        "INSERT OR REPLACE INTO catalogue_fts (docid, _ID, title, description, rss_url, period, web_url) " +
            "SELECT _ID, _ID, title, description, rss_url, period, web_url FROM catalogue");
  }
}
