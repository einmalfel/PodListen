package com.einmalfel.podlisten;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PreferencesActivity extends AppCompatActivity {
  private static final String TAG = "PAC";
  private final Preferences preferences = Preferences.getInstance();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    android.support.v4.app.FragmentManager fM = getSupportFragmentManager();
    if (fM.findFragmentById(android.R.id.content) == null) {
      fM.beginTransaction()
        .add(android.R.id.content, new PreferencesFragment())
        .commit();
    }

    setTitle(getString(R.string.preferences_title));
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    } else {
      Log.wtf(TAG, "Should never get here: failed to get action bar of preference activity");
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    switch (intent.getAction()) {
      case "com.einmalfel.podlisten.SEND_BUG_REPORT":
        PodListenApp.sendLogs();
        break;
      case "com.einmalfel.podlisten.OPML_EXPORT":
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_UNKNOWN.equals(state)) {
          final File dir;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
          } else {
            dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS);
          }
          if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Directory for OPML export doesn't exist " + dir);
          }
          final File target = new File(dir, getString(R.string.opml_export_file_name));
          if (exportToOPML(target)) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(getString(R.string.opml_dialog_title))
             .setMessage(String.format(getString(R.string.opml_dialog_message), target))
             .setNegativeButton(R.string.opml_dialog_done, null);

            final Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("file/*");
            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(target));
            if (sendIntent.resolveActivity(getPackageManager()) != null) {
              b.setPositiveButton(R.string.opml_dialog_send, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  startActivity(sendIntent);
                }
              });
            }

            if (showDirectory(dir, true)) {
              b.setNeutralButton(R.string.opml_dialog_FMapp, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  showDirectory(dir, false);
                }
              });
            }

            b.show();
          } else {
            Snackbar.make(findViewById(android.R.id.content),
                          R.string.preferences_opml_export_failed,
                          Snackbar.LENGTH_LONG).show();
          }
        }
        break;
      default:
        Log.e(TAG, "Unexpected intent received: " + intent);
        break;
    }
  }

  private static final SimpleDateFormat RFC822DATEFORMAT
      = new SimpleDateFormat("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", Locale.US);

  private boolean exportToOPML(File file) {
    XmlSerializer serializer = Xml.newSerializer();
    try {
      if (!file.exists() && !file.createNewFile()) {
        Log.e(TAG, "failed to create file for OPML export " + file);
        return false;
      }
      serializer.setOutput(new FileWriter(file));
      serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
      serializer.startDocument("UTF-8", false);
      serializer.startTag(XmlPullParser.NO_NAMESPACE, "opml");
      serializer.attribute(XmlPullParser.NO_NAMESPACE, "version", "1.0");

      serializer.startTag(XmlPullParser.NO_NAMESPACE, "head");
      serializer.startTag(XmlPullParser.NO_NAMESPACE, "title");
      serializer.text("Podlisten subscriptions");
      serializer.endTag(XmlPullParser.NO_NAMESPACE, "title");
      serializer.startTag(XmlPullParser.NO_NAMESPACE, "dateCreated");
      serializer.text(RFC822DATEFORMAT.format(new Date()));
      serializer.endTag(XmlPullParser.NO_NAMESPACE, "dateCreated");
      serializer.endTag(XmlPullParser.NO_NAMESPACE, "head");

      serializer.startTag(XmlPullParser.NO_NAMESPACE, "body");

      Cursor cursor = getContentResolver().query(
          Provider.podcastUri, new String[]{Provider.K_PNAME, Provider.K_PFURL}, null, null, null);
      if (cursor == null) {
        return false;
      }
      int urlInd = cursor.getColumnIndexOrThrow(Provider.K_PFURL);
      int titleInd = cursor.getColumnIndexOrThrow(Provider.K_PNAME);
      while (cursor.moveToNext()) {
        String url = cursor.getString(urlInd);
        String title = cursor.getString(titleInd);
        if (url == null || url.isEmpty()) {
          Log.w(TAG, "OPML export: Skipping " + title + " url " + url);
          continue;
        }
        serializer.startTag(XmlPullParser.NO_NAMESPACE, "outline");
        serializer.attribute(XmlPullParser.NO_NAMESPACE, "type", "rss");
        serializer.attribute(XmlPullParser.NO_NAMESPACE, "text", title == null ? "Unknown" : title);
        serializer.attribute(XmlPullParser.NO_NAMESPACE, "xmlUrl", url);
        serializer.endTag(XmlPullParser.NO_NAMESPACE, "outline");
      }
      cursor.close();

      serializer.endDocument();
      return true;
    } catch (IOException ioException) {
      Log.w(TAG, "OPML export failed", ioException);
      return false;
    }
  }

  static private final String[] directoryMimeTypes = new String[]{
      "application/x-directory",
      "resource/folder",
      "x-directory/normal",
      "inode/directory",
      "application/folder",
      "vnd.android.cursor.item/file"
  };

  private boolean showDirectory(File dir, boolean test) {
    Intent showFolderIntent = new Intent(Intent.ACTION_VIEW);
    for (String mimeType : directoryMimeTypes) {
      showFolderIntent.setDataAndType(Uri.fromFile(dir), mimeType);
      if (test) {
        if (showFolderIntent.resolveActivity(getPackageManager()) != null) {
          return true;
        }
      } else {
        try {
          startActivity(showFolderIntent);
          return true;
        } catch (ActivityNotFoundException ignored) {}
      }
    }
    return false;
  }
}
