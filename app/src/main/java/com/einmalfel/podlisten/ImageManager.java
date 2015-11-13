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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileLock;

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
  public Bitmap getImage(long id) {
    Bitmap result = memoryCache.get(id);
    if (result == null) {
      result = loadFromDisk(id);
      if (result != null) {
        memoryCache.put(id, result);
      }
    }
    return result;
  }

  public void deleteImage(long id) {
    File file = getImageFile(id, true);
    if (file != null && file.exists()) {
      if (!file.delete()) {
        Log.e(TAG, "Deletion of " + file.getAbsolutePath() + " failed");
      }
    }
  }

  // based on snippet from http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
  private static int calculateInSampleSize(BitmapFactory.Options options, int reqHeight) {
    int inSampleSize = 1;

    if (options.outHeight > reqHeight) {
      final int halfHeight = options.outHeight / 2;
      // Calculate the largest inSampleSize value that is a power of 2 and keeps height larger than
      // requested
      while ((halfHeight / inSampleSize) > reqHeight) {
        inSampleSize *= 2;
      }
    }

    return inSampleSize;
  }

  public void download(long id, URL url) throws IOException {

    HttpURLConnection urlConnection = null;
    Bitmap bitmap = null;
    try {
      urlConnection = (HttpURLConnection) url.openConnection();
      urlConnection.connect();
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      bitmap = BitmapFactory.decodeStream(urlConnection.getInputStream(), null, options);
      urlConnection.disconnect();
      urlConnection = (HttpURLConnection) url.openConnection();
      options.inJustDecodeBounds = false;
      options.inSampleSize = calculateInSampleSize(options, heightPx);
      Log.d(TAG, "Downloading " + url + ". Sampling factor: " + options.inSampleSize);
      bitmap = BitmapFactory.decodeStream(urlConnection.getInputStream(), null, options);
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

    File file = getImageFile(id, false);
    if (file == null) {
      Log.e(TAG, "Image " + id + " download failed. No writable storage");
      return;
    }
    FileOutputStream stream = null;
    FileLock lock = null;
    try {
      stream = new FileOutputStream(file);
      lock = stream.getChannel().lock();
      scaled.compress(Bitmap.CompressFormat.PNG, 100, stream);
      Log.d(TAG, url.toString() + " written to " + file.getAbsolutePath());
    } catch (IOException exception) {
      Log.e(TAG, "Failed to read image " + id + "from flash", exception);
    } finally {
      bitmap.recycle();
      scaled.recycle();
      if (lock != null) {
        try {
          lock.release();
        } catch (IOException exception) {
          Log.wtf(TAG, "Failed to close stream", exception);
        }
      }
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException exception) {
          Log.wtf(TAG, "Failed to close stream", exception);
        }
      }
    }
  }

  public boolean isDownloaded(long id) {
    File file = getImageFile(id, false);
    return file != null && file.exists();
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
    if (!isDownloaded(id)) {
      return null;
    }
    Log.d(TAG, "Loading " + id + " from sdcard. Cache size before " + getCacheSize());
    File file = getImageFile(id, false);
    FileInputStream stream = null;
    FileLock lock = null;
    try {
      stream = new FileInputStream(file);
      lock = stream.getChannel().lock(0, Long.MAX_VALUE, true);
      return BitmapFactory.decodeStream(stream);
    } catch (FileNotFoundException ignored) {
      return null; // it's normal if there is no file
    } catch (IOException exception) {
      Log.e(TAG, "Failed to read image " + id + "from flash", exception);
      return null;
    } finally {
      if (lock != null) {
        try {
          lock.release();
        } catch (IOException exception) {
          Log.wtf(TAG, "Failed to close stream", exception);
        }
      }
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException exception) {
          Log.wtf(TAG, "Failed to close stream", exception);
        }
      }
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
