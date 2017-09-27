/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.database.CharArrayBuffer
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AlphabetIndexer
import android.widget.CursorAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.SectionIndexer
import android.widget.TextView
import nu.staldal.djdplayer.ExportPlaylistTask
import nu.staldal.djdplayer.MusicAlphabetIndexer
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.SettingsActivity
import nu.staldal.djdplayer.ShufflePlaylistTask
import nu.staldal.djdplayer.provider.MusicContract
import nu.staldal.ui.TouchInterceptor
import nu.staldal.ui.WithSectionMenu
import java.util.Arrays
import java.util.HashSet

private const val LOGTAG = "TrackFragment"

open class TrackFragment : BrowserFragment(), PopupMenu.OnMenuItemClickListener, WithSectionMenu {

    companion object {
        const val URI = "uri"
    }

    internal var selectedPosition: Int = 0
    internal var selectedId: Long = 0

    private var uri: Uri? = null
    private var isPlaylist: Boolean = false
    private var playlist: Long = 0
    private var isAlbum: Boolean = false
    private var isMedadataCategory: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            selectedPosition = savedInstanceState.getInt("selectedposition")
            selectedId = savedInstanceState.getLong("selectedtrack")
        }

        val uriString = if (arguments != null) arguments.getString(URI) else null
        if (uriString != null) {
            uri = Uri.parse(uriString)
            isPlaylist = uriString.startsWith(MusicContract.Playlist.CONTENT_URI.toString())
            playlist = if (isPlaylist) ContentUris.parseId(uri) else -1
            isAlbum = uriString.startsWith(MusicContract.Album.CONTENT_URI.toString())
            isMedadataCategory = uriString.startsWith(MusicContract.Artist.CONTENT_URI.toString())
                    || uriString.startsWith(MusicContract.Album.CONTENT_URI.toString())
                    || uriString.startsWith(MusicContract.Genre.CONTENT_URI.toString())
        } else {
            uri = MusicContract.CONTENT_URI
            isPlaylist = false
            playlist = -1
            isAlbum = false
            isMedadataCategory = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val listView: ListView

        if (isEditMode) {
            listView = TouchInterceptor(activity, null)
            listView.setDropListener { from, to -> MediaStore.Audio.Playlists.Members.moveItem(activity.contentResolver, playlist, from, to) }
            listView.setRemoveListener { this.removePlaylistItem(it) }
            listView.setDivider(null)
            listView.setSelector(R.drawable.list_selector_background)
        } else {
            listView = ListView(activity)
            listView.isTextFilterEnabled = true
        }

        listView.id = android.R.id.list
        listView.isFastScrollEnabled = true

        registerForContextMenu(listView)

        return listView
    }

    override fun createListAdapter(): CursorAdapter =
        TrackListAdapter(
            activity, // need to use application context to avoid leaks
            if (isEditMode) R.layout.edit_track_list_item else R.layout.track_list_item,
            arrayOf(),
            intArrayOf())

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> =
        CursorLoader(activity, uri, null, null, null, null)

    private fun removePlaylistItem(which: Int) {
        val v = listView.getChildAt(which - listView.firstVisiblePosition)
        if (v != null) {
            v.visibility = View.GONE
            listView.invalidateViews()
        }
        removeItemFromPlaylist(which)
        if (v != null) {
            v.visibility = View.VISIBLE
            listView.invalidateViews()
        }
    }

    private fun removeItemFromPlaylist(which: Int) {
        adapter!!.cursor.moveToPosition(which)
        val itemId = adapter!!.cursor.getLong(adapter!!.cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members._ID))
        Log.d(LOGTAG, "Removing item " + itemId + " from playlist " + uri?.toString())
        val uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlist)
        val rowCount = activity.contentResolver.delete(ContentUris.withAppendedId(uri, itemId), null, null)
        if (rowCount < 1) {
            Log.i(LOGTAG, "Unable to remove item " + itemId + " from playlist " + uri.toString())
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfoIn: ContextMenu.ContextMenuInfo?) {
        if (menuInfoIn == null) return

        val mi = menuInfoIn as AdapterView.AdapterContextMenuInfo
        selectedPosition = mi.position
        adapter!!.cursor.moveToPosition(selectedPosition)
        selectedId = try {
            val id_idx = adapter!!.cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID)
            adapter!!.cursor.getLong(id_idx)
        } catch (ex: IllegalArgumentException) {
            mi.id
        }

        menu.setHeaderTitle(adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(
                MediaStore.Audio.AudioColumns.TITLE)))

        menu.add(0, R.id.track_play_now, 0, R.string.play_now)
        menu.add(0, R.id.track_play_next, 0, R.string.play_next)
        menu.add(0, R.id.track_queue, 0, R.string.queue)

        val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_to_playlist)
        MusicUtils.makePlaylistMenu(activity, sub, R.id.track_new_playlist, R.id.track_selected_playlist)

        if (isEditMode) {
            menu.add(0, R.id.track_remove_from_playlist, 0, R.string.remove_from_playlist)
        }

        if (!isEditMode) {
            menu.add(0, R.id.track_delete, 0, R.string.delete_item)
        }

        menu.add(0, R.id.track_info, 0, R.string.info)

        menu.add(0, R.id.track_share_via, 0, R.string.share_via)

        // only add the 'search' menu if the selected item is music
        if (MusicUtils.isMusic(adapter!!.cursor)) {
            menu.add(0, R.id.track_search_for_track, 0, R.string.search_for)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.track_play_now -> {
                MusicUtils.queueAndPlayImmediately(activity, longArrayOf(selectedId))
                true
            }

            R.id.track_play_next -> {
                MusicUtils.queueNext(activity, longArrayOf(selectedId))
                true
            }

            R.id.track_queue -> {
                MusicUtils.queue(activity, longArrayOf(selectedId))
                true
            }

            R.id.track_new_playlist -> {
                CreatePlaylist.showMe(activity, longArrayOf(selectedId))
                true
            }

            R.id.track_selected_playlist -> {
                val playlist = item.intent.getLongExtra("playlist", 0)
                MusicUtils.addToPlaylist(activity, longArrayOf(selectedId), playlist)
                true
            }

            R.id.track_delete -> {
                val list = LongArray(1)
                list[0] = selectedId.toInt().toLong()
                val f = getString(R.string.delete_song_desc)
                val desc = String.format(f, adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.AudioColumns.TITLE)))

                AlertDialog.Builder(activity)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_song_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel) { _, _ -> }
                        .setPositiveButton(R.string.delete_confirm_button_text) { _, _ -> MusicUtils.deleteTracks(this@TrackFragment.activity, list) }
                        .show()
                true
            }

            R.id.track_remove_from_playlist -> {
                removePlaylistItem(selectedPosition)
                true
            }

            R.id.track_info -> {
                TrackInfoFragment.showMe(activity,
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId))
                true
            }

            R.id.track_share_via -> {
                adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE))?.let { mimeType ->
                    startActivity(MusicUtils.shareVia(
                            selectedId,
                            mimeType,
                            resources))
                }
                true
            }

            R.id.track_search_for_track -> {
                val trackName: String? = adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.AudioColumns.TITLE))
                val artistName: String? = adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.AudioColumns.ARTIST))
                if (trackName != null && artistName != null) {
                    startActivity(MusicUtils.searchForTrack(trackName, artistName, resources))
                }
                true
            }

            else -> super.onContextItemSelected(item)
        }

    override fun onCreateSectionMenu(view: View) {
        val sectionMenu = PopupMenu(activity, view)
        sectionMenu.setOnMenuItemClickListener(this)
        val menu = sectionMenu.menu

        if (isEditMode) {
            menu.add(0, R.id.tracks_shuffle_playlist, 0, R.string.shuffleplaylist).setIcon(R.drawable.ic_menu_shuffle)
            menu.add(0, R.id.tracks_uniqueify_playlist, 0, R.string.uniqueifyplaylist).setIcon(R.drawable.ic_menu_uniqueify)
        }

        menu.add(0, R.id.tracks_play_all_now, 0, R.string.play_all_now).setIcon(R.drawable.ic_menu_play_clip)
        menu.add(0, R.id.tracks_play_all_next, 0, R.string.play_all_next).setIcon(R.drawable.ic_menu_play_clip)
        menu.add(0, R.id.tracks_queue_all, 0, R.string.queue_all).setIcon(R.drawable.ic_menu_play_clip)
        val interleave = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.interleave_all).setIcon(
                R.drawable.ic_menu_interleave)
        for (i in 1..5) {
            for (j in 1..5) {
                val intent = Intent()
                intent.putExtra(CURRENT_COUNT, i)
                intent.putExtra(NEW_COUNT, j)
                interleave.add(2, R.id.tracks_interleave_all, 0, resources.getString(R.string.interleaveNNN, i, j)).intent = intent
            }
        }

        val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_all_to_playlist).setIcon(R.drawable.ic_menu_add)
        MusicUtils.makePlaylistMenu(activity, sub, R.id.tracks_new_playlist, R.id.tracks_selected_playlist)

        if (!isPlaylist) {
            menu.add(0, R.id.tracks_delete_all, 0, R.string.delete_all).setIcon(R.drawable.ic_menu_delete)
        }

        val title = activity.title
        if (isMedadataCategory && title != null && title != MediaStore.UNKNOWN_STRING) {
            menu.add(0, R.id.tracks_search_for_category, 0, R.string.search_for).setIcon(R.drawable.ic_menu_search)
        }

        if (playlist == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
            menu.add(0, R.id.tracks_edit_playlist, 0, R.string.edit_playlist_menu)
        }

        if (playlist >= 0) {
            menu.add(0, R.id.tracks_export_playlist, 0, R.string.export_playlist_menu)
            menu.add(0, R.id.tracks_share_playlist, 0, R.string.share_via)
        }

        sectionMenu.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.tracks_shuffle_playlist -> {
                ShufflePlaylistTask(activity.applicationContext).execute(
                        playlist, MusicUtils.getSongListForCursor(adapter!!.cursor))
                true
            }

            R.id.tracks_uniqueify_playlist -> {
                val songs = MusicUtils.getSongListForCursor(adapter!!.cursor)
                val found = HashSet<Long>()
                songs.indices
                        .filterNot { found.add(songs[it]) }
                        .forEach { removePlaylistItem(it) }
                true
            }

            R.id.tracks_play_all_now -> {
                MusicUtils.playAll(activity, MusicUtils.getSongListForCursor(adapter!!.cursor))
                true
            }

            R.id.tracks_play_all_next -> {
                MusicUtils.queueNext(activity, MusicUtils.getSongListForCursor(adapter!!.cursor))
                true
            }

            R.id.tracks_queue_all -> {
                MusicUtils.queue(activity, MusicUtils.getSongListForCursor(adapter!!.cursor))
                true
            }

            R.id.tracks_interleave_all -> {
                val intent = item.intent
                val currentCount = intent.getIntExtra(CURRENT_COUNT, 0)
                val newCount = intent.getIntExtra(NEW_COUNT, 0)
                val songs = MusicUtils.getSongListForCursor(adapter!!.cursor)
                MusicUtils.interleave(activity, songs, currentCount, newCount)
                true
            }

            R.id.tracks_new_playlist -> {
                CreatePlaylist.showMe(activity, MusicUtils.getSongListForCursor(adapter!!.cursor))
                true
            }

            R.id.tracks_selected_playlist -> {
                val songs = MusicUtils.getSongListForCursor(adapter!!.cursor)
                val playlist = item.intent.getLongExtra("playlist", 0)
                MusicUtils.addToPlaylist(activity, songs, playlist)
                true
            }

            R.id.tracks_delete_all -> {
                val songs = MusicUtils.getSongListForCursor(adapter!!.cursor)
                val f = getString(R.string.delete_category_desc)
                val desc = String.format(f, activity.title)
                AlertDialog.Builder(activity)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_songs_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel) { _, _ -> }
                        .setPositiveButton(R.string.delete_confirm_button_text) { _, _ -> MusicUtils.deleteTracks(this@TrackFragment.activity, songs) }
                        .show()
                true
            }

            R.id.tracks_search_for_category -> {
                startActivity(MusicUtils.searchForCategory(activity.title,
                        MediaStore.Audio.Media.CONTENT_TYPE, resources))
                true
            }

            R.id.tracks_edit_playlist -> {
                AlertDialog.Builder(activity)
                        .setTitle(R.string.weekpicker_title)
                        .setItems(R.array.weeklist) { _, which ->
                            val numweeks = which + 1
                            MusicUtils.setIntPref(this@TrackFragment.activity, SettingsActivity.NUMWEEKS,
                                    numweeks)
                            loaderManager.restartLoader(0, null, this@TrackFragment)
                        }
                        .show()
                true
            }

            R.id.tracks_export_playlist -> {
                ExportPlaylistTask(activity.applicationContext).execute(activity.title, playlist, false)
                true
            }

            R.id.tracks_share_playlist -> {
                ExportPlaylistTask(activity.applicationContext).execute(activity.title, playlist, true)
                true
            }
            else -> false
        }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        selectedPosition = position
        adapter!!.cursor.moveToPosition(selectedPosition)
        selectedId = try {
            val id_idx = adapter!!.cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID)
            adapter!!.cursor.getLong(id_idx)
        } catch (ex: IllegalArgumentException) {
            id
        }

        if (isPicking()) {
            activity.setResult(Activity.RESULT_OK, Intent().setData(
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId)))
            activity.finish()
        } else {
            MusicUtils.playSong(activity, selectedId)
        }
    }


    override fun onSaveInstanceState(outcicle: Bundle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putInt("selectedposition", selectedPosition)
        outcicle.putLong("selectedtrack", selectedId)

        super.onSaveInstanceState(outcicle)
    }

    val isEditMode: Boolean
        get() = playlist >= 0

    internal data class ViewHolder(
            val line1: TextView,
            val line2: TextView,
            val duration: TextView,
            val play_indicator: ImageView,
            val crossfade_indicator: ImageView,
            val buffer1: CharArrayBuffer,
            var buffer2: CharArray
    )

    internal inner class TrackListAdapter(context: Context, layout: Int, from: Array<String>, to: IntArray) :
            SimpleCursorAdapterWithContextMenu(context, layout, null, from, to, 0), SectionIndexer {

        private val stringBuilder = StringBuilder()
        private val unknownArtistLabel = context.getString(R.string.unknown_artist_name)

        private var titleIdx = -1
        private var artistIdx = -1
        private var durationIdx = -1
        private var audioIdIdx = -1

        private var indexer: AlphabetIndexer? = null

        override fun swapCursor(c: Cursor?): Cursor? {
            val res: Cursor? = super.swapCursor(c)
            if (c != null) {
                getColumnIndices(c)
            }
            return res
        }

        private fun getColumnIndices(cursor: Cursor) {
            try {
                titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)
                artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)
                durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION)

                audioIdIdx = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID)
                if (audioIdIdx < 0) {
                    audioIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID)
                }

                if (indexer != null) {
                    indexer!!.setCursor(cursor)
                } else if (!isEditMode && !isAlbum) {
                    indexer = MusicAlphabetIndexer(cursor, titleIdx, getString(R.string.fast_scroll_alphabet))
                }
            } catch (e: IllegalArgumentException) {
                Log.w(LOGTAG, "Cursor does not contain expected columns, actually contains: "
                        + Arrays.toString(cursor.columnNames) + " - " + e.toString())
            }

        }

        override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
            val view = super.newView(context, cursor, parent)

            (view.findViewById(R.id.icon) as ImageView).visibility = View.GONE

            view.tag = ViewHolder(
                view.findViewById(R.id.line1) as TextView,
                view.findViewById(R.id.line2) as TextView,
                view.findViewById(R.id.duration) as TextView,
                view.findViewById(R.id.play_indicator) as ImageView,
                view.findViewById(R.id.crossfade_indicator) as ImageView,
                CharArrayBuffer(100),
                CharArray(200))

            return view
        }

        override fun bindView(view: View, context: Context, cursor: Cursor) {
            val vh = view.tag as ViewHolder

            cursor.copyStringToBuffer(titleIdx, vh.buffer1)
            vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied)

            val secs = cursor.getInt(durationIdx)
            if (secs == 0) {
                vh.duration.text = ""
            } else {
                vh.duration.text = MusicUtils.formatDuration(context, secs.toLong())
            }

            val builder = stringBuilder
            builder.delete(0, builder.length)

            val name = cursor.getString(artistIdx)
            if (name == null || name == MediaStore.UNKNOWN_STRING) {
                builder.append(unknownArtistLabel)
            } else {
                builder.append(name)
            }
            val len = builder.length
            if (vh.buffer2.size < len) {
                vh.buffer2 = CharArray(len)
            }
            builder.getChars(0, len, vh.buffer2, 0)
            vh.line2.setText(vh.buffer2, 0, len)

            val audioId = cursor.getLong(audioIdIdx)

            val playingId = MusicUtils.sService?.audioId ?: -1L

            val crossfadingId = MusicUtils.sService?.crossfadeAudioId ?: -1L

            when (audioId) {
                playingId -> {
                    vh.play_indicator.visibility = View.VISIBLE
                    vh.crossfade_indicator.visibility = View.INVISIBLE
                }
                crossfadingId -> {
                    vh.play_indicator.visibility = View.INVISIBLE
                    vh.crossfade_indicator.visibility = View.VISIBLE
                }
                else -> {
                    vh.play_indicator.visibility = View.INVISIBLE
                    vh.crossfade_indicator.visibility = View.INVISIBLE
                }
            }
        }

        // SectionIndexer methods

        override fun getSections(): Array<Any>? = indexer?.sections

        override fun getPositionForSection(section: Int) = indexer?.getPositionForSection(section) ?: 0

        override fun getSectionForPosition(position: Int) = 0
    }

}
