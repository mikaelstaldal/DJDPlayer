/*
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

import android.app.Activity;
import android.os.Bundle;

public class SettingsActivity extends Activity {
    public static final String CLICK_ON_SONG = "clickonsong";
    public static final String SHOW_ARTISTS_TAB = "show_artists_tab";
    public static final String SHOW_ALBUMS_TAB = "show_albums_tab";
    public static final String SHOW_GENRES_TAB = "show_genres_tab";
    public static final String SHOW_FOLDERS_TAB = "show_folders_tab";
    public static final String SHOW_PLAYLISTS_TAB = "show_playlists_tab";
    public static final String MUSIC_FOLDER = "music_folder";
    public static final String FADE_OUT_SECONDS = "fade_out_seconds";
    public static final String PAUSE_SECONDS = "pause_seconds";
    public static final String FADE_IN_SECONDS = "fade_in_seconds";

    public static final String PLAYQUEUE = "queue";
    public static final String CARDID = "cardid";
    public static final String CURPOS = "curpos";
    public static final String SEEKPOS = "seekpos";
    public static final String REPEATMODE = "repeatmode";
    public static final String NUMWEEKS = "numweeks";
    public static final String ACTIVE_TAB = "ActiveTab";

    // CLICK_ON_SONG values
    public static final String PLAY_NEXT = "PLAY_NEXT";
    public static final String PLAY_NOW = "PLAY_NOW";
    public static final String QUEUE = "QUEUE";

    // ACTIVE_TAB values
    public static final String ARTISTS_TAB = "artists";
    public static final String ALBUMS_TAB = "albums";
    public static final String GENRES_TAB = "genres";
    public static final String FOLDERS_TAB = "folders";
    public static final String PLAYLISTS_TAB = "playlists";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.settings);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
}