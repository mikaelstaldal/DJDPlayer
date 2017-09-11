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

import android.app.AlertDialog
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
import android.widget.AdapterView
import android.widget.CursorAdapter
import android.widget.ListView
import android.widget.TextView
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.provider.MusicContract

private const val CURRENT_FOLDER = "currentfolder"

class FolderFragment : CategoryFragment() {

    companion object {
        private val COLUMNS = arrayOf<String>(
                MusicContract.FolderColumns.NAME,
                MediaStore.Audio.Media._COUNT,
                MusicContract.FolderColumns.PATH,
                MediaStore.Audio.Media._ID)
    }

    private var mCurrentFolder: String? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        if (icicle != null) {
            mCurrentFolder = icicle.getString(CURRENT_FOLDER)
        }
    }

    override fun createListAdapter(): CursorAdapter {
        val listAdapter = SimpleCursorAdapterWithContextMenu(
                activity,
                R.layout.track_list_item, null,
                COLUMNS,
                intArrayOf(R.id.line1, R.id.line2, R.id.play_indicator),
                0)

        listAdapter.setViewBinder { view, cursor, columnIndex ->
            when (view.id) {
                R.id.line2 -> {
                    val numSongs = cursor.getInt(columnIndex)
                    (view as TextView).text = this@FolderFragment.activity.resources
                            .getQuantityString(R.plurals.Nsongs, numSongs, numSongs)
                    true
                }

                R.id.play_indicator -> {
                    val folder = cursor.getString(columnIndex)

                    val currentFolder = MusicUtils.sService?.folder

                    if (currentFolder != null && currentFolder.absolutePath == folder) {
                        view.visibility = View.VISIBLE
                    } else {
                        view.visibility = View.INVISIBLE
                    }
                    true
                }

                else -> false
            }
        }

        return listAdapter
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> =
        CursorLoader(activity, MusicContract.Folder.CONTENT_URI, COLUMNS, null, null, null)

    override fun onSaveInstanceState(outcicle: Bundle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putString(CURRENT_FOLDER, mCurrentFolder)
        super.onSaveInstanceState(outcicle)
    }

    private fun fetchSongList(folder: String): LongArray {
        val cursor = MusicUtils.query(activity,
                MusicContract.Folder.getMembersUri(folder),
                arrayOf(MediaStore.Audio.AudioColumns._ID), null, null, null)

        if (cursor != null) {
            val list = MusicUtils.getSongListForCursor(cursor)
            cursor.close()
            return list
        }
        return MusicUtils.sEmptyList
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        adapter!!.cursor.moveToPosition(position)
        val path = adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(MusicContract.FolderColumns.PATH))
        viewCategory(MusicContract.Folder.getMembersUri(path))
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfoIn: ContextMenu.ContextMenuInfo?) {
        if (menuInfoIn == null) return

        val mi = menuInfoIn as AdapterView.AdapterContextMenuInfo
        adapter!!.cursor.moveToPosition(mi.position)
        mCurrentFolder = adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(MusicContract.FolderColumns.PATH))
        val title = adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(MusicContract.FolderColumns.NAME))
        menu.setHeaderTitle(title)

        menu.add(0, R.id.folder_play_all_now, 0, R.string.play_all_now)
        menu.add(0, R.id.folder_play_all_next, 0, R.string.play_all_next)
        menu.add(0, R.id.folder_queue_all, 0, R.string.queue_all)
        val interleave = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.interleave_all)
        for (i in 1..5) {
            for (j in 1..5) {
                val intent = Intent()
                intent.putExtra(CURRENT_COUNT, i)
                intent.putExtra(NEW_COUNT, j)
                interleave.add(2, R.id.folder_interleave_all, 0, resources.getString(R.string.interleaveNNN, i, j)).intent = intent
            }
        }

        val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_all_to_playlist)
        MusicUtils.makePlaylistMenu(activity, sub, R.id.folder_new_playlist, R.id.folder_selected_playlist)

        menu.add(0, R.id.folder_delete_all, 0, R.string.delete_all)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.folder_play_all_now -> {
                MusicUtils.playAll(activity, fetchSongList(mCurrentFolder!!))
                return true
            }

            R.id.folder_play_all_next -> {
                MusicUtils.queueNext(activity, fetchSongList(mCurrentFolder!!))
                return true
            }

            R.id.folder_queue_all -> {
                MusicUtils.queue(activity, fetchSongList(mCurrentFolder!!))
                return true
            }

            R.id.folder_interleave_all -> {
                val intent = item.intent
                val currentCount = intent.getIntExtra(CURRENT_COUNT, 0)
                val newCount = intent.getIntExtra(NEW_COUNT, 0)
                MusicUtils.interleave(activity, fetchSongList(mCurrentFolder!!), currentCount, newCount)
                return true
            }

            R.id.folder_new_playlist -> {
                CreatePlaylist.showMe(activity, fetchSongList(mCurrentFolder!!))
                return true
            }

            R.id.folder_selected_playlist -> {
                val playlist = item.intent.getLongExtra("playlist", 0)
                MusicUtils.addToPlaylist(activity, fetchSongList(mCurrentFolder!!), playlist)
                return true
            }

            R.id.folder_delete_all -> {
                val list = fetchSongList(mCurrentFolder!!)
                val f = getString(R.string.delete_folder_desc)
                val desc = String.format(f, mCurrentFolder!!)

                AlertDialog.Builder(activity)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_songs_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel) { _, _ -> }
                        .setPositiveButton(R.string.delete_confirm_button_text) { _, _ -> MusicUtils.deleteTracks(this@FolderFragment.activity, list) }
                        .show()
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }

}
