package com.einmalfel.podlisten;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
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

  private static final int DOWNLOAD_CHECK_PERIOD = 500;
  private static final String TAG = "MAC";

  WidgetHelper widgetHelper;
  PlayerLocalConnection connection;
  private int newEpisodesNumber = 0;
  private SubscribeDialog subscribeDialog;
  private ViewPager pager;
  private ImageButton playButton;
  private ImageButton ffButton;
  private ImageButton fbButton;
  private ImageButton nextButton;
  private ImageButton optionsButton;
  private ProgressBar progressBar;
  private TextView progressBarTitle;
  private ImageView episodeImage;
  private TabLayout tabLayout;
  private TabsAdapter tabsAdapter;
  private FloatingActionButton fab;
  private Timer timer;
  private PlaylistFragment playlistFragment = null;
  private FabAction currentFabAction;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    fab = (FloatingActionButton) findViewById(R.id.fab);
    playButton = (ImageButton) findViewById(R.id.play_button);
    nextButton = (ImageButton) findViewById(R.id.next_button);
    fbButton = (ImageButton) findViewById(R.id.fb_button);
    ffButton = (ImageButton) findViewById(R.id.ff_button);
    optionsButton = (ImageButton) findViewById(R.id.play_options);
    tabLayout = (TabLayout) findViewById(R.id.tab_layout);
    pager = (ViewPager) findViewById(R.id.pager);
    fab.setOnClickListener(this);
    playButton.setOnClickListener(this);
    nextButton.setOnClickListener(this);
    optionsButton.setOnClickListener(this);
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
    pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        updateFAB(Pages.values()[position], positionOffset);
      }

      @Override
      public void onPageSelected(int position) {
        if (position < 0 || position >= Pages.values().length) {
          Log.e(TAG, "Unexpected pager position " + position, new IndexOutOfBoundsException());
        } else {
          updateFAB(Pages.values()[position], 0f);
        }
      }

      @Override
      public void onPageScrollStateChanged(int state) {}
    });

    connection = new PlayerLocalConnection(this);
  }


  enum FabAction {SORT, REFRESH, CLEAR, ADD}

  void updateFAB(int newEpisodesUpdate) {
    boolean stateChanged =
        (newEpisodesNumber == 0 && newEpisodesUpdate != 0) ||
        (newEpisodesNumber != 0 && newEpisodesUpdate == 0);
    newEpisodesNumber = newEpisodesUpdate;
    if (stateChanged && pager.getCurrentItem() == Pages.NEW_EPISODES.ordinal()) {
      updateFAB();
    }
  }

  void updateFAB() {
    updateFAB(Pages.values()[pager.getCurrentItem()], 0);
  }

  void updateFAB(Pages fragment, float positionOffset) {
    fab.show();
    FabAction newEpisodesAction = newEpisodesNumber == 0 ? FabAction.REFRESH : FabAction.CLEAR;
    switch (fragment) {
      case PLAYLIST:
        lerpFAB(FabAction.SORT, newEpisodesAction, positionOffset);
        break;
      case NEW_EPISODES:
        lerpFAB(newEpisodesAction, FabAction.ADD, positionOffset);
        break;
      case SUBSCRIPTIONS:
        lerpFAB(FabAction.ADD, FabAction.ADD, 0);
        break;
    }
  }

  private void setFabAction(@NonNull FabAction action) {
    if (currentFabAction != action) {
      switch (action) {
        case SORT:
          fab.setImageResource(R.mipmap.ic_sort_by_alpha_black_24dp);
          break;
        case REFRESH:
          fab.setImageResource(R.mipmap.ic_refresh_black_24dp);
          break;
        case ADD:
          fab.setImageResource(R.mipmap.ic_add_black_24dp);
          break;
        case CLEAR:
          fab.setImageResource(R.mipmap.ic_clear_all_black_24dp);
          break;
      }
      currentFabAction = action;
    }
  }

  void lerpFAB(@NonNull FabAction prevAction, @NonNull FabAction nextAction, float offset) {
    fab.setAlpha(0.5f + Math.abs(offset - 0.5f));
    fab.setScaleX(2f * Math.abs(offset - 0.5f));
    setFabAction(offset > 0.5f ? nextAction : prevAction);
  }

  @Override
  protected void onPause() {
    super.onPause();
    connection.unbind();
    timer.cancel();
    timer = null;
    fab.hide();
  }

  @Override
  protected void onResume() {
    super.onResume();
    connection.bind();
    timer = new Timer(true);
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        sendBroadcast(new Intent(DownloadReceiver.DOWNLOAD_HEARTBEAT_ACTION));
      }
    }, 0, DOWNLOAD_CHECK_PERIOD);
    fab.show();
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
      if (id != 0) {
        Log.e(TAG, "Playing non-existent episode " + id);
      }
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

    if (v == fab) {
      switch (currentFabAction) {
        case CLEAR:
          PodcastHelper.getInstance().clearNewEpisodes();
          break;
        case ADD:
          openSubscribeDialog(null);
          break;
        case REFRESH:
          PodlistenAccount.getInstance().refresh(0);
          break;
      }
    } else if (v == playButton) {
      connection.service.playPauseResume();
    } else if (v == nextButton) {
      connection.service.playNext();
    } else if (v == ffButton) {
      connection.service.jumpForward();
    } else if (v == fbButton) {
      connection.service.jumpBackward();
    } else if (v == episodeImage) {
      if (playlistFragment != null) {
        playlistFragment.showEpisode(connection.service.getEpisodeId(),
                                     pager.getCurrentItem() == Pages.PLAYLIST.ordinal());
        pager.setCurrentItem(Pages.PLAYLIST.ordinal());
      }
    } else if (v == optionsButton) {
      startActivity(new Intent(this, PreferencesActivity.class));
    }
  }

  @UiThread
  private void openSubscribeDialog(@Nullable Uri uri) {
    if (subscribeDialog != null) {
      subscribeDialog.dismiss();
      subscribeDialog = null;
    }
    pager.setCurrentItem(Pages.SUBSCRIPTIONS.ordinal());
    subscribeDialog = new SubscribeDialog();
    if (uri != null) {
      Bundle dialogArguments = new Bundle(1);
      dialogArguments.putString(SubscribeDialog.URL_ARG, uri.toString());
      subscribeDialog.setArguments(dialogArguments);
    }
    subscribeDialog.show(getSupportFragmentManager(), "subscribe dialog");
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.i(TAG, "Processing intent " + intent.toString());
    if (Intent.ACTION_VIEW.equals(intent.getAction())) {
      openSubscribeDialog(intent.getData());
    } else if (intent.hasExtra(PAGE_LAUNCH_OPTION)) {
      int page = intent.getIntExtra(PAGE_LAUNCH_OPTION, Pages.PLAYLIST.ordinal());
      Log.d(TAG, "Setting page " + page);
      if (intent.hasExtra(EPISODE_ID_OPTION) && playlistFragment != null) {
        playlistFragment.showEpisode(intent.getLongExtra(EPISODE_ID_OPTION, 0),
                                     pager.getCurrentItem() == Pages.PLAYLIST.ordinal());
      }
      pager.setCurrentItem(page);
    }
  }
}
