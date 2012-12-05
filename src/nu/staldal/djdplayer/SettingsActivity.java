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

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity {
    public static final String SHOW_ARTISTS_TAB = "show_artists_tab";
    public static final String SHOW_ALBUMS_TAB = "show_albums_tab";
    public static final String SHOW_GENRES_TAB = "show_genres_tab";
    public static final String SHOW_FOLDERS_TAB = "show_folders_tab";
    public static final String SHOW_SONGS_TAB = "show_songs_tab";
    public static final String SHOW_PLAYLISTS_TAB = "show_playlists_tab";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        setTitle(R.string.settings);
    }
}