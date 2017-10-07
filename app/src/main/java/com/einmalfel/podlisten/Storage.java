package com.einmalfel.podlisten;

import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public class Storage {
  private static final String TAG = "STR";
  private static final String APP_FILES = "Android/data/" + BuildConfig.APPLICATION_ID + "/files";
  private static final String UNKNOWN_STATE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
      ? Environment.MEDIA_UNKNOWN : "unknown";
  private final File appFilesDir; // /*/Android/data/com.einmalfel.podlisten/files

  /**
   * @param path directory where audio and image data will be stored
   * @throws IOException if given path couldn't be converted to canonical form
   */
  Storage(@NonNull File path) throws IOException {
    this.appFilesDir = path.getCanonicalFile();
  }

  /**
   * @return Ordered and deduplicated list of writable storages. Order: primary first, then dirs
   *     obtained from getExternalDirs, then dirs from environment variables
   */
  @NonNull
  public static LinkedHashSet<Storage> getWritableStorages() {
    LinkedHashSet<Storage> result = new LinkedHashSet<>();
    Set<File> dirs = new LinkedHashSet<>();
    // calling getCanonicalFile to ensure no symbolic links are processed
    dirs.add(new File(tryGetCanonicalFile(Environment.getExternalStorageDirectory()), APP_FILES));
    for (File dir : ContextCompat.getExternalFilesDirs(PodListenApp.getContext(), null)) {
      dirs.add(tryGetCanonicalFile(dir));
    }
    for (String env : new String[]{"EXTERNAL_STORAGE", "SECONDARY_STORAGE",
                                   "EXTERNAL_SDCARD_STORAGE", "SECOND_VOLUME_STORAGE",
                                   "THIRD_VOLUME_STORAGE"}) {
      String value = System.getenv(env);
      if (!TextUtils.isEmpty(value)) {
        for (String path : value.split(":")) {
          File storageDir = new File(path);
          // filter legacy out. Download to thais dir fails on CyanogenMod because of perm. problems
          if (!path.startsWith("/storage/emulated/legacy") && storageDir.isDirectory()) {
            File filesDir = tryGetCanonicalFile(new File(storageDir, APP_FILES));
            if (dirs.add(filesDir)) {
              Log.i(TAG, "Found storage via environment variable: " + filesDir);
            }
          }
        }
      }
    }

    // some paths found in env variables may cause Android API to produce exceptions in following
    // functions on android versions 5.0+ (seen this bug in emulator). Filter such storages out
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      Set<File> filtered = new LinkedHashSet<>(dirs.size());
      for (File dir : dirs) {
        if (dir != null) {
          try {
            Environment.getExternalStorageState(dir);
            Environment.isExternalStorageRemovable(dir);
            filtered.add(dir);
          } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Storage " + dir + " makes Android API throw exception, ignoring it", ex);
          }
        }
      }
      dirs = filtered;
    }

    // filter out read-only storages
    for (File dir : dirs) {
      if (dir != null) { //getExternalFilesDir could return nulls for currently unavailable storages
        try {
          Storage storage = new Storage(dir);
          String state = storage.getState();
          // unlike isAvailableRw(), use more slow and precise method to determine writability here
          if (UNKNOWN_STATE.equals(state) && !storage.isPrimaryStorage()) {
            // if PodListen directories already exist, assume it's a writable storage
            if (storage.getImagesDir().exists() || storage.getPodcastDir().exists()) {
              state = Environment.MEDIA_MOUNTED;
              Log.i(TAG, "Storage " + storage + ". Already contains PodListen dirs. It's writable");
            } else {
              // try create dirs to check storage is writable
              try {
                storage.createSubdirs();
                state = Environment.MEDIA_MOUNTED;
                Log.i(TAG, "Successfully created directories in " + storage + ". It's writable");
              } catch (IOException ignored) {
                Log.i(TAG, "Failed to create directories in " + storage + ". It's not writable");
              } finally {
                storage.cleanup();
              }
            }
          }
          if (Environment.MEDIA_MOUNTED.equals(state) || storage.isPrimaryStorage()) {
            Log.i(TAG, "Found writable storage: " + dir);
            result.add(storage);
          }
        } catch (IOException ioExcpetion) {
          Log.e(TAG,
                "File path couldn't be converted to canonical form:" + dir.getAbsolutePath(),
                ioExcpetion);
        }
      }
    }
    return result;
  }

  @NonNull
  public static Storage getPrimaryStorage() {
    try {
      return new Storage(new File(Environment.getExternalStorageDirectory(), APP_FILES));
    } catch (IOException exception) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        throw new AssertionError("Cant convert primary storage to canonical form", exception);
      } else {
        throw new AssertionError("Cant convert primary storage to canonical form");
      }
    }
  }

  public void createSubdirs() throws IOException {
    for (File dir : new File[]{getImagesDir(), getPodcastDir()}) {
      if (!dir.exists() && !dir.mkdirs()) {
        throw new IOException("Failed to create " + dir);
      }
    }
  }

  public void cleanup() {
    for (File dir : new File[]{getPodcastDir(), getImagesDir(), appFilesDir,
                               appFilesDir.getParentFile()}) {
      deleteRecursively(dir);
    }
  }

  @NonNull
  public File getPodcastDir() {
    return new File(appFilesDir, Environment.DIRECTORY_PODCASTS);
  }

  @NonNull
  public File getImagesDir() {
    return new File(appFilesDir, Environment.DIRECTORY_PICTURES);
  }

  /**
   * Checks whether given file belongs to this storage
   *
   * @throws IOException if File path couldn't be converted to canonical form
   */
  public boolean contains(File file) throws IOException {
    File cnFile = file.getCanonicalFile();
    return appFilesDir.equals(cnFile) || cnFile.getPath().startsWith(appFilesDir + File.separator);
  }

  public boolean isPrimaryStorage() {
    Storage prim = getPrimaryStorage();
    return equals(prim) || appFilesDir.getPath().startsWith(prim.appFilesDir + File.separator);
  }

  public boolean isAvailableRead() {
    String state = getState();
    return Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)
        || Environment.MEDIA_MOUNTED.equals(state) || UNKNOWN_STATE.equals(state);
  }

  public boolean isAvailableRw() {
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
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    Storage storage = (Storage) other;

    // it safe to compare these file paths, as they were converted to canonical form in constructor
    return appFilesDir.equals(storage.appFilesDir);
  }

  @Override
  public int hashCode() {
    return appFilesDir.hashCode();
  }

  @NonNull
  private static File tryGetCanonicalFile(@NonNull File file) {
    File result = null;
    try {
      result = file.getCanonicalFile();
    } catch (IOException ioException) {
      Log.e(TAG, "Failed to get canonical of " + file + ":" + Log.getStackTraceString(ioException));
    }
    return result == null ? file : result;
  }

  private static boolean deleteRecursively(@NonNull File file) {
    if (file.isDirectory()) {
      String[] subFileNames = file.list();
      if (subFileNames != null) { // seen null here on api-24 emulator
        for (String subFileName : subFileNames) {
          if (!deleteRecursively(new File(subFileName))) {
            Log.e(TAG, "Failed to delete " + subFileName);
          }
        }
      }
      return file.delete();
    } else if (file.exists()) {
      return file.delete();
    } else {
      return true;
    }
  }
}
