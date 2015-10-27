/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012-2015 Mikael Ståldal
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

package nu.staldal.djdplayer.tv;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import nu.staldal.djdplayer.CreatePlaylist;
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.ImportPlaylistTask;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.QueryFragment;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.SettingsActivity;
import nu.staldal.djdplayer.TrackFragment;
import nu.staldal.djdplayer.provider.MusicContract;
import nu.staldal.djdplayer.provider.MusicProvider;
import nu.staldal.ui.WithSectionMenu;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BrowserActivity extends Activity implements MusicUtils.Defs, ServiceConnection {

    private static final String LOGTAG = "BrowserActivity";

    private static final String TRACKS_FRAGMENT = "tracksFragment";

    private ImageView categoryMenuView;

    private MusicUtils.ServiceToken token = null;
    private MediaPlayback service = null;

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

        setContentView(R.layout.tv_browser_activity);

        if (savedInstanceState != null) {
            backStack = savedInstanceState.getParcelableArrayList("backStack");
        } else {
            backStack = new ArrayList<>(8);
        }

        parseIntent(getIntent(), true);

        token = MusicUtils.bindToService(this, this);
    }

    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((MediaPlaybackService.LocalBinder)binder).getService();

        notifyFragmentConnected(R.id.browser_fragment, service);

        if (songToPlay > -1) {
            MusicUtils.playSong(this, songToPlay);
            songToPlay = -1;
        }

        invalidateOptionsMenu();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(LOGTAG, "onNewIntent - " + intent);
        boolean addToBackStack = parseIntent(intent, false);
        if (songToPlay > -1) {
            if (service != null) {
                MusicUtils.playSong(this, songToPlay);
                songToPlay = -1;
            }
        }
        if (addToBackStack) {
            backStack.add(getIntent());
            setIntent(intent);
        }
    }

    private boolean parseIntent(Intent intent, boolean onCreate) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getData() != null
                && intent.getType() != null && intent.getType().startsWith(MusicUtils.AUDIO_X_MPEGURL)) {
            new ImportPlaylistTask(getApplicationContext()).execute(intent.getData());
            songToPlay = -1;
            uri = null;
            title = null;
            searchResult = false;
            MusicUtils.setStringPref(this, SettingsActivity.ACTIVE_TAB, SettingsActivity.PLAYLISTS_TAB);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getData() != null
                && intent.getData().toString().startsWith(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString())
                && MusicUtils.isLong(intent.getData().getLastPathSegment())) {
            songToPlay = ContentUris.parseId(intent.getData());
            if (!onCreate) return false;
            uri = null;
            title = null;
            searchResult = false;
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getData() != null
                && intent.getData().getScheme().equals("file")) {
            songToPlay = fetchSongIdFromPath(intent.getData().getPath());
            if (!onCreate) return false;
            uri = null;
            title = null;
            searchResult = false;
        } else if ((Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_PICK.equals(intent.getAction()))
                && intent.getData() != null) {
            songToPlay = -1;
            uri = fixUri(intent.getData());
            title = MusicProvider.calcTitle(this, uri);
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

        return true;
    }

    private long fetchSongIdFromPath(String path) {
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Media._ID },
                MediaStore.Audio.Media.DATA + "=?",
                new String[] { path },
                null);
        if (cursor == null) {
            Log.w(LOGTAG, "Unable to fetch Song Id (cursor is null) for " + path);
            return -1;
        }
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
            } else {
                Log.w(LOGTAG, "Unable to fetch Song Id (cursor is empty) for " + path);
                return -1;
            }
        } finally {
            cursor.close();
        }
    }

    private Uri fixUri(Uri uri) {
        String lastPathSegment = uri.getLastPathSegment();
        try {
            long id = Long.parseLong(lastPathSegment);
            if (uri.toString().startsWith(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Album.getMembersUri(id);
            } else if (uri.toString().startsWith(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Artist.getMembersUri(id);
            } else if (uri.toString().startsWith(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Genre.getMembersUri(id);
            } else if (uri.toString().startsWith(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Playlist.getMembersUri(id);
            } else {
                return uri;
            }
        } catch (NumberFormatException e) {
            return uri;
        }
    }

    private void enterCategoryMode() {
        uri = null;
        title = null;

        Fragment oldFragment = getFragmentManager().findFragmentByTag(TRACKS_FRAGMENT);
        if (oldFragment != null) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.remove(oldFragment);
            ft.commit();
        }
    }

    private void enterSongsMode() {
        Fragment fragment;
        if (searchResult) {
            fragment = Fragment.instantiate(this, QueryFragment.class.getName());
        } else {
            Bundle bundle = new Bundle();
            bundle.putString(TrackFragment.URI, uri.toString());
            fragment = Fragment.instantiate(this, TrackFragment.class.getName(), bundle);
            final WithSectionMenu trackFragment = (WithSectionMenu)fragment;

            categoryMenuView = new ImageView(this);
            categoryMenuView.setImageResource(R.drawable.ic_section_menu);
            categoryMenuView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    trackFragment.onCreateSectionMenu(categoryMenuView);
                }
            });
        }
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.main, fragment, TRACKS_FRAGMENT);
        ft.commit();
    }

    public void onServiceDisconnected(ComponentName name) {
        service = null;

        notifyFragmentDisconnected(R.id.browser_fragment);

        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelableArrayList("backStack", backStack);
    }

    @Override
    protected void onDestroy() {
        if (token != null) MusicUtils.unbindFromService(token);
        service = null;

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.browser_menu, menu);

        if (getResources().getBoolean(R.bool.tablet_layout)) {
            SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, 16, R.string.add_all_to_playlist);
            MusicUtils.makePlaylistMenu(this, sub, MusicUtils.Defs.NEW_PLAYLIST4, MusicUtils.Defs.PLAYLIST_SELECTED4);
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateSoundEffectItem(menu);

        updateRepeatItem(menu);

        updatePlayingItems(menu);

        return true;
    }

    private void updateSoundEffectItem(Menu menu) {
        MenuItem item = menu.findItem(R.id.effect_panel);
        if (item != null) {
            Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
            item.setVisible(getPackageManager().resolveActivity(intent, 0) != null);
        }
    }

    private void updateRepeatItem(Menu menu) {
        MenuItem item = menu.findItem(R.id.repeat);
        if (item != null) {
            if (service != null) {
                switch (service.getRepeatMode()) {
                    case MediaPlayback.REPEAT_ALL:
                        item.setIcon(R.drawable.ic_mp_repeat_all_btn);
                        break;
                    case MediaPlayback.REPEAT_CURRENT:
                        item.setIcon(R.drawable.ic_mp_repeat_once_btn);
                        break;
                    case MediaPlayback.REPEAT_STOPAFTER:
                        item.setIcon(R.drawable.ic_mp_repeat_stopafter_btn);
                        break;
                    default:
                        item.setIcon(R.drawable.ic_mp_repeat_off_btn);
                        break;
                }
            } else {
                item.setIcon(R.drawable.ic_mp_repeat_off_btn);
            }
        }
    }

    private void updatePlayingItems(Menu menu) {
        menu.setGroupVisible(R.id.playing_items, service != null && !service.isPlaying());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                backStack.clear();
                Intent intent = new Intent(Intent.ACTION_MAIN);
                parseIntent(intent, false);
                setIntent(intent);
                return true;
            }

            case R.id.repeat:
                cycleRepeat();
                return true;

            case R.id.shuffle:
                if (service != null) service.doShuffle();
                return true;

            case R.id.uniqueify:
                if (service != null) service.uniqueify();
                return true;

            case R.id.clear_queue:
                if (service != null) service.removeTracks(0, Integer.MAX_VALUE);
                return true;

            case NEW_PLAYLIST4:
                if (service != null) CreatePlaylist.showMe(this, service.getQueue());
                return true;

            case PLAYLIST_SELECTED4:
                if (service != null) {
                    long playlist = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(this, service.getQueue(), playlist);
                }
                return true;

            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.search:
                return onSearchRequested();

            case R.id.effect_panel:
                Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, service.getAudioSessionId());
                startActivityForResult(intent, 0);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void cycleRepeat() {
        if (service == null) {
            return;
        }
        int mode = service.getRepeatMode();
        if (mode == MediaPlayback.REPEAT_NONE) {
            service.setRepeatMode(MediaPlayback.REPEAT_ALL);
            Toast.makeText(this, R.string.repeat_all_notif, Toast.LENGTH_SHORT).show();
        } else if (mode == MediaPlayback.REPEAT_ALL) {
            service.setRepeatMode(MediaPlayback.REPEAT_CURRENT);
            Toast.makeText(this, R.string.repeat_current_notif, Toast.LENGTH_SHORT).show();
        } else if (mode == MediaPlayback.REPEAT_CURRENT) {
            service.setRepeatMode(MediaPlayback.REPEAT_STOPAFTER);
            Toast.makeText(this, R.string.repeat_stopafter_notif, Toast.LENGTH_SHORT).show();
        } else {
            service.setRepeatMode(MediaPlayback.REPEAT_NONE);
            Toast.makeText(this, R.string.repeat_off_notif, Toast.LENGTH_SHORT).show();
        }
        invalidateOptionsMenu();
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

    private void notifyFragmentConnected(int id, MediaPlayback service) {
        Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment != null && fragment.isInLayout()) {
            ((FragmentServiceConnection) fragment).onServiceConnected(service);
        }
    }

    private void notifyFragmentDisconnected(int id) {
        Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment != null && fragment.isInLayout()) {
            ((FragmentServiceConnection) fragment).onServiceDisconnected();
        }
    }

}
