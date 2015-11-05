package com.einmalfel.podlisten;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

public class PodcastCatalogue {
  public class CatalogueHelper extends SQLiteAssetHelper {
    public CatalogueHelper(Context context) {
      super(context, "PodcastCatalogue.sqlite", null, 1);
    }
  }

  static final String FTS_NAME = "catalogue_fts";
  private static final String CATALOGUE_NAME = "catalogue";
  private static final String TAG = "PCT";

  final SQLiteDatabase db;

  public PodcastCatalogue(Context context) {
    db = new CatalogueHelper(context).getWritableDatabase();
    Cursor cursor = db.rawQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name == ?",
                                new String[]{FTS_NAME});
    if (cursor.getCount() == 0) {
      Log.i(TAG, "Generating FTS DB");
      db.beginTransaction();
      try {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS " + FTS_NAME + " USING fts4(content='" +
                       CATALOGUE_NAME + "', _ID, title, description, rss_url, period, web_url)");
        db.execSQL("INSERT OR REPLACE INTO " + FTS_NAME + " (docid, _ID, title, description, " +
                       "rss_url, period, web_url) SELECT _ID, _ID, title, description, rss_url, " +
                       "period, web_url FROM " + CATALOGUE_NAME);
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    }
    cursor.close();
  }
}
