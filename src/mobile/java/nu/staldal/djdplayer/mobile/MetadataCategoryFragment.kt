/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.MediaStore
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R

private const val CURRENT_NAME = "CURRENT_NAME"
private const val IS_UNKNOWN = "IS_UNKNOWN"

abstract class MetadataCategoryFragment : CategoryFragment() {

    protected var mCurrentId: Long = 0
    protected var mCurrentName: String? = null
    protected var mIsUnknown: Boolean = false

    protected abstract fun getSelectedCategoryId(): String

    abstract fun getNameColumnIndex(cursor: Cursor): Int

    protected abstract fun fetchSongList(id: Long): LongArray

    abstract fun fetchCategoryName(cursor: Cursor): String?

    abstract fun getNumberOfSongsColumnName(): String

    abstract fun getUnknownStringId(): Int

    protected abstract fun getDeleteDescStringId(): Int

    abstract fun fetchCurrentlyPlayingCategoryId(): Long

    protected abstract fun getEntryContentType(): String

    protected abstract fun shuffleSongs(): Boolean

    protected abstract fun pupulareContextMenu(menu: ContextMenu)

    /**
     * Do nothing by default, can be overridden by subclasses.
     * @param i  intent
     */
    protected open fun addExtraSearchData(i: Intent) {}

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        if (icicle != null) {
            mCurrentId = icicle.getLong(getSelectedCategoryId())
            mCurrentName = icicle.getString(CURRENT_NAME)
            mIsUnknown = icicle.getBoolean(IS_UNKNOWN)
        }
    }

    override fun createListAdapter(): MetadataCategoryListAdapter =
        MetadataCategoryListAdapter(
                activity,
                R.layout.track_list_item, null,
                arrayOf(),
                intArrayOf(),
                this)

    override fun onSaveInstanceState(outcicle: Bundle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putLong(getSelectedCategoryId(), mCurrentId)
        outcicle.putString(CURRENT_NAME, mCurrentName)
        outcicle.putBoolean(IS_UNKNOWN, mIsUnknown)
        super.onSaveInstanceState(outcicle)
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfoIn: ContextMenu.ContextMenuInfo?) {
        if (menuInfoIn == null) return

        val mi = menuInfoIn as AdapterView.AdapterContextMenuInfo
        adapter!!.cursor.moveToPosition(mi.position)
        mCurrentId = adapter!!.cursor.getLong(adapter!!.cursor.getColumnIndexOrThrow(BaseColumns._ID))
        mCurrentName = fetchCategoryName(adapter!!.cursor)
        mIsUnknown = mCurrentName == null || mCurrentName == MediaStore.UNKNOWN_STRING
        val title = if (mIsUnknown) getString(getUnknownStringId()) else mCurrentName
        menu.setHeaderTitle(title)

        pupulareContextMenu(menu)
    }

    protected fun playAllNow() {
        val songs = fetchSongList(mCurrentId)
        if (shuffleSongs()) MusicUtils.shuffleArray(songs)
        MusicUtils.playAll(activity, songs)
    }

    protected fun playAllNext() {
        val songs = fetchSongList(mCurrentId)
        if (shuffleSongs()) MusicUtils.shuffleArray(songs)
        MusicUtils.queueNext(activity, songs)
    }

    protected fun queueAll() {
        val songs = fetchSongList(mCurrentId)
        if (shuffleSongs()) MusicUtils.shuffleArray(songs)
        MusicUtils.queue(activity, songs)
    }

    protected fun interleaveAll(item: MenuItem) {
        val intent = item.intent
        val currentCount = intent.getIntExtra(CURRENT_COUNT, 0)
        val newCount = intent.getIntExtra(NEW_COUNT, 0)
        val songs = fetchSongList(mCurrentId)
        if (shuffleSongs()) MusicUtils.shuffleArray(songs)
        MusicUtils.interleave(activity, songs, currentCount, newCount)
    }

    protected fun newPlaylist() {
        val songs = fetchSongList(mCurrentId)
        if (shuffleSongs()) MusicUtils.shuffleArray(songs)
        CreatePlaylist.showMe(activity, songs)
    }

    protected fun selectedPlaylist(item: MenuItem) {
        val playlist = item.intent.getLongExtra("playlist", 0)
        val songs = fetchSongList(mCurrentId)
        if (shuffleSongs()) MusicUtils.shuffleArray(songs)
        MusicUtils.addToPlaylist(activity, songs, playlist)
    }

    protected fun deleteAll() {
        val songs = fetchSongList(mCurrentId)
        val f = getString(getDeleteDescStringId())
        val desc = String.format(f, mCurrentName)
        AlertDialog.Builder(activity)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.delete_songs_title)
                .setMessage(desc)
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .setPositiveButton(R.string.delete_confirm_button_text) { _, _ -> MusicUtils.deleteTracks(this@MetadataCategoryFragment.activity, songs) }
                .show()
    }

    protected fun searchForCategory() {
        val intent = MusicUtils.searchForCategory(mCurrentName, getEntryContentType(), resources)
        addExtraSearchData(intent)
        startActivity(intent)
    }

}
