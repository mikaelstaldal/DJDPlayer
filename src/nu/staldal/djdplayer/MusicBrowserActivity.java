/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012-2014 Mikael Ståldal
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

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewConfiguration;

import java.io.File;
import java.util.ArrayList;

public class MusicBrowserActivity extends Activity implements MusicUtils.Defs, ServiceConnection,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LOGTAG = "MusicBrowserActivity";

    public static final String TRACKS_FRAGMENT = "tracksFragment";

    private MusicUtils.ServiceToken token = null;
    private MediaPlaybackService service = null;

    private boolean invalidateTabs = false;
    private long songToPlay = -1;

    private ArrayList<Intent> backStack;
    private Uri uri;
    private String title;
    private boolean searchResult;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(LOGTAG, "onCreate - " + getIntent());

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        setContentView(R.layout.music_browser_activity);

        if (savedInstanceState != null) {
            backStack = savedInstanceState.getParcelableArrayList("backStack");
            String uriString = savedInstanceState.getString("uri");
            uri = uriString != null ? Uri.parse(uriString) : null;
            title = savedInstanceState.getString("title");
            searchResult = savedInstanceState.getBoolean("searchResult");
        } else {
            backStack = new ArrayList<>(8);
            parseIntent(getIntent(), true);
        }

        token = MusicUtils.bindToService(this, this);
    }

    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((MediaPlaybackService.MediaPlaybackServiceBinder)binder).getService();

        if (songToPlay > -1) {
            MusicUtils.playSong(this, songToPlay);
        }

        NowPlayingFragment nowPlayingFragment = (NowPlayingFragment) getFragmentManager().findFragmentById(R.id.nowplaying);
        nowPlayingFragment.onServiceConnected(service);

        invalidateOptionsMenu();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(LOGTAG, "onNewIntent - " + intent);
        parseIntent(intent, false);
        if (songToPlay > -1) {
            if (service != null) {
                MusicUtils.playSong(this, songToPlay);
            }
        } else {
            backStack.add(getIntent());
            setIntent(intent);
        }
    }

    private void parseIntent(Intent intent, boolean onCreate) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getData() != null
                && intent.getData().toString().startsWith(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString())
                && MusicUtils.isLong(intent.getData().getLastPathSegment())) {
            songToPlay = ContentUris.parseId(intent.getData());
            if (!onCreate) return;
            uri = null;
            title = null;
            searchResult = false;
        } else if ((Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_PICK.equals(intent.getAction())) && intent.getData() != null) {
            songToPlay = -1;
            uri = fixUri(intent.getData());
            title = calcTitle(uri);
            searchResult = false;
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())
                            || MediaStore.INTENT_ACTION_MEDIA_SEARCH.equals(intent.getAction())) {
            songToPlay = -1;
            uri = null;
            title = getString(R.string.search_results, intent.getStringExtra(SearchManager.QUERY));
            searchResult = true;
        } else {
            songToPlay = -1;
            uri = null;
            title = null;
            searchResult = false;
        }

        if (title == null) {
            enterCategoryMode();
        } else {
            enterSongsMode();
        }
    }

    private Uri fixUri(Uri uri) {
        String lastPathSegment = uri.getLastPathSegment();
        if (MusicUtils.isLong(lastPathSegment)) {
            long id = Long.parseLong(lastPathSegment);
            if (uri.toString().startsWith(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Album.getAlbumUri(id);
            } else if (uri.toString().startsWith(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Artist.getArtistUri(id);
            } else if (uri.toString().startsWith(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Genre.getGenreUri(id);
            } else if (uri.toString().startsWith(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Playlist.getPlaylistUri(id);
            } else {
                return uri;
            }
        } else {
            return uri;
        }
    }

    private String calcTitle(Uri uri) {
        switch (MusicProvider.sURIMatcher.match(uri)) {
            case MusicProvider.FOLDER_MEMBERS:
                File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                return uri.getLastPathSegment().substring(root.getAbsolutePath().length() + 1);

            case MusicProvider.PLAYLIST_MEMBERS: {
                if (ContentUris.parseId(uri) == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
                    return getString(R.string.recentlyadded_title);
                } else {
                    String[] cols = new String[]{
                            MediaStore.Audio.Playlists.NAME
                    };
                    Cursor cursor = MusicUtils.query(this,
                            ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                            cols, null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.getCount() != 0) {
                                cursor.moveToFirst();
                                return cursor.getString(0);
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
                return getString(R.string.unknown_playlist_name);
            }
            case MusicProvider.GENRE_MEMBERS: {
                String[] cols = new String[]{
                        MediaStore.Audio.Genres.NAME
                };
                Cursor cursor = MusicUtils.query(this,
                        ContentUris.withAppendedId(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                        cols, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.getCount() != 0) {
                            cursor.moveToFirst();
                            return ID3Utils.decodeGenre(cursor.getString(0));
                        }
                    } finally {
                        cursor.close();
                    }
                }
                return getString(R.string.unknown_genre_name);
            }
            case MusicProvider.ARTIST_MEMBERS: {
                String[] cols = new String[]{
                        MediaStore.Audio.Artists.ARTIST
                };
                Cursor cursor = MusicUtils.query(this,
                        ContentUris.withAppendedId(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                        cols, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.getCount() != 0) {
                            cursor.moveToFirst();
                            return cursor.getString(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }
                return getString(R.string.unknown_artist_name);
            }
            case MusicProvider.ALBUM_MEMBERS: {
                String fancyName = null;
                String[] cols = new String[]{
                        MediaStore.Audio.Albums.ALBUM
                };
                Cursor cursor = MusicUtils.query(this,
                        ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                        cols, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.getCount() != 0) {
                            cursor.moveToFirst();
                            fancyName = cursor.getString(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }
                if (fancyName == null || fancyName.equals(MediaStore.UNKNOWN_STRING)) {
                    return getString(R.string.unknown_album_name);
                } else {
                    return fancyName;
                }
            }

            case MusicProvider.MUSIC_MEMBERS:
                return getString(R.string.tracks_title);

            default:
                return getString(R.string.tracks_title);
        }
    }

    private void enterCategoryMode() {
        uri = null;
        title = null;

        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setHomeButtonEnabled(false);
        if (hasMenuKey()) actionBar.setDisplayShowHomeEnabled(false);
        setTitle(null);

        Fragment oldFragment = getFragmentManager().findFragmentByTag(TRACKS_FRAGMENT);
        if (oldFragment != null) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.remove(oldFragment);
            ft.commit();
        }
        setupTabs(actionBar);

        restoreActiveTab(actionBar);
    }

    private void enterSongsMode() {
        ActionBar actionBar = getActionBar();
        saveActiveTab(actionBar);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        setTitle(title);

        Fragment fragment;
        if (searchResult) {
            fragment = Fragment.instantiate(this, QueryFragment.class.getName());
        } else {
            Bundle bundle = new Bundle();
            bundle.putString(TrackFragment.URI, uri.toString());
            fragment = Fragment.instantiate(this, TrackFragment.class.getName(), bundle);
        }
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.main, fragment, TRACKS_FRAGMENT);
        ft.commit();
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

    private void setupTabs(ActionBar actionBar) {
        actionBar.removeAllTabs();

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

    @Override
    protected void onResume() {
        super.onResume();

        if (title == null) {
            ActionBar actionBar = getActionBar();

            if (invalidateTabs) {
                setupTabs(actionBar);
                invalidateTabs = false;
            }

            restoreActiveTab(actionBar);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        saveActiveTab(getActionBar());
    }

    private void restoreActiveTab(ActionBar actionBar) {
        String activeTab = MusicUtils.getStringPref(this, SettingsActivity.ACTIVE_TAB, null);
        for (int i = 0; i < actionBar.getTabCount(); i++) {
            if (actionBar.getTabAt(i).getTag().equals(activeTab)) {
                actionBar.setSelectedNavigationItem(i);
                break;
            }
        }
    }

    private void saveActiveTab(ActionBar actionBar) {
        ActionBar.Tab selectedTab = actionBar.getSelectedTab();
        if (selectedTab != null) {
            MusicUtils.setStringPref(this, SettingsActivity.ACTIVE_TAB, (String) selectedTab.getTag());
        }
    }

    public void onServiceDisconnected(ComponentName name) {
        service = null;
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("backStack", backStack);
        outState.putString("uri", uri != null ? uri.toString() : null);
        outState.putString("title", title);
        outState.putBoolean("searchResult", searchResult);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (token != null) {
            MusicUtils.unbindFromService(token);
        }
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
            case android.R.id.home:
                backStack.clear();
                parseIntent(new Intent(Intent.ACTION_MAIN), false);
                return true;

            case SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case SEARCH:
                return onSearchRequested();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!backStack.isEmpty()) {
            Intent intent = backStack.remove(backStack.size() - 1);
            parseIntent(intent, false);
            setIntent(intent);
        } else {
            super.onBackPressed();
        }
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
