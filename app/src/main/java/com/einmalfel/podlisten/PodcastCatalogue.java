package com.einmalfel.podlisten;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

public class PodcastCatalogue {
  public class CatalogueHelper extends SQLiteAssetHelper {
    public CatalogueHelper(Context context) {
      super(context, "PodcastCatalogue.sqlite", null, 1);
      setForcedUpgrade();
    }
  }

  static final String FTS_NAME = "catalogue_fts";
  static final String K_ID = "_ID";
  static final String K_DOCID = "docid";
  static final String K_RSS = "rss_url";
  static final String K_WEB = "web_url";
  static final String K_TITLE = "title";
  static final String K_DESCRIPTION = "description";
  static final String K_PERIOD = "period";
  static final String CAT_NAME = "catalogue";
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
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS " + FTS_NAME +
                       " USING fts4(title, description, web_url)");
        Cursor catalogue = db.query(
            CAT_NAME,
            new String[]{K_ID, K_TITLE, K_DESCRIPTION, K_WEB},
            null, null, null, null, null);
        ContentValues cv = new ContentValues(4);
        int iDiD = catalogue.getColumnIndexOrThrow(K_ID);
        int titleId = catalogue.getColumnIndexOrThrow(K_TITLE);
        int descriptionId = catalogue.getColumnIndexOrThrow(K_DESCRIPTION);
        int webId = catalogue.getColumnIndexOrThrow(K_WEB);
        while (catalogue.moveToNext()) {
          cv.put(K_DOCID, catalogue.getLong(iDiD));
          cv.put(K_TITLE, catalogue.getString(titleId).toLowerCase());
          String description = catalogue.getString(descriptionId);
          if (description != null) {
            cv.put(K_DESCRIPTION, description.toLowerCase());
          }
          String webUrl = catalogue.getString(webId);
          if (webUrl != null) {
            cv.put(K_WEB, webUrl.toLowerCase());
          }
          db.insert(FTS_NAME, null, cv);
        }
        catalogue.close();
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    }
    cursor.close();
  }
}
