/*
 * Copyright (C) 2012-2017 Mikael Ståldal
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
package nu.staldal.djdplayer

import android.app.Activity
import android.os.Bundle

class SettingsActivity : Activity() {

    companion object {
        const val CLICK_ON_SONG = "clickonsong"
        const val SHOW_ARTISTS_TAB = "show_artists_tab"
        const val SHOW_ALBUMS_TAB = "show_albums_tab"
        const val SHOW_GENRES_TAB = "show_genres_tab"
        const val SHOW_FOLDERS_TAB = "show_folders_tab"
        const val SHOW_PLAYLISTS_TAB = "show_playlists_tab"
        const val MUSIC_FOLDER = "music_folder"
        const val FADE_SECONDS = "fade_seconds"
        const val CROSS_FADE = "cross_fade"

        const val PLAYQUEUE = "queue"
        const val CARDID = "cardid"
        const val CURPOS = "curpos"
        const val SEEKPOS = "seekpos"
        const val REPEATMODE = "repeatmode"
        const val NUMWEEKS = "numweeks"
        const val ACTIVE_TAB = "ActiveTab"

        // CLICK_ON_SONG values
        const val PLAY_NEXT = "PLAY_NEXT"
        const val PLAY_NOW = "PLAY_NOW"
        const val QUEUE = "QUEUE"
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.settings)
        fragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
    }

}