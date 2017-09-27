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
import android.content.Loader
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
import nu.staldal.djdplayer.provider.MusicContract

class ArtistFragment : MetadataCategoryFragment() {

    override fun getSelectedCategoryId() = "selectedartist"

    override fun getUnknownStringId() = R.string.unknown_artist_name

    override fun getDeleteDescStringId() = R.string.delete_artist_desc

    override fun fetchCurrentlyPlayingCategoryId() = MusicUtils.sService?.artistId ?: -1L

    override fun getEntryContentType() = MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE

    override fun addExtraSearchData(i: Intent) {
        i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, mCurrentName)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> =
        CursorLoader(
                activity,
                MusicContract.Artist.CONTENT_URI,
                arrayOf(
                        MediaStore.Audio.AudioColumns._ID,
                        MusicContract.ArtistColumns.NAME,
                        MusicContract.ArtistColumns.COUNT),
                null,
                null,
                null)

    override fun fetchCategoryName(cursor: Cursor) = cursor.getString(getNameColumnIndex(cursor))

    override fun getNameColumnIndex(cursor: Cursor) = cursor.getColumnIndexOrThrow(MusicContract.ArtistColumns.NAME)

    override fun getNumberOfSongsColumnName() = MusicContract.ArtistColumns.COUNT

    override fun fetchSongList(id: Long): LongArray =
        MusicUtils.query(activity,
                MusicContract.Artist.getMembersUri(id),
                arrayOf(MediaStore.Audio.AudioColumns._ID), null, null, null).use { cursor ->
            MusicUtils.getSongListForCursor(cursor)
        }

    override fun shuffleSongs() = true

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        viewCategory(MusicContract.Artist.getMembersUri(id))
    }

    override fun pupulareContextMenu(menu: ContextMenu) {
        menu.add(0, R.id.artist_play_all_now, 0, R.string.play_all_now)
        menu.add(0, R.id.artist_play_all_next, 0, R.string.play_all_next)
        menu.add(0, R.id.artist_queue_all, 0, R.string.queue_all)
        val interleave = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.interleave_all)
        for (i in 1..5) {
            for (j in 1..5) {
                val intent = Intent()
                intent.putExtra(BrowserFragment.CURRENT_COUNT, i)
                intent.putExtra(BrowserFragment.NEW_COUNT, j)
                interleave.add(2, R.id.artist_interleave_all, 0, resources.getString(R.string.interleaveNNN, i, j)).intent = intent
            }
        }

        val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_all_to_playlist)
        MusicUtils.makePlaylistMenu(activity, sub, R.id.artist_new_playlist, R.id.artist_selected_playlist)

        menu.add(0, R.id.artist_delete_all, 0, R.string.delete_all)

        if (!mIsUnknown) {
            menu.add(0, R.id.artist_search_for_category, 0, R.string.search_for)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.artist_play_all_now -> {
                playAllNow()
                return true
            }

            R.id.artist_play_all_next -> {
                playAllNext()
                return true
            }

            R.id.artist_queue_all -> {
                queueAll()
                return true
            }

            R.id.artist_interleave_all -> {
                interleaveAll(item)
                return true
            }

            R.id.artist_new_playlist -> {
                newPlaylist()
                return true
            }

            R.id.artist_selected_playlist -> {
                selectedPlaylist(item)
                return true
            }

            R.id.artist_delete_all -> {
                deleteAll()
                return true
            }

            R.id.artist_search_for_category -> {
                searchForCategory()
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }
}
