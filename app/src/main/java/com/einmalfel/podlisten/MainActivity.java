package com.einmalfel.podlisten;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import com.astuetz.PagerSlidingTabStrip;

import java.io.File;
import java.io.IOException;


public class MainActivity extends FragmentActivity implements MediaPlayer.OnCompletionListener,
    View.OnClickListener, Runnable {
  private static final String TAG = "MainActivity";
  public static final int POLL_FREQUENCY = 60 * 60;
  public static final int JUMP_INTERVAL = 30000;
  private Account account;
  private ViewPager pager;
  private static MediaPlayer player;
  private static long playingID = 0;

  private ImageButton playButton;
  private ImageButton ffButton;
  private ImageButton fbButton;
  private ImageButton nextButton;
  private ProgressBar progressBar;


  enum Pages {PLAYER, PLAYLIST, NEW_EPISODES, SUBSCRIPTIONS}
  static final String PAGE_LAUNCH_OPTION = "Page";

  private static class TabsAdapter extends FragmentPagerAdapter {
    private static final String[] TAB_NAMES = {"Player", "Playlist", "New episodes",
        "Subscriptions"};

    TabsAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public int getCount() {
      return Pages.values().length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      return TAB_NAMES[position];
    }

    @Override
    public Fragment getItem(int position) {
      Log.v(TAG, "Making fragment for position " + position);
      switch (Pages.values()[position]) {
        case PLAYER:
          return new PlayerFragment();
        case PLAYLIST:
          return new PlaylistFragment();
        case NEW_EPISODES:
          return new NewEpisodesFragment();
        case SUBSCRIPTIONS:
          return new SubscriptionsFragment();
      }
      Log.e(TAG, "Wrong fragment number " + position);
      return null;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ContentResolver.addPeriodicSync(getAccount(), getString(R.string.app_id), Bundle.EMPTY, POLL_FREQUENCY);
    ContentResolver.setSyncAutomatically(getAccount(), getString(R.string.app_id), true);
    setContentView(R.layout.activity_main);
    pager = (ViewPager) findViewById(R.id.pager);
    pager.setAdapter(new TabsAdapter(getSupportFragmentManager()));
    PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);
    tabs.setViewPager(pager);
    playButton = (ImageButton) findViewById(R.id.play_button);
    nextButton = (ImageButton) findViewById(R.id.next_button);
    fbButton = (ImageButton) findViewById(R.id.fb_button);
    ffButton = (ImageButton) findViewById(R.id.ff_button);
    playButton.setOnClickListener(this);
    nextButton.setOnClickListener(this);
    fbButton.setOnClickListener(this);
    ffButton.setOnClickListener(this);
    progressBar = (ProgressBar) findViewById(R.id.play_progress);
  }

  @Override
  public synchronized void onClick(View v) {
    if (v == playButton) {
      if (playingID != 0) {
        if (player.isPlaying()) {
          Log.d(TAG, "PAUSE");
          playButton.setImageResource(R.mipmap.ic_play_arrow_grey600_36dp);
          player.pause();
        } else {
          Log.d(TAG, "RESUME");
          playButton.setImageResource(R.mipmap.ic_pause_grey600_36dp);
          player.start();
        }
      } else {
        Log.d(TAG, "Starting fresh playback");
        tryPlayNext();
      }
    } else if (v == nextButton) {
      if (playingID != 0) {
        PlaylistFragment.deleteEpisode(stop(), this);
      }
      Log.d(TAG, "NEXT");
      tryPlayNext();
    } else if (v == ffButton && playingID != 0) {
      player.seekTo(player.getCurrentPosition() + JUMP_INTERVAL);
    } else if (v == fbButton && playingID != 0) {
      player.seekTo(player.getCurrentPosition() - JUMP_INTERVAL);
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (intent.hasExtra(PAGE_LAUNCH_OPTION)) {
      Log.d(TAG, "Setting page " + intent.getIntExtra(PAGE_LAUNCH_OPTION, 0));
      pager.setCurrentItem(intent.getIntExtra(PAGE_LAUNCH_OPTION, 0));
    }
  }

  public synchronized boolean isPlaying() {
    return playingID != 0;
  }

  public synchronized long stop() {
    long prevplayingID = playingID;
    if (playingID != 0) {
      Log.d(TAG, "Stopping player " + prevplayingID);
      player.stop();
      player.release();
      playingID = 0;
      playButton.setImageResource(R.mipmap.ic_play_arrow_grey600_36dp);
      if (progressBar != null) {
        progressBar.setVisibility(View.GONE);
      }
    }
    return prevplayingID;
  }

  @Override
  public synchronized void onCompletion(MediaPlayer mp) {
    Log.d(TAG, "Play complete");
    Long epEnded = stop();
    if (epEnded != 0) {
      PlaylistFragment.deleteEpisode(epEnded, this);
    }
    tryPlayNext();
  }

  public synchronized boolean tryStart(long id) {
    Log.d(TAG, "Trying to start " + id);
    if (playingID != 0) {
      stop();
    }
    Uri uri = Provider.getUri(Provider.T_EPISODE, id);
    Cursor cursor = getContentResolver().query(uri, null, null, null, null);
    if (cursor == null) {
      return false;
    }
    if (!cursor.moveToFirst() || cursor.getInt(cursor.getColumnIndex(Provider.K_EDFIN)) != 100) {
      cursor.close();
      return false;
    }
    String title = cursor.getString(cursor.getColumnIndex(Provider.K_ENAME));
    String description = cursor.getString(cursor.getColumnIndex(Provider.K_EDESCR));
    String eURL = cursor.getString(cursor.getColumnIndex(Provider.K_EURL));
    cursor.close();
    File file = new File(getExternalFilesDir(Environment.DIRECTORY_PODCASTS), Long.toString(id));
    if (!file.exists()) {
      ContentValues values = new ContentValues();
      values.put(Provider.K_EDFIN, 0);
      getContentResolver().update(uri, values, null, null);
      return false;
    }
    player = new MediaPlayer();
    player.setLooping(false);
    player.setOnCompletionListener(this);
    try {
      player.setDataSource(this, Uri.fromFile(file));
      player.prepare();
    } catch (IOException e) {
      Log.e(TAG, "Failed to set play source " + e);
      player.stop();
      player.release();
      return false;
    }
    player.start();
    if (progressBar != null) {
      new Thread(this).start();
      progressBar.setVisibility(View.VISIBLE);
    }
    playingID = id;
    PlayerFragment.setText(title, description, eURL);
    playButton.setImageResource(R.mipmap.ic_pause_grey600_36dp);
    return true;
  }

  public boolean tryPlayNext() {
    Cursor c = getContentResolver().query(Provider.episodeUri, null,
        Provider.K_ESTATE + " = ? AND " + Provider.K_EDFIN + " = ?",
        new String[]{Integer.toString(Provider.ESTATE_IN_PLAYLIST), Integer.toString(100)},
        Provider.K_EDATE);
    if (c == null) {
      return false;
    }
    if (c.moveToFirst()) {
      do {
        boolean result = tryStart(c.getLong(c.getColumnIndex(Provider.K_ID)));
        if (result) {
          c.close();
          return true;
        }
      } while (c.moveToNext());
    }
    c.close();
    return false;
  }

  public Account getAccount() {
    if (account == null) {
      // user base authority/app id as stub account type and name
      String accountTypeName = getResources().getString(R.string.app_id);
      account = new Account(accountTypeName, accountTypeName);
      AccountManager accountManager = (AccountManager) getSystemService(ACCOUNT_SERVICE);
      accountManager.addAccountExplicitly(account, null, null);
    }
    return account;
  }

  public void refresh() {
    Bundle settingsBundle = new Bundle();
    settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
    settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
    Account acc = getAccount();
    ContentResolver.requestSync(acc, acc.type, settingsBundle);
  }


  private synchronized boolean checkProgressBar() {
    if (playingID == 0 || player == null) {
      return false;
    } else {
      if (progressBar != null) {
        progressBar.setMax(player.getDuration());
        progressBar.setProgress(player.getCurrentPosition());
      }
      return true;
    }
  }

  @Override
  public void run() {
    while (checkProgressBar()) {
      try {
        Thread.sleep(350);
      } catch (InterruptedException ignored) {
      }
    }
  }
}
