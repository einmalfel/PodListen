package com.einmalfel.podlisten;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.LruCache;
import android.view.WindowManager;

import com.einmalfel.podlisten.support.UnitConverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * This class is in charge of downloading, storing and memory-caching images
 */
public class ImageManager {
  private static final String TAG = "IMG";
  private static final int HEIGHT_SP = 70;
  private static final int PAGES_TO_CACHE = 10;
  private final int heightPx;
  private static ImageManager instance;

  private final LruCache<Long, Bitmap> memoryCache;
  private final Context context;

  @NonNull
  public static ImageManager getInstance() {
    if (instance == null) {
      synchronized (ImageManager.class) {
        if (instance == null) {
          instance = new ImageManager();
        }
      }
    }
    return instance;
  }

  @Nullable
  synchronized public Bitmap getImage(long id) {
    Bitmap result = memoryCache.get(id);
    if (result == null) {
      result = loadFromDisk(id);
      if (result != null) {
        memoryCache.put(id, result);
      }
    }
    return result;
  }

  synchronized public void deleteImage(long id) {
    File file = getImageFile(id, true);
    if (file != null && file.exists()) {
      if (!file.delete()) {
        Log.e(TAG, "Deletion of " + file.getAbsolutePath() + " failed");
      }
    }
  }

  public void download(long id, URL url) throws IOException {
    Log.d(TAG, "Downloading " + url);
    HttpURLConnection urlConnection = null;
    Bitmap bitmap = null;
    try {
      urlConnection = (HttpURLConnection) url.openConnection();
      urlConnection.connect();
      bitmap = BitmapFactory.decodeStream(urlConnection.getInputStream());
      if (bitmap == null) {
        throw new IOException("Failed to load image from " + url);
      }
    } finally {
      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
    Bitmap scaled = Bitmap.createScaledBitmap(
        bitmap, bitmap.getWidth() * heightPx / bitmap.getHeight(), heightPx, true);
    // synchronize to be sure that isDownloaded won't return true while image file is being written
    synchronized (this) {
      File file = getImageFile(id, true);
      if (file != null) {
        FileOutputStream stream = new FileOutputStream(file);
        scaled.compress(Bitmap.CompressFormat.PNG, 100, stream);
        stream.close();
        Log.d(TAG, url.toString() + " written to " + file.getAbsolutePath());
      }
    }
  }

  public synchronized boolean isDownloaded(long id) {
    synchronized (Preferences.getInstance()) {
      File file = getImageFile(id, false);
      return file != null && file.exists();
    }
  }

  @Nullable
  private File getImageFile(long id, boolean write) {
    Storage s = Preferences.getInstance().getStorage();
    if (s == null) {
      return null;
    }
    boolean isAvailable = write ? s.isAvailableRW() : s.isAvailableRead();
    return isAvailable ? new File(s.getImagesDir(), id + ".png") : null;
  }


  @Nullable
  private Bitmap loadFromDisk(long id) {
    synchronized (Preferences.getInstance()) {
      if (!isDownloaded(id)) {
        return null;
      }
      Log.d(TAG, "Loading " + id + " from sdcard. Cache size before " + getCacheSize());
      File file = getImageFile(id, false);
      return file == null ? null : BitmapFactory.decodeFile(file.getAbsolutePath());
    }
  }

  private int getCacheSize() {
    int size = 0;
    for (Bitmap b : memoryCache.snapshot().values()) {
      size += b.getByteCount(); // approximate value. TODO use getAllocatedByteCount on 4.4+
    }
    return size;
  }

  private ImageManager() {
    context = PodListenApp.getContext();
    heightPx = UnitConverter.getInstance().spToPx(HEIGHT_SP);
    Point displaySize = new Point();
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    wm.getDefaultDisplay().getSize(displaySize);
    int imagesPerPage = displaySize.y / heightPx;
    memoryCache = new LruCache<>(PAGES_TO_CACHE * imagesPerPage);
  }
}
