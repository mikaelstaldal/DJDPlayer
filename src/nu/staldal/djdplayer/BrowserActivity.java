/*
 * Copyright (C) 2012 Mikael Ståldal
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
import android.app.ListActivity;
import android.content.*;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TabWidget;
import android.widget.TextView;

public abstract class BrowserActivity extends ListActivity
        implements View.OnCreateContextMenuListener, MusicUtils.Defs, ServiceConnection {
    private static final String TAG = "BrowserActivity";

    private static int sActiveTabIndex = -1;

    protected MusicUtils.ServiceToken mToken;

    private View nowPlayingView;
    private TextView titleView;
    private TextView artistView;
    private ImageButton playButton;


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        if (getIntent().getBooleanExtra("withtabs", false)) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.media_picker_activity);
    }

    protected boolean updateButtonBar(int tabId) {
        final TabWidget ll = (TabWidget) findViewById(R.id.buttonbar);
        boolean withtabs = false;
        Intent intent = getIntent();
        if (intent != null) {
            withtabs = intent.getBooleanExtra("withtabs", false);
        }

        if (!withtabs) {
            ll.setVisibility(View.GONE);
            return withtabs;
        } else {
            ll.setVisibility(View.VISIBLE);
        }

        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_ARTISTS_TAB, true)) {
            findViewById(R.id.artisttab).setVisibility(View.GONE);
        }
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_ALBUMS_TAB, true)) {
            findViewById(R.id.albumtab).setVisibility(View.GONE);
        }
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_GENRES_TAB, true)) {
            findViewById(R.id.genretab).setVisibility(View.GONE);
        }
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_FOLDERS_TAB, true)) {
            findViewById(R.id.foldertab).setVisibility(View.GONE);
        }
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_SONGS_TAB, true)) {
            findViewById(R.id.songtab).setVisibility(View.GONE);
        }
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.SHOW_PLAYLISTS_TAB, true)) {
            findViewById(R.id.playlisttab).setVisibility(View.GONE);
        }

        for (int i = ll.getChildCount() - 1; i >= 0; i--) {
            View v = ll.getChildAt(i);
            boolean isActive = (v.getId() == tabId);
            if (isActive) {
                ll.setCurrentTab(i);
                sActiveTabIndex = i;
            }
            v.setTag(i);
            v.setOnFocusChangeListener(new View.OnFocusChangeListener() {

                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        for (int i = 0; i < ll.getTabCount(); i++) {
                            if (ll.getChildTabViewAt(i) == v) {
                                ll.setCurrentTab(i);
                                processTabClick((Activity)ll.getContext(), v, ll.getChildAt(sActiveTabIndex).getId());
                                break;
                            }
                        }
                    }
                }});

            v.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    processTabClick((Activity)ll.getContext(), v, ll.getChildAt(sActiveTabIndex).getId());
                }});
        }
        return withtabs;
    }

    protected void processTabClick(Activity a, View v, int current) {
        int id = v.getId();
        if (id == current) {
            return;
        }

        final TabWidget ll = (TabWidget) a.findViewById(R.id.buttonbar);

        MusicUtils.activateTab(a, id);
        if (id != R.id.nowplayingtab) {
            ll.setCurrentTab((Integer) v.getTag());
            MusicUtils.setIntPref(a, "activetab", id);
        }
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        nowPlayingView = findViewById(R.id.nowplaying);
        if (nowPlayingView != null) {
            titleView = (TextView) nowPlayingView.findViewById(R.id.title);
            artistView = (TextView) nowPlayingView.findViewById(R.id.artist);
            playButton = (ImageButton) nowPlayingView.findViewById(R.id.control_play);
            ImageButton prevButton = (ImageButton) nowPlayingView.findViewById(R.id.control_prev);
            ImageButton nextButton = (ImageButton) nowPlayingView.findViewById(R.id.control_next);

            nowPlayingView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Context c = v.getContext();
                    c.startActivity(new Intent(c, MediaPlaybackActivity.class));
                }
            });

            playButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (MusicUtils.isPlaying()) {
                        MusicUtils.pause();
                    } else {
                        MusicUtils.play();
                    }
                }
            });

            if (prevButton != null) {
                prevButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        MusicUtils.prev();
                    }
                });
            }

            if (nextButton != null) {
                nextButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        MusicUtils.next();
                    }
                });
            }
        }
        updateNowPlaying();
    }

    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        registerReceiver(mStatusListener, new IntentFilter(f));
    }

    @Override
    public void onPause() {
        unregisterReceiver(mStatusListener);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, SETTINGS, 0, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(0, SEARCH, 0, R.string.search_title).setIcon(android.R.drawable.ic_menu_search);

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

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                if (nowPlayingView != null) {
                    if (MusicUtils.isPlaying()) {
                        playButton.setImageResource(R.drawable.music_pause);
                    } else {
                        playButton.setImageResource(R.drawable.music_play);
                    }
                }
            } else if (intent.getAction().equals(MediaPlaybackService.META_CHANGED)) {
                updateNowPlaying();
            } else if (intent.getAction().equals(MediaPlaybackService.QUEUE_CHANGED)) {
                updateNowPlaying();
            }
        }
    };

    private void updateNowPlaying() {
        if (nowPlayingView != null) {
            if (MusicUtils.sService != null && MusicUtils.sService.getAudioId() != -1) {
                titleView.setText(MusicUtils.sService.getTrackName());
                String artistName = MusicUtils.sService.getArtistName();
                if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                    artistName = getString(R.string.unknown_artist_name);
                }
                artistView.setText(artistName);
                nowPlayingView.setVisibility(View.VISIBLE);
            }
        }
    }
}
