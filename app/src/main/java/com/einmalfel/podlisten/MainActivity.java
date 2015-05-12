package com.einmalfel.podlisten;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;

import com.astuetz.PagerSlidingTabStrip;


public class MainActivity extends FragmentActivity {
  private static final String TAG = "MainActivity";
  private Account account;
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
    setContentView(R.layout.activity_main);
    ViewPager pager = (ViewPager) findViewById(R.id.pager);
    pager.setAdapter(new TabsAdapter(getSupportFragmentManager()));
    PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);
    tabs.setViewPager(pager);
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
}
