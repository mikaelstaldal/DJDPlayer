/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012-2013 Mikael Ståldal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nu.staldal.djdplayer;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewConfiguration;

public class MusicBrowserActivity extends Activity implements MusicUtils.Defs, ServiceConnection,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LOGTAG = "MusicBrowserActivity";

    private MusicUtils.ServiceToken mToken;

    private NowPlayingFragment nowPlayingFragment;

    private boolean invalidateTabs = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayShowTitleEnabled(false);
        if (hasMenuKey()) actionBar.setDisplayShowHomeEnabled(false);

        setupTabs();

        setContentView(R.layout.media_picker_activity);

        nowPlayingFragment = (NowPlayingFragment) getFragmentManager().findFragmentById(R.id.nowplaying);

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        mToken = MusicUtils.bindToService(this, this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SettingsActivity.SHOW_ARTISTS_TAB)
                || key.equals(SettingsActivity.SHOW_ALBUMS_TAB)
                || key.equals(SettingsActivity.SHOW_GENRES_TAB)
                || key.equals(SettingsActivity.SHOW_FOLDERS_TAB)
                || key.equals(SettingsActivity.SHOW_SONGS_TAB)
                || key.equals(SettingsActivity.SHOW_PLAYLISTS_TAB)) {
            invalidateTabs = true;
        }
    }

    private void setupTabs() {
        ActionBar actionBar = getActionBar();
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_ARTISTS_TAB, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setText(R.string.artists_menu)
                    .setTag(SettingsActivity.ARTISTS_TAB)
                    .setTabListener(new TabListener<>(this, SettingsActivity.ARTISTS_TAB, ArtistFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_ALBUMS_TAB, false)) {
            actionBar.addTab(actionBar.newTab()
                    .setText(R.string.albums_menu)
                    .setTag(SettingsActivity.ALBUMS_TAB)
                    .setTabListener(new TabListener<>(this, SettingsActivity.ALBUMS_TAB, AlbumFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_GENRES_TAB, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setText(R.string.genres_menu)
                    .setTag(SettingsActivity.GENRES_TAB)
                    .setTabListener(new TabListener<>(this, SettingsActivity.GENRES_TAB, GenreFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_FOLDERS_TAB, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setText(R.string.folders_menu)
                    .setTag(SettingsActivity.FOLDERS_TAB)
                    .setTabListener(new TabListener<>(this, SettingsActivity.FOLDERS_TAB, FolderFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_SONGS_TAB, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setText(R.string.tracks_menu)
                    .setTag(SettingsActivity.SONGS_TAB)
                    .setTabListener(new TabListener<>(this, SettingsActivity.SONGS_TAB, TrackFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_PLAYLISTS_TAB, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setText(R.string.playlists_menu)
                    .setTag(SettingsActivity.PLAYLISTS_TAB)
                    .setTabListener(new TabListener<>(this, SettingsActivity.PLAYLISTS_TAB, PlaylistFragment.class)));
        }
    }

    private boolean hasMenuKey() {
        return ViewConfiguration.get(this).hasPermanentMenuKey();
    }

    public void onServiceConnected(ComponentName name, IBinder binder) {
        MediaPlaybackService service = ((MediaPlaybackService.MediaPlaybackServiceBinder)binder).getService();

        nowPlayingFragment.onServiceConnected(service);

        invalidateOptionsMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();

        ActionBar actionBar = getActionBar();

        if (invalidateTabs) {
            actionBar.removeAllTabs();
            setupTabs();
            invalidateTabs = false;
        }

        String activeTab = MusicUtils.getStringPref(this, SettingsActivity.ACTIVE_TAB, null);
        for (int i = 0; i < actionBar.getTabCount(); i++) {
            if (actionBar.getTabAt(i).getTag().equals(activeTab)) {
                actionBar.setSelectedNavigationItem(i);
                break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ActionBar.Tab selectedTab = getActionBar().getSelectedTab();
        if (selectedTab != null) {
            MusicUtils.setStringPref(this, SettingsActivity.ACTIVE_TAB, (String) selectedTab.getTag());
        }
    }

    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MusicUtils.unbindFromService(mToken);
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, SETTINGS, 0, R.string.settings).setIcon(R.drawable.ic_menu_preferences);
        menu.add(0, SEARCH, 0, R.string.search_title).setIcon(R.drawable.ic_menu_search);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case SEARCH:
                return onSearchRequested();
        }
        return super.onOptionsItemSelected(item);
    }

    static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private Fragment mFragment;
        private final Activity mActivity;
        private final String mTag;
        private final Class<T> mClass;

        /**
         * @param activity  The host Activity, used to instantiate the fragment
         * @param tag  The identifier tag for the fragment
         * @param clz  The fragment's Class, used to instantiate the fragment
         */
        public TabListener(Activity activity, String tag, Class<T> clz) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;

            mFragment = mActivity.getFragmentManager().findFragmentByTag(mTag);
            if (mFragment != null && !mFragment.isDetached()) {
                FragmentTransaction ft = mActivity.getFragmentManager().beginTransaction();
                ft.detach(mFragment);
                ft.commit();
            }
        }

        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            // Check if the fragment is already initialized
            if (mFragment == null) {
                // If not, instantiate and add it to the activity
                mFragment = Fragment.instantiate(mActivity, mClass.getName());
                ft.add(R.id.main, mFragment, mTag);
            } else {
                // If it exists, simply attach it in order to show it
                ft.attach(mFragment);
            }
        }

        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                // Detach the fragment, because another one is being attached
                ft.detach(mFragment);
            }
        }

        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
            // User selected the already selected tab. Usually do nothing.
        }
    }
}
