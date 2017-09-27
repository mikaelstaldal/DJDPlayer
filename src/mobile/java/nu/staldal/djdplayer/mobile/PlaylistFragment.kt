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

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.CursorLoader
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.CursorAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import nu.staldal.djdplayer.ExportPlaylistTask
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.SettingsActivity
import nu.staldal.djdplayer.provider.MusicContract

private const val LOGTAG = "PlaylistFragment"

private const val CURRENT_PLAYLIST = "currentplaylist"
private const val CURRENT_PLAYLIST_NAME = "currentplaylistname"

class PlaylistFragment : CategoryFragment() {

    companion object {
        private val COLUMNS = arrayOf(
                MediaStore.Audio.AudioColumns._ID,
                MusicContract.PlaylistColumns.NAME,
                MediaStore.Audio.AudioColumns._COUNT)
    }

    private var currentId: Long = 0
    private var playlistName: String? = null

    private var createShortcut: Boolean = false

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        if (icicle != null) {
            currentId = icicle.getLong(CURRENT_PLAYLIST)
            playlistName = icicle.getString(CURRENT_PLAYLIST_NAME)
        }

        createShortcut = activity is PlaylistShortcutActivity
    }

    override fun createListAdapter(): CursorAdapter {
        val listAdapter = SimpleCursorAdapterWithContextMenu(
                activity,
                R.layout.track_list_item, null,
                arrayOf(MusicContract.PlaylistColumns.NAME, MediaStore.Audio.AudioColumns._COUNT),
                intArrayOf(R.id.line1, R.id.line2),
                0)

        listAdapter.setViewBinder { view, cursor, columnIndex ->
            when (view.id) {
                R.id.line2 -> {
                    val numSongs = cursor.getInt(columnIndex)
                    (view as TextView).text = this@PlaylistFragment.activity.resources
                            .getQuantityString(R.plurals.Nsongs, numSongs, numSongs)
                    true
                }

                else -> false
            }
        }

        return listAdapter
    }

    override fun onCreateLoader(id: Int, args: Bundle?) =
        CursorLoader(
            activity,
            MusicContract.Playlist.CONTENT_URI,
            COLUMNS,
            MusicContract.PlaylistColumns.NAME + " != ''",
            null,
            MusicContract.PlaylistColumns.NAME)

    override fun onSaveInstanceState(outcicle: Bundle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putLong(CURRENT_PLAYLIST, currentId)
        outcicle.putString(CURRENT_PLAYLIST_NAME, playlistName)
        super.onSaveInstanceState(outcicle)
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfoIn: ContextMenuInfo?) {
        if (menuInfoIn == null) return

        val mi = menuInfoIn as AdapterContextMenuInfo
        currentId = mi.id
        adapter!!.cursor.moveToPosition(mi.position)
        playlistName = adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(MusicContract.PlaylistColumns.NAME))
        menu.setHeaderTitle(playlistName)

        menu.add(0, R.id.playlist_play_all_now, 0, R.string.play_all_now)
        menu.add(0, R.id.playlist_play_all_next, 0, R.string.play_all_next)
        menu.add(0, R.id.playlist_queue_all, 0, R.string.queue_all)
        val interleave = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.interleave_all)
        for (i in 1..5) {
            for (j in 1..5) {
                val intent = Intent()
                intent.putExtra(CURRENT_COUNT, i)
                intent.putExtra(NEW_COUNT, j)
                interleave.add(2, R.id.playlist_interleave_all, 0, resources.getString(R.string.interleaveNNN, i, j)).intent = intent
            }
        }

        val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_all_to_playlist)
        MusicUtils.makePlaylistMenu(activity, sub, R.id.playlist_new_playlist, R.id.playlist_selected_playlist)

        if (currentId >= 0) {
            menu.add(0, R.id.playlist_delete_playlist, 0, R.string.delete_playlist_menu)
            menu.add(0, R.id.playlist_rename_playlist, 0, R.string.rename_playlist_menu)
        }

        if (currentId == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
            menu.add(0, R.id.playlist_edit_playlist, 0, R.string.edit_playlist_menu)
        }

        if (currentId >= 0) {
            menu.add(0, R.id.playlist_export_playlist, 0, R.string.export_playlist_menu)
            menu.add(0, R.id.playlist_share_playlist, 0, R.string.share_via)
        }
    }

    @SuppressLint("InflateParams")
    override fun onContextItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.playlist_play_all_now -> {
                MusicUtils.playAll(activity, fetchSongList(currentId))
                true
            }

            R.id.playlist_play_all_next -> {
                MusicUtils.queueNext(activity, fetchSongList(currentId))
                true
            }

            R.id.playlist_queue_all -> {
                MusicUtils.queue(activity, fetchSongList(currentId))
                true
            }

            R.id.playlist_interleave_all -> {
                val intent = item.intent
                val currentCount = intent.getIntExtra(CURRENT_COUNT, 0)
                val newCount = intent.getIntExtra(NEW_COUNT, 0)
                MusicUtils.interleave(activity, fetchSongList(currentId), currentCount, newCount)
                true
            }

            R.id.playlist_new_playlist -> {
                CreatePlaylist.showMe(activity, fetchSongList(currentId))
                true
            }

            R.id.playlist_selected_playlist -> {
                val playlist = item.intent.getLongExtra("playlist", 0)
                MusicUtils.addToPlaylist(activity, fetchSongList(currentId), playlist)
                true
            }

            R.id.playlist_delete_playlist -> {
                val desc = String.format(getString(R.string.delete_playlist_desc),
                        adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(MusicContract.PlaylistColumns.NAME)))
                AlertDialog.Builder(activity)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_playlist_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel) { _, _ -> }
                        .setPositiveButton(R.string.delete_confirm_button_text) { _, _ ->
                            val uri = ContentUris.withAppendedId(
                                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, currentId)
                            activity.contentResolver.delete(uri, null, null)
                            Toast.makeText(activity, R.string.playlist_deleted_message, Toast.LENGTH_SHORT).show()
                        }
                        .show()
                true
            }

            R.id.playlist_edit_playlist -> {
                if (currentId == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
                    AlertDialog.Builder(activity)
                            .setTitle(R.string.weekpicker_title)
                            .setItems(R.array.weeklist) { _, which ->
                                val numweeks = which + 1
                                MusicUtils.setIntPref(this@PlaylistFragment.activity, SettingsActivity.NUMWEEKS,
                                        numweeks)
                                loaderManager.restartLoader(0, null, this@PlaylistFragment)
                            }.show()
                } else {
                    Log.e(LOGTAG, "should not be here")
                }
                true
            }

            R.id.playlist_rename_playlist -> {
                val view = activity.layoutInflater.inflate(R.layout.rename_playlist, null)
                val mPlaylist = view.findViewById(R.id.playlist) as EditText
                val playlistId = currentId

                if (playlistId >= 0 && playlistName != null) {
                    mPlaylist.setText(playlistName)
                    mPlaylist.setSelection(playlistName!!.length)

                    AlertDialog.Builder(activity)
                            .setTitle(String.format(this@PlaylistFragment.getString(R.string.rename_playlist_prompt),
                                    playlistName))
                            .setView(view)
                            .setNegativeButton(R.string.cancel) { _, _ -> }
                            .setPositiveButton(R.string.create_playlist_create_text) { _, _ -> MusicUtils.renamePlaylist(activity, playlistId, mPlaylist.text.toString()) }
                            .show()
                }
                true
            }

            R.id.playlist_export_playlist -> {
                ExportPlaylistTask(activity.applicationContext).execute(playlistName, currentId, false)
                true
            }

            R.id.playlist_share_playlist -> {
                ExportPlaylistTask(activity.applicationContext).execute(playlistName, currentId, true)
                true
            }
            else -> super.onContextItemSelected(item)
        }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        if (createShortcut) {
            val shortcut = Intent()
            shortcut.action = Intent.ACTION_VIEW
            shortcut.data = MusicContract.Playlist.getMembersUri(id)

            val intent = Intent()
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut)
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(MusicContract.PlaylistColumns.NAME)))
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(
                    activity, R.drawable.ic_launcher_shortcut_music_playlist))

            activity.setResult(Activity.RESULT_OK, intent)
            activity.finish()
        } else {
            viewCategory(MusicContract.Playlist.getMembersUri(id))
        }
    }

    private fun fetchSongList(playlistId: Long): LongArray =
            MusicUtils.query(activity,
                    MusicContract.Playlist.getMembersUri(playlistId),
                    null,null,null,null).use { cursor ->
                MusicUtils.getSongListForCursor(cursor)
            }

}
