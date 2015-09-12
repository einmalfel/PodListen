package com.einmalfel.podlisten;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends FragmentActivity implements PlayerService.PlayerStateListener,
    View.OnClickListener {
  enum Pages {PLAYLIST, NEW_EPISODES, SUBSCRIPTIONS}

  private static final String[] TAB_NAMES = {"Playlist", "New episodes",
      "Subscriptions"};

  static final String PAGE_LAUNCH_OPTION = "Page";
  static final String EPISODE_ID_OPTION = "Episode";
  static final LightingColorFilter disabledFilter = new LightingColorFilter(Color.GRAY, 0);

  private class TabsAdapter extends FragmentPagerAdapter {

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
    public Object instantiateItem(ViewGroup container, int position) {
      Fragment newFragment = (Fragment) super.instantiateItem(container, position);
      if (position == Pages.PLAYLIST.ordinal()) {
        playlistFragment = (PlaylistFragment) newFragment;
      }
      return newFragment;
    }

    @Override
    public Fragment getItem(int position) {
      switch (Pages.values()[position]) {
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
  }

  public static final int POLL_FREQUENCY = 60 * 60;
  private static final int DOWNLOAD_CHECK_PERIOD = 500;
  private static final String TAG = "MAC";

  WidgetHelper widgetHelper;
  PlayerLocalConnection connection;
  private Account account;
  private ViewPager pager;
  private ImageButton playButton;
  private ImageButton ffButton;
  private ImageButton fbButton;
  private ImageButton nextButton;
  private ProgressBar progressBar;
  private TextView progressBarTitle;
  private ImageView episodeImage;
  private TabLayout tabLayout;
  private TabsAdapter tabsAdapter;
  private Timer timer;
  private PlaylistFragment playlistFragment = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ContentResolver.addPeriodicSync(getAccount(), getString(R.string.app_id), Bundle.EMPTY, POLL_FREQUENCY);
    ContentResolver.setSyncAutomatically(getAccount(), getString(R.string.app_id), true);
    setContentView(R.layout.activity_main);

    playButton = (ImageButton) findViewById(R.id.play_button);
    nextButton = (ImageButton) findViewById(R.id.next_button);
    fbButton = (ImageButton) findViewById(R.id.fb_button);
    ffButton = (ImageButton) findViewById(R.id.ff_button);
    tabLayout = (TabLayout) findViewById(R.id.tab_layout);
    pager = (ViewPager) findViewById(R.id.pager);
    playButton.setOnClickListener(this);
    nextButton.setOnClickListener(this);
    fbButton.setOnClickListener(this);
    ffButton.setOnClickListener(this);
    progressBar = (ProgressBar) findViewById(R.id.play_progress);
    progressBarTitle = (TextView) findViewById(R.id.play_title);
    episodeImage = (ImageView) findViewById(R.id.play_episode_image);
    episodeImage.setOnClickListener(this);

    tabsAdapter = new TabsAdapter(getSupportFragmentManager());
    pager.setAdapter(tabsAdapter);
    pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
    tabLayout.setTabsFromPagerAdapter(tabsAdapter);
    tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
    tabLayout.setOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(pager));

    connection = new PlayerLocalConnection(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    connection.unbind();
    timer.cancel();
    timer = null;
  }

  @Override
  protected void onResume() {
    super.onResume();
    connection.bind();
    timer = new Timer(true);
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        sendBroadcast(new Intent(DownloadStartReceiver.DOWNLOAD_HEARTBEAT_ACTION));
      }
    }, 0, DOWNLOAD_CHECK_PERIOD);
  }

  @Override
  public void progressUpdate(final int position, final int max) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        progressBar.setMax(max);
        progressBar.setProgress(position);
      }
    });
  }

  @Override
  public void stateUpdate(final PlayerService.State state) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        boolean isRan = state == PlayerService.State.PLAYING || state == PlayerService.State.PAUSED;
        fbButton.setEnabled(isRan);
        ffButton.setEnabled(isRan);
        fbButton.setColorFilter(isRan ? null : disabledFilter);
        ffButton.setColorFilter(isRan ? null : disabledFilter);
        progressBarTitle.setVisibility(isRan ? View.VISIBLE : View.INVISIBLE);
        if (state == PlayerService.State.PLAYING) {
          playButton.setImageResource(R.mipmap.ic_pause_white_36dp);
        } else {
          playButton.setImageResource(R.mipmap.ic_play_arrow_white_36dp);
        }
      }
    });
  }

  @Override
  public void episodeUpdate(final long id) {
    Cursor cursor = getContentResolver().query(Provider.getUri(Provider.T_EPISODE, id), null,
        null, null, null);
    if (cursor.moveToFirst()) {
      final String title = cursor.getString(cursor.getColumnIndex(Provider.K_ENAME));
      final String description = cursor.getString(cursor.getColumnIndex(Provider.K_EDESCR));
      final String eURL = cursor.getString(cursor.getColumnIndex(Provider.K_EURL));
      final long pId = cursor.getLong(cursor.getColumnIndex(Provider.K_EPID));
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          progressBarTitle.setText(title);
          Bitmap image = ImageManager.getInstance().getImage(id);
          if (image == null) {
            image = ImageManager.getInstance().getImage(pId);
          }
          if (image == null) {
            episodeImage.setImageResource(R.drawable.main_icon);
          } else {
            episodeImage.setImageBitmap(image);
          }
        }
      });
    } else {
      Log.e(TAG, "Playing non-existent episode " + id);
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          progressBarTitle.setText("Episode " + id + " doesn't exist");
          episodeImage.setImageResource(R.drawable.main_icon);
        }
      });
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
      connection.service.playPauseResume();
    } else if (v == nextButton) {
      connection.service.playNext();
    } else if (v == ffButton) {
      connection.service.jumpForward();
    } else if (v == fbButton) {
      connection.service.jumpBackward();
    } else if (v == episodeImage) {
      if (playlistFragment != null) {
        playlistFragment.showEpisode(connection.service.getEpisodeId());
      }
    }
  }

  public void addSubscription(String url) {
    try {
      if (PodcastHelper.getInstance().addSubscription(url)) {
        refresh();
      } else {
        Toast.makeText(this, getString(R.string.already_subscribed) + url, Toast.LENGTH_SHORT)
             .show();
      }
    } catch (PodcastHelper.SubscriptionNotInsertedException e) {
      Toast.makeText(this, getString(R.string.subscription_add_failed) + url, Toast.LENGTH_LONG)
           .show();
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.e(TAG, intent.toString());
    if (Intent.ACTION_VIEW.equals(intent.getAction())) {
      Uri uri = intent.getData();
      pager.setCurrentItem(Pages.SUBSCRIPTIONS.ordinal());
      addSubscription(uri.toString());
    } else if (intent.hasExtra(PAGE_LAUNCH_OPTION)) {
      int page = intent.getIntExtra(PAGE_LAUNCH_OPTION, Pages.PLAYLIST.ordinal());
      Log.d(TAG, "Setting page " + page);
      pager.setCurrentItem(page);
      if (intent.hasExtra(EPISODE_ID_OPTION) && playlistFragment != null) {
        playlistFragment.showEpisode(intent.getLongExtra(EPISODE_ID_OPTION, 0));
      }
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
