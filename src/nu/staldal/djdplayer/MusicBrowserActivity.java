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
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

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

        if (savedInstanceState != null) {
            actionBar.setSelectedNavigationItem(savedInstanceState.getInt("NavigationIndex", 0));
        }

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
                    .setCustomView(buildTabView(R.drawable.ic_tab_artists, R.string.artists_menu))
                    .setTabListener(new TabListener<ArtistFragment>(this, "artist", ArtistFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_ALBUMS_TAB, false)) {
            actionBar.addTab(actionBar.newTab()
                    .setCustomView(buildTabView(R.drawable.ic_tab_albums, R.string.albums_menu))
                    .setTabListener(new TabListener<AlbumFragment>(this, "album", AlbumFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_GENRES_TAB, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setCustomView(buildTabView(R.drawable.ic_tab_genres, R.string.genres_menu))
                    .setTabListener(new TabListener<GenreFragment>(this, "genre", GenreFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_FOLDERS_TAB, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setCustomView(buildTabView(R.drawable.ic_tab_folders, R.string.folders_menu))
                    .setTabListener(new TabListener<FolderFragment>(this, "folder", FolderFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_SONGS_TAB, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setCustomView(buildTabView(R.drawable.ic_tab_songs, R.string.tracks_menu))
                    .setTabListener(new TabListener<TrackFragment>(this, "track", TrackFragment.class)));
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_PLAYLISTS_TAB, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setCustomView(buildTabView(R.drawable.ic_tab_playlists, R.string.playlists_menu))
                    .setTabListener(new TabListener<PlaylistFragment>(this, "playlist", PlaylistFragment.class)));
        }
    }

    private View buildTabView(int icon, int label) {
        TextView textView = new TextView(this);
        textView.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
        textView.setText(label);
        return textView;
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
        if (invalidateTabs) {
            getActionBar().removeAllTabs();
            setupTabs();
            invalidateTabs = false;
        }
    }

    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("NavigationIndex", getActionBar().getSelectedNavigationIndex());
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
