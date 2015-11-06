package com.einmalfel.podlisten;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

public class CatalogueFragment extends Fragment {
  interface CatalogueListener {
    void onLoadProgress(int progress);

    void onQueryComplete(Cursor cursor);
  }

  private class CatalogueHelper extends SQLiteAssetHelper {
    public CatalogueHelper(Context context) {
      super(context, "PodcastCatalogue.sqlite", null, 2);
      setForcedUpgrade();
    }
  }

  private class CatalogWorker extends HandlerThread {
    private Handler handler;

    public CatalogWorker() {
      super("CatalogWorker");
    }

    @Override
    public synchronized void start() {
      super.start();
      handler = new Handler(getLooper());
    }

    public synchronized Handler getHandler() {
      if (!isAlive()) {
        start();
      }
      return handler;
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

  private CatalogWorker worker;
  private CatalogueListener listener;
  private SQLiteDatabase db;
  private Context appContext;
  private int loadProgress;

  public CatalogueFragment() {
    super();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (appContext == null) {
      appContext = context.getApplicationContext();
      worker = new CatalogWorker();
      worker.start();
      worker.getHandler().post(new Runnable() {
        @Override
        public void run() {
          initDb();
        }
      });
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.i(TAG, "Stopping handler thread");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      worker.quitSafely();
    } else {
      worker.quit();
    }
  }

  @UiThread
  public void setListener(CatalogueListener listener) {
    this.listener = listener;
  }

  public int getLoadProgress() {
    return loadProgress;
  }

  public void query(final String[] terms) {
    worker.getHandler().post(new Runnable() {
      @Override
      public void run() {
        String query;
        if (terms.length == 0 || (terms.length == 1 && terms[0].equals(""))) {
          query = "SELECT * FROM " + CAT_NAME + " WHERE title != ?";
        } else {
          query = "SELECT " + CAT_NAME + ".* FROM " + FTS_NAME + " JOIN " + CAT_NAME + " WHERE " +
              FTS_NAME + "." + K_DOCID + " == " + CAT_NAME + "." + K_ID + " AND " + FTS_NAME +
              " match ?";

          // optimization: don't sort results if number of terms is big (slow WHERE processing) or
          // all terms are short (i.e. a lots of results)
          int longest = 0;
          for (String term : terms) {
            if (term.length() > longest) {
              longest = term.length();
            }
          }
          if (longest - terms.length > 5) {
            query += " ORDER BY length(offsets(" + FTS_NAME + ")) DESC";
          }
        }

        final Cursor c = db.rawQuery(query, new String[]{TextUtils.join("* ", terms) + "*"});

        new Handler(appContext.getMainLooper()).post(new Runnable() {
          @Override
          public void run() {
            if (listener != null) {
              listener.onQueryComplete(c);
            }
          }
        });
      }
    });
  }

  private void initDb() {
    db = new CatalogueHelper(appContext).getWritableDatabase();
    Cursor cursor = db.rawQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name == ?",
                                new String[]{FTS_NAME});
    if (cursor.getCount() == 0) {
      reportProgress(0);
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
        int recordsPerPercent = catalogue.getCount() / 90;
        while (catalogue.moveToNext()) {
          if (catalogue.getPosition() % recordsPerPercent == 0) {
            reportProgress(catalogue.getPosition() / recordsPerPercent);
          }
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
        reportProgress(91);
        catalogue.close();
        reportProgress(92);
        db.setTransactionSuccessful();
        reportProgress(98);
      } finally {
        db.endTransaction();
        reportProgress(99);
      }
    }
    cursor.close();
    reportProgress(100);
  }

  private void reportProgress(final int progress) {
    this.loadProgress = progress;
    Log.e(TAG, "Progress " + progress);
    new Handler(appContext.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        if (listener != null) {
          listener.onLoadProgress(progress);
        }
      }
    });
  }
}


