package com.einmalfel.podlisten;

import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class Storage {
  private static final String TAG = "STR";
  private static final String UNKNOWN_STATE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
      Environment.MEDIA_UNKNOWN : "unknown";
  private final File appFilesDir; // /*/Android/data/com.einmalfel.podlisten/files

  @NonNull
  public static List<Storage> getAvailableStorages() {
    List<Storage> result = new LinkedList<>();
    for (File dir : ContextCompat.getExternalFilesDirs(PodListenApp.getContext(), null)) {
      try {
        result.add(new Storage(dir));
      } catch (IOException e) {
        Log.e(TAG, "File path couldn't be converted to canonical form:" + dir.getAbsolutePath(), e);
      }
    }
    return result;
  }

  @NonNull
  public static Storage getPrimaryStorage() {
    try {
      return new Storage(Environment.getExternalStorageDirectory());
    } catch (IOException exception) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        throw new AssertionError("Cant convert primary storage to canonical form", exception);
      } else {
        throw new AssertionError("Cant convert primary storage to canonical form");
      }
    }
  }

  /**
   * @param path directory where audio and image data will be stored
   * @throws IOException if given path couldn't be converted to canonical form
   */
  Storage(@NonNull File path) throws IOException {
    this.appFilesDir = path.getCanonicalFile();
  }

  @NonNull
  public File getPodcastDir() {
    return new File(appFilesDir, Environment.DIRECTORY_PODCASTS);
  }

  @NonNull
  public File getImagesDir() {
    return new File(appFilesDir, Environment.DIRECTORY_PICTURES);
  }

  public boolean isPrimaryStorage() {
    Storage prim = getPrimaryStorage();
    return equals(prim) || appFilesDir.getPath().startsWith(prim.appFilesDir + File.separator);
  }

  public boolean isAvailableRead() {
    String state = getState();
    return Environment.MEDIA_MOUNTED_READ_ONLY.equals(state) ||
        Environment.MEDIA_MOUNTED.equals(state) || UNKNOWN_STATE.equals(state);
  }

  public boolean isAvailableRW() {
    String state = getState();
    return Environment.MEDIA_MOUNTED.equals(state) || UNKNOWN_STATE.equals(state);
  }

  @NonNull
  private String getState() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return Environment.getExternalStorageState(appFilesDir);
    } else {
      return isPrimaryStorage() ? Environment.getExternalStorageState() : UNKNOWN_STATE;
    }
  }

  public boolean isRemovable() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return Environment.isExternalStorageRemovable(appFilesDir);
    } else {
      return isPrimaryStorage() ? Environment.isExternalStorageRemovable() : true;
    }
  }

  @NonNull
  @Override
  public String toString() {
    return appFilesDir.getAbsolutePath();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    Storage storage = (Storage) o;

    // it safe to compare these file paths, as they were converted to canonical form in constructor
    return appFilesDir.equals(storage.appFilesDir);
  }

  @Override
  public int hashCode() {
    return appFilesDir.hashCode();
  }
}
