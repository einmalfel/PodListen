package com.einmalfel.podlisten;

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

  private static class TabsAdapter extends FragmentPagerAdapter {
    private static final String[] TAB_NAMES = {"Player", "Playlist", "New episodes",
        "Subscriptions"};

    TabsAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public int getCount() {
      return 4;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      return TAB_NAMES[position];
    }

    @Override
    public Fragment getItem(int position) {
      Log.v(TAG, "Making fragment for position " + position);
      return PlaylistFragment.newInstance();
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
}
