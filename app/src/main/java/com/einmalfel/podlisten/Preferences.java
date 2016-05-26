package com.einmalfel.podlisten;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** This class is intended to encapsulate preferences names and default values */
public class Preferences implements SharedPreferences.OnSharedPreferenceChangeListener {
  enum Key {
    STORAGE_PATH,
    MAX_DOWNLOADS,
    REFRESH_INTERVAL,
    SORTING_MODE,
    PLAYER_FOREGROUND,
    AUTO_DOWNLOAD,
    AUTO_DOWNLOAD_AC,
    DOWNLOAD_NETWORK,
    COMPLETE_ACTION,
    JUMP_INTERVAL,
    CURRENT_ACTIVITY,
    PAUSE_ON_DISCONNECT,
    FIX_SKIP_ENDING,
  }

  enum JumpInterval {
    TEN_SECONDS(R.string.jump_interval_10sec),
    TWENTY_SECONDS(R.string.jump_interval_20sec),
    THIRTY_SECONDS(R.string.jump_interval_30sec),
    ONE_MINUTE(R.string.jump_interval_1min),
    FIVE_MINUTES(R.string.jump_interval_5min);

    private final int stringId;

    JumpInterval(@StringRes int stringId) {
      this.stringId = stringId;
    }

    @Override
    public String toString() {
      return PodListenApp.getContext().getString(stringId);
    }

    public int inMilliseconds() {
      switch (this) {
        case TEN_SECONDS:
          return 10000;
        case TWENTY_SECONDS:
          return 20000;
        case THIRTY_SECONDS:
          return 30000;
        case ONE_MINUTE:
          return 60000;
        case FIVE_MINUTES:
          return 300000;
        default:
          throw new AssertionError("Unknown jump interval: " + this);
      }
    }
  }

  enum CompleteAction {
    DO_NOTHING(R.string.playback_complete_do_nothing),
    PLAY_NEXT(R.string.playback_complete_play_next),
    DELETE_PLAY_NEXT(R.string.playback_complete_delete_play_next),
    PLAY_FIRST(R.string.playback_complete_play_first),
    DELETE_PLAY_FIRST(R.string.playback_complete_delete_play_first),
    DELETE_DO_NOTHING(R.string.playback_complete_delete_do_nothing);

    private final int stringId;

    CompleteAction(@StringRes int stringId) {
      this.stringId = stringId;
    }

    @Override
    public String toString() {
      return PodListenApp.getContext().getString(stringId);
    }
  }

  enum DownloadNetwork {
    WIFI(R.string.download_network_wifi),
    NON_ROAMING(R.string.download_network_non_roaming),
    ANY(R.string.download_network_any);

    private final int stringId;

    DownloadNetwork(@StringRes int stringId) {
      this.stringId = stringId;
    }

    @Override
    public String toString() {
      return PodListenApp.getContext().getString(stringId);
    }
  }

  enum AutoDownloadMode {
    ALL_NEW(R.string.auto_download_all_new),
    PLAYLIST(R.string.auto_download_playlist),
    NEVER(R.string.auto_download_never);

    private final int stringId;

    AutoDownloadMode(@StringRes int stringId) {
      this.stringId = stringId;
    }

    @Override
    public String toString() {
      return PodListenApp.getContext().getString(stringId);
    }
  }

  enum MaxDownloadsOption {
    ONE, TWO, THREE, FOUR, FIVE, TEN, UNLIMITED;

    public int toInt() {
      switch (this) {
        case TEN:
          return 10;
        case UNLIMITED:
          return Integer.MAX_VALUE;
        default:
          return ordinal() + 1;
      }
    }

    @Override
    public String toString() {
      if (this == UNLIMITED) {
        return PodListenApp.getContext().getString(R.string.preferences_max_downloads_unlimited);
      } else {
        return Integer.toString(toInt());
      }
    }
  }

  enum SortingMode {
    OLDEST_FIRST, NEWEST_FIRST, BY_FEED, SHORTEST_FIRST, LONGEST_FIRST;

    @NonNull
    public String toSql() {
      switch (this) {
        case OLDEST_FIRST:
          return Provider.K_EDATE + " ASC";
        case NEWEST_FIRST:
          return Provider.K_EDATE + " DESC";
        case BY_FEED:
          return Provider.K_EPID + " ASC";
        case SHORTEST_FIRST:
          return Provider.K_ELENGTH + " ASC";
        case LONGEST_FIRST:
          return Provider.K_ELENGTH + " DESC";
        default:
          throw new AssertionError("Unknown sorting mode");
      }
    }

    @NonNull
    public SortingMode nextCyclic() {
      int newArrayId = ordinal() == values().length - 1 ? 0 : ordinal() + 1;
      return values()[newArrayId];
    }

    /** @return at string intended to be shown in snackbar when user switches modes */
    @Override
    public String toString() {
      switch (this) {
        case OLDEST_FIRST:
          return PodListenApp.getContext().getString(R.string.sorting_mode_oldest_first);
        case NEWEST_FIRST:
          return PodListenApp.getContext().getString(R.string.sorting_mode_newest_first);
        case BY_FEED:
          return PodListenApp.getContext().getString(R.string.sorting_mode_by_feed);
        case SHORTEST_FIRST:
          return PodListenApp.getContext().getString(R.string.sorting_mode_shortest_first);
        case LONGEST_FIRST:
          return PodListenApp.getContext().getString(R.string.sorting_mode_longest_first);
        default:
          throw new AssertionError("Unknown sorting mode");
      }
    }
  }

  enum RefreshIntervalOption {
    NEVER(R.string.refresh_period_never, 0),
    HOUR(R.string.refresh_period_hour, 1),
    HOUR2(R.string.refresh_period_2hours, 2),
    HOUR3(R.string.refresh_period_3hours, 3),
    HOUR6(R.string.refresh_period_6hours, 6),
    HOUR12(R.string.refresh_period_12hours, 12),
    DAY(R.string.refresh_period_day, 24),
    DAY2(R.string.refresh_period_2days, 24 * 2),
    WEEK(R.string.refresh_period_week, 24 * 7),
    WEEK2(R.string.refresh_period_2weeks, 24 * 14),
    MONTH(R.string.refresh_period_month, 30 * 24);

    public final int periodSeconds;
    private final int stringResource;

    RefreshIntervalOption(@StringRes int stringResource, int periodHours) {
      this.periodSeconds = periodHours * 60 * 60;
      this.stringResource = stringResource;
    }

    @Override
    public String toString() {
      return PodListenApp.getContext().getString(stringResource);
    }
  }

  private static final String TAG = "PRF";
  private static final MaxDownloadsOption DEFAULT_MAX_DOWNLOADS = MaxDownloadsOption.TWO;
  private static final RefreshIntervalOption DEFAULT_REFRESH_INTERVAL = RefreshIntervalOption.DAY;
  private static final SortingMode DEFAULT_SORTING_MODE = SortingMode.OLDEST_FIRST;
  private static final AutoDownloadMode DEFAULT_DOWNLOAD_MODE = AutoDownloadMode.ALL_NEW;
  private static final DownloadNetwork DEFAULT_DOWNLOAD_NETWORK = DownloadNetwork.WIFI;
  private static final CompleteAction DEFAULT_COMPLETE_ACTION = CompleteAction.PLAY_NEXT;
  private static final JumpInterval DEFAULT_JUMP_INTERVAL = JumpInterval.THIRTY_SECONDS;
  private static Preferences instance = null;

  // fields below could be changed from readPreference() only
  private MaxDownloadsOption maxDownloads;
  private Storage storage;
  private RefreshIntervalOption refreshInterval;
  private SortingMode sortingMode;
  private AutoDownloadMode autoDownloadMode;
  private DownloadNetwork downloadNetwork;
  private CompleteAction completeAction;
  private JumpInterval jumpInterval;
  private boolean autoDownloadACOnly;
  private boolean playerForeground; // preserve last player service state across app kill/restarts
  @Nullable
  private String currentActivity; // current activity class name, for services in separate process
  private boolean pauseOnDisconnect;
  private boolean fixSkipEnding;

  private SharedPreferences sPrefs;

  private final Context context = PodListenApp.getContext();

  public Preferences() {
    // TODO make prefs synced between processes via IPC. MULTI_PROCESS is deprecated and unreliable
    sPrefs = context.getSharedPreferences(context.getPackageName() + "_preferences",
            Context.MODE_MULTI_PROCESS);
    sPrefs.registerOnSharedPreferenceChangeListener(this);
    for (Key key : Key.values()) {
      readPreference(key);
    }
  }

  public static Preferences getInstance() {
    if (instance == null) {
      synchronized (Preferences.class) {
        if (instance == null) {
          instance = new Preferences();
        }
      }
    }
    return instance;
  }

  /**
   * When there is some downloaded episodes on current storage and user asks to switch storage
   * - stop all running downloads
   * - stop and disable sync
   * - stop playback if not streaming (TODO)
   * - reset download progress and download ID fields
   * - remove old files
   * - ask download manager to start downloads for all non-gone episodes
   * - re-enable sync and re-run it to re-download images
   */
  private void clearStorage() {
    DownloadReceiver.stopDownloads(null);

    PodlistenAccount account = PodlistenAccount.getInstance();
    account.setupSync(0);
    account.cancelRefresh();

    ContentValues cv = new ContentValues(4);
    cv.put(Provider.K_EDID, 0);
    cv.put(Provider.K_EDFIN, 0);
    cv.put(Provider.K_EDTSTAMP, 0);
    cv.put(Provider.K_EERROR, (String)null);
    context.getContentResolver().update(Provider.episodeUri, cv, null, null);

    storage.cleanup();

    context.sendBroadcast(new Intent(DownloadReceiver.UPDATE_QUEUE_ACTION));
    account.refresh(0);
    account.setupSync(getRefreshInterval().periodSeconds);
  }

  @NonNull
  private <T extends Enum<T>> T readEnum(@NonNull Key key, @NonNull T defaultValue) {
    try {
      String pref = sPrefs.getString(key.toString(), "-1");
      int id = Integer.valueOf(pref);
      return defaultValue.getDeclaringClass().getEnumConstants()[id];
    } catch (ClassCastException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
      Log.e(TAG, "Illegal enum value, reverting to default: " + defaultValue.toString(), e);
      sPrefs.edit().putString(key.toString(), Integer.toString(defaultValue.ordinal())).commit();
      return defaultValue;
    }
  }

  private synchronized void readPreference(Key key) {
    switch (key) {
      case JUMP_INTERVAL:
        jumpInterval = readEnum(Key.JUMP_INTERVAL, DEFAULT_JUMP_INTERVAL);
      case COMPLETE_ACTION:
        completeAction = readEnum(Key.COMPLETE_ACTION, DEFAULT_COMPLETE_ACTION);
        break;
      case DOWNLOAD_NETWORK:
        DownloadNetwork newDLNetwork = readEnum(Key.DOWNLOAD_NETWORK, DEFAULT_DOWNLOAD_NETWORK);
        if (downloadNetwork != newDLNetwork) {
          downloadNetwork = newDLNetwork;
          DownloadReceiver.stopDownloads(null);
          context.sendBroadcast(new Intent(DownloadReceiver.UPDATE_QUEUE_ACTION));
        }
        break;
      case PAUSE_ON_DISCONNECT:
        pauseOnDisconnect = sPrefs.getBoolean(Key.PAUSE_ON_DISCONNECT.toString(), true);
        break;
      case FIX_SKIP_ENDING:
        fixSkipEnding = sPrefs.getBoolean(Key.FIX_SKIP_ENDING.toString(), false);
        break;
      case AUTO_DOWNLOAD_AC:
        boolean newAutoDownloadAC = sPrefs.getBoolean(Key.AUTO_DOWNLOAD_AC.toString(), false);
        if (newAutoDownloadAC != autoDownloadACOnly) {
          autoDownloadACOnly = newAutoDownloadAC;
          if (!autoDownloadACOnly) {
            context.sendBroadcast(new Intent(DownloadReceiver.UPDATE_QUEUE_ACTION));
          } else if (!DownloadReceiver.isDeviceCharging()) {
            DownloadReceiver.stopDownloads(null);
          }
        }
        break;
      case PLAYER_FOREGROUND:
        playerForeground = sPrefs.getBoolean(Key.PLAYER_FOREGROUND.toString(), false);
        break;
      case CURRENT_ACTIVITY:
        currentActivity = sPrefs.getString(Key.CURRENT_ACTIVITY.toString(), null);
        Log.e(TAG, "read " + currentActivity);
        break;
      case AUTO_DOWNLOAD:
        AutoDownloadMode newM = readEnum(Key.AUTO_DOWNLOAD, DEFAULT_DOWNLOAD_MODE);
        if (newM != autoDownloadMode) {
          if (newM == AutoDownloadMode.PLAYLIST && autoDownloadMode == AutoDownloadMode.ALL_NEW) {
            DownloadReceiver.stopDownloads(
                Provider.K_ESTATE + " != " + Provider.ESTATE_IN_PLAYLIST);
          } else if (newM == AutoDownloadMode.NEVER) {
            DownloadReceiver.stopDownloads(null);
          }
          autoDownloadMode = newM;
          context.sendBroadcast(new Intent(DownloadReceiver.UPDATE_QUEUE_ACTION));
        }
        break;
      case SORTING_MODE:
        sortingMode = readEnum(Key.SORTING_MODE, DEFAULT_SORTING_MODE);
        break;
      case MAX_DOWNLOADS:
        MaxDownloadsOption newMaxDL = readEnum(Key.MAX_DOWNLOADS, DEFAULT_MAX_DOWNLOADS);
        if (newMaxDL != maxDownloads) {
          maxDownloads = newMaxDL;
          context.sendBroadcast(new Intent(DownloadReceiver.UPDATE_QUEUE_ACTION));
        }
        break;
      case REFRESH_INTERVAL:
        RefreshIntervalOption newRI = readEnum(Key.REFRESH_INTERVAL, DEFAULT_REFRESH_INTERVAL);
        if (newRI != refreshInterval) {
          refreshInterval = newRI;
          PodlistenAccount.getInstance().setupSync(refreshInterval.periodSeconds);
        }
        break;
      case STORAGE_PATH:
        String storagePreferenceString = sPrefs.getString(Key.STORAGE_PATH.toString(), "");
        if (storagePreferenceString.isEmpty()) {
          // by default, if there are removable storages use first removable, otherwise use last one
          List<Storage> storages = Storage.getWritableStorages();
          storage = Storage.getPrimaryStorage();
          for (Storage storageOption : storages) {
            storage = storageOption;
            if (storageOption.isRemovable()) {
              break;
            }
          }
          try {
            storage.createSubdirs();
            sPrefs.edit().putString(Key.STORAGE_PATH.toString(), storage.toString()).commit();
          } catch (IOException exception) {
            Log.wtf(TAG, "Failed to init storage known to be writable " + storage, exception);
          }
        } else {
          try {
            Storage newStorage = new Storage(new File(storagePreferenceString));
            newStorage.createSubdirs();
            if (storage != null && !storage.equals(newStorage)) {
              clearStorage();
            }
            storage = newStorage;
          } catch (IOException e) {
            Log.wtf(
                TAG, "Failed to set storage " + storagePreferenceString + ". Reverting to prev", e);
            sPrefs.edit().putString(
                Key.STORAGE_PATH.toString(), storage == null ? "" : storage.toString()).commit();
          }
        }
        break;
      default:
        Log.e(TAG, "Unexpected key received: " + key);
        break;
    }
  }

  @NonNull
  public RefreshIntervalOption getRefreshInterval() {
    return refreshInterval;
  }

  @NonNull
  public MaxDownloadsOption getMaxDownloads() {
    return maxDownloads;
  }

  @Nullable
  public Storage getStorage() {
    return storage;
  }

  @NonNull
  public SortingMode getSortingMode() {
    return sortingMode;
  }

  public void setSortingMode(SortingMode sortingMode) {
    sPrefs.edit()
          .putString(Key.SORTING_MODE.toString(), Integer.toString(sortingMode.ordinal()))
          .commit();
  }

  public void setPlayerForeground(boolean playerServicePlaying) {
    sPrefs.edit().putBoolean(Key.PLAYER_FOREGROUND.toString(), playerServicePlaying).commit();
  }

  public boolean getPlayerForeground() {
    return playerForeground;
  }

  public void setCurrentActivity(@Nullable String currentActivity) {
    sPrefs.edit().putString(Key.CURRENT_ACTIVITY.toString(), currentActivity).commit();
  }

  @Nullable
  public String getCurrentActivity(boolean sync) {
    if (sync) { // TODO remove this, make preferences synchronized between processes via IPC
      // need to re-get shared preferences object to cause reloading of preferences file
      sPrefs = context.getSharedPreferences(context.getPackageName() + "_preferences",
                                            Context.MODE_MULTI_PROCESS);
      readPreference(Key.CURRENT_ACTIVITY);
    }
    return currentActivity;
  }


  public boolean getAutoDownloadACOnly() {
    return autoDownloadACOnly;
  }

  @NonNull
  public AutoDownloadMode getAutoDownloadMode() {
    return autoDownloadMode;
  }

  @NonNull
  public DownloadNetwork getDownloadNetwork() {
    return downloadNetwork;
  }

  @NonNull
  public CompleteAction getCompleteAction() {
    return completeAction;
  }

  @NonNull
  public JumpInterval getJumpInterval() {
    return jumpInterval;
  }

  public boolean getPauseOnDisconnect() {
    return pauseOnDisconnect;
  }

  public boolean fixSkipEnding() {
    return fixSkipEnding;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Log.i(TAG, "Preference changed " + key + ", values: " + sharedPreferences.getAll().toString());
    Key preferenceKey;
    try {
      preferenceKey = Key.valueOf(key);
    } catch (IllegalArgumentException exception) {
      Log.i(TAG, "Unknown preference " + key + ", skipping..");
      return;
    }
    readPreference(preferenceKey);
  }
}
