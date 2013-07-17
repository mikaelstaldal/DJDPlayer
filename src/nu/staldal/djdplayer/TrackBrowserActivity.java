/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2013 Mikael Ståldal
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

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;

public class TrackBrowserActivity extends Activity implements MusicUtils.Defs, ServiceConnection {
    private static final String LOGTAG = "TrackBrowserActivity";

    private MusicUtils.ServiceToken mToken;

    private NowPlayingFragment nowPlayingFragment;

    private String title;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (savedInstanceState != null) {
            title = savedInstanceState.getString("title");
        } else {
            title = calcTitle(getIntent());
        }

        getActionBar().setHomeButtonEnabled(true);
        setTitle(title);

        setContentView(R.layout.track_browser_activity);

        nowPlayingFragment = (NowPlayingFragment) getFragmentManager().findFragmentById(R.id.nowplaying);

        mToken = MusicUtils.bindToService(this, this);
    }


    private String calcTitle(Intent intent) {
        long mAlbumId = MusicUtils.parseLong(intent.getStringExtra("album"));
        long mArtistId = MusicUtils.parseLong(intent.getStringExtra("artist"));
        long mPlaylist = MusicUtils.parseLong(intent.getStringExtra(PlaylistFragment.CATEGORY_ID));
        long mGenreId = MusicUtils.parseLong(intent.getStringExtra("genre"));
        String mFolder = intent.getStringExtra(FolderFragment.CATEGORY_ID);

        String fancyName = null;
        if (mAlbumId != -1) {
            String [] cols = new String [] {
                MediaStore.Audio.Albums.ALBUM
            };
            Cursor cursor = MusicUtils.query(this,
                    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, mAlbumId),
                    cols, null, null, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    cursor.moveToFirst();
                    fancyName = cursor.getString(0);
                }
                cursor.close();
            }
            if (fancyName == null || fancyName.equals(MediaStore.UNKNOWN_STRING)) {
                fancyName = getString(R.string.unknown_album_name);
            }
        } else if (mArtistId != -1) {
            String [] cols = new String [] {
                MediaStore.Audio.Artists.ARTIST
            };
            Cursor cursor = MusicUtils.query(this,
                    ContentUris.withAppendedId(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, mArtistId),
                    cols, null, null, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    cursor.moveToFirst();
                    fancyName = cursor.getString(0);
                }
                cursor.close();
            }
        } else if (mPlaylist != -1) {
            if (mPlaylist == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
                fancyName = getString(R.string.recentlyadded_title);
            } else {
                String [] cols = new String [] {
                    MediaStore.Audio.Playlists.NAME
                };
                Cursor cursor = MusicUtils.query(this,
                        ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, mPlaylist),
                        cols, null, null, null);
                if (cursor != null) {
                    if (cursor.getCount() != 0) {
                        cursor.moveToFirst();
                        fancyName = cursor.getString(0);
                    }
                    cursor.close();
                }
            }
        } else if (mGenreId != -1) {
            String [] cols = new String [] {
                MediaStore.Audio.Genres.NAME
            };
            Cursor cursor = MusicUtils.query(this,
                    ContentUris.withAppendedId(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, mGenreId),
                    cols, null, null, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    cursor.moveToFirst();
                    fancyName = ID3Utils.decodeGenre(cursor.getString(0));
                }
                cursor.close();
            }
        } else if (mFolder != null) {
            File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            fancyName = mFolder.substring(root.getAbsolutePath().length() + 1);
        }

        if (fancyName != null) {
            return fancyName;
        } else {
            return getString(R.string.tracks_title);
        }
    }

    public void onServiceConnected(ComponentName name, IBinder binder) {
        MediaPlaybackService service = ((MediaPlaybackService.MediaPlaybackServiceBinder)binder).getService();

        nowPlayingFragment.onServiceConnected(service);
    }

    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("title", title);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MusicUtils.unbindFromService(mToken);
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
        Log.i(LOGTAG, "onOptionsItemSelected: " + item.getItemId());
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, MusicBrowserActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;

            case SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case SEARCH:
                return onSearchRequested();
        }
        return super.onOptionsItemSelected(item);
    }

}
