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

package nu.staldal.djdplayer.mobile

import android.content.CursorLoader
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.provider.ID3Utils
import nu.staldal.djdplayer.provider.MusicContract

class GenreFragment : MetadataCategoryFragment() {

    override fun getSelectedCategoryId() = "selectedgenre"

    override fun getUnknownStringId() = R.string.unknown_genre_name

    override fun getDeleteDescStringId() = R.string.delete_genre_desc

    override fun fetchCurrentlyPlayingCategoryId() =
        if (MusicUtils.sService != null)
            MusicUtils.sService.genreId
        else
            -1L

    override fun getEntryContentType() = MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE

    override fun onCreateLoader(id: Int, args: Bundle?) = CursorLoader(
                activity,
                MusicContract.Genre.CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.AudioColumns._ID,
                    MusicContract.GenreColumns.NAME,
                    MediaStore.Audio.AudioColumns._COUNT),
                null,
                null,
                null)

    override fun fetchCategoryName(cursor: Cursor): String? =
        ID3Utils.decodeGenre(cursor.getString(getNameColumnIndex(cursor)))

    override fun getNameColumnIndex(cursor: Cursor): Int = cursor.getColumnIndexOrThrow(MusicContract.GenreColumns.NAME)

    override fun getNumberOfSongsColumnName(): String = MediaStore.Audio.AudioColumns._COUNT

    override fun fetchSongList(id: Long): LongArray {
        val cursor = MusicUtils.query(activity,
                MusicContract.Genre.getMembersUri(id),
                arrayOf(MediaStore.Audio.AudioColumns._ID), null, null, null)

        if (cursor != null) {
            val list = MusicUtils.getSongListForCursor(cursor)
            cursor.close()
            return list
        }
        return MusicUtils.sEmptyList
    }

    override fun shuffleSongs() = true

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        viewCategory(MusicContract.Genre.getMembersUri(id))
    }

    override fun pupulareContextMenu(menu: ContextMenu) {
        menu.add(0, R.id.genre_play_all_now, 0, R.string.play_all_now)
        menu.add(0, R.id.genre_play_all_next, 0, R.string.play_all_next)
        menu.add(0, R.id.genre_queue_all, 0, R.string.queue_all)
        val interleave = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.interleave_all)
        for (i in 1..5) {
            for (j in 1..5) {
                val intent = Intent()
                intent.putExtra(BrowserFragment.CURRENT_COUNT, i)
                intent.putExtra(BrowserFragment.NEW_COUNT, j)
                interleave.add(2, R.id.genre_interleave_all, 0, resources.getString(R.string.interleaveNNN, i, j)).intent = intent
            }
        }

        val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_all_to_playlist)
        MusicUtils.makePlaylistMenu(activity, sub, R.id.genre_new_playlist, R.id.genre_selected_playlist)

        menu.add(0, R.id.genre_delete_all, 0, R.string.delete_all)

        if (!mIsUnknown) {
            menu.add(0, R.id.genre_search_for_category, 0, R.string.search_for)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.genre_play_all_now -> {
                playAllNow()
                return true
            }

            R.id.genre_play_all_next -> {
                playAllNext()
                return true
            }

            R.id.genre_queue_all -> {
                queueAll()
                return true
            }

            R.id.genre_interleave_all -> {
                interleaveAll(item)
                return true
            }

            R.id.genre_new_playlist -> {
                newPlaylist()
                return true
            }

            R.id.genre_selected_playlist -> {
                selectedPlaylist(item)
                return true
            }

            R.id.genre_delete_all -> {
                deleteAll()
                return true
            }

            R.id.genre_search_for_category -> {
                searchForCategory()
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }
}
