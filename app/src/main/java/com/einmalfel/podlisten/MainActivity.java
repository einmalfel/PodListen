package com.einmalfel.podlisten;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import com.astuetz.PagerSlidingTabStrip;

public class MainActivity extends FragmentActivity implements PlayerService.PlayerStateListener,
    View.OnClickListener {
  enum Pages {PLAYER, PLAYLIST, NEW_EPISODES, SUBSCRIPTIONS}

  private static final String[] TAB_NAMES = {"Playing", "Playlist", "New episodes",
      "Subscriptions"};

  static final String PAGE_LAUNCH_OPTION = "Page";

  private class TabsAdapter extends FragmentPagerAdapter {
    PlayerFragment currentPlayerFragment = null;

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
      switch (Pages.values()[position]) {
        case PLAYER:
          return new PlayerFragment();
        case PLAYLIST:
          return new PlaylistFragment();
        case NEW_EPISODES:
          return new NewEpisodesFragment();
        case SUBSCRIPTIONS:
          return new SubscriptionsFragment();
        default:
          Log.e(TAG, "Trying to create fragment for wrong position " + position);
          return null;
      }
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
      Object newFragment = super.instantiateItem(container, position);
      if (position == Pages.PLAYER.ordinal()) {
        currentPlayerFragment = (PlayerFragment) newFragment;
      }
      return newFragment;
    }
  }

  public static final int POLL_FREQUENCY = 60 * 60;
  private static final String TAG = "MAC";

  final PlayerLocalConnection connection = new PlayerLocalConnection(this);
  private Account account;
  private ViewPager pager;
  private ImageButton playButton;
  private ImageButton ffButton;
  private ImageButton fbButton;
  private ImageButton nextButton;
  private ProgressBar progressBar;
  private WidgetHelper widgetHelper;
  private TabsAdapter tabsAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ContentResolver.addPeriodicSync(getAccount(), getString(R.string.app_id), Bundle.EMPTY, POLL_FREQUENCY);
    ContentResolver.setSyncAutomatically(getAccount(), getString(R.string.app_id), true);
    setContentView(R.layout.activity_main);
    pager = (ViewPager) findViewById(R.id.pager);
    tabsAdapter = new TabsAdapter(getSupportFragmentManager());
    pager.setAdapter(tabsAdapter);
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
  protected void onPause() {
    super.onPause();
    connection.unbind();
  }

  @Override
  protected void onResume() {
    super.onResume();
    connection.bind();
  }

  @Override
  public void progressUpdate(final int position, final int max) {
    // progress bar present in portrait orientation only
    if (progressBar != null) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          progressBar.setMax(max);
          progressBar.setProgress(position);
        }
      });
    }
  }

  @Override
  public void stateUpdate(final PlayerService.State state) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        boolean isRan = state == PlayerService.State.PLAYING || state == PlayerService.State.PAUSED;
        fbButton.setEnabled(isRan);
        ffButton.setEnabled(isRan);
        if (state == PlayerService.State.PLAYING) {
          playButton.setImageResource(R.mipmap.ic_pause_grey600_36dp);
        } else {
          playButton.setImageResource(R.mipmap.ic_play_arrow_grey600_36dp);
        }
      }
    });
  }

  @Override
  public void episodeUpdate(long id) {
    Cursor cursor = getContentResolver().query(Provider.getUri(Provider.T_EPISODE, id), null,
        null, null, null);
    if (cursor.moveToFirst()) {
      final String title = cursor.getString(cursor.getColumnIndex(Provider.K_ENAME));
      final String description = cursor.getString(cursor.getColumnIndex(Provider.K_EDESCR));
      final String eURL = cursor.getString(cursor.getColumnIndex(Provider.K_EURL));
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          tabsAdapter.currentPlayerFragment.setText(title, description, eURL);
        }
      });
    } else {
      Log.e(TAG, "Playing non-existent episode " + Long.toString(id));
    }
    cursor.close();
  }

  @Override
  public synchronized void onClick(View v) {
    if (widgetHelper == null && (v == playButton || v == nextButton)) {
      // before playback launches, widget helper should be up and bound
      widgetHelper = WidgetHelper.getInstance();
    }

    if (connection.service == null) {
      // skip tap if not bound to player yet. This is quite unlikely
      Log.e(TAG, "Skipping player action. Service is not ready yet");
      return;
    }

    if (v == playButton) {
      switch (connection.service.getState()) {
        case STOPPED:
        case STOPPED_ERROR:
          connection.service.playNext();
          break;
        case PLAYING:
          connection.service.pause();
          break;
        case PAUSED:
          connection.service.resume();
          break;
      }
    } else if (v == nextButton) {
      connection.service.playNext();
    } else if (v == ffButton) {
      connection.service.jumpForward();
    } else if (v == fbButton) {
      connection.service.jumpBackward();
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
}
