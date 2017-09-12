/*
 * Copyright (C) 2013-2017 Mikael Ståldal
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
import android.app.ListFragment
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import nu.staldal.djdplayer.FragmentServiceConnection
import nu.staldal.djdplayer.MediaPlayback
import nu.staldal.djdplayer.MediaPlaybackService
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.PlayQueueCursor
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.SettingsActivity
import nu.staldal.ui.TouchInterceptor

private const val LOGTAG = "PlayQueueFragment"

class PlayQueueFragment : ListFragment(), FragmentServiceConnection, AbsListView.OnScrollListener {

    private var service: MediaPlayback? = null

    internal var playQueueCursor: PlayQueueCursor? = null
    internal var listAdapter: SimpleCursorAdapter? = null
    internal var deletedOneRow: Boolean = false

    var isQueueZoomed: Boolean = false
        set(queueZoomed) {
            field = queueZoomed
            if (!queueZoomed) listScrolled = false
        }
    private var listScrolled: Boolean = false

    internal var mSelectedPosition = -1
    internal var mSelectedId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.let { state ->
            mSelectedPosition = state.getInt("selectedposition", -1)
            mSelectedId = state.getLong("selectedtrack", -1)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val listView = TouchInterceptor(activity, null)
        listView.id = android.R.id.list
        listView.isFastScrollEnabled = true
        listView.setDropListener { from, to ->
            playQueueCursor!!.moveItem(from, to)
            listAdapter!!.notifyDataSetChanged()
            getListView().invalidateViews()
            deletedOneRow = true
        }
        listView.setRemoveListener { this.removePlaylistItem(it) }
        listView.divider = null
        listView.setSelector(R.drawable.list_selector_background)

        registerForContextMenu(listView)

        listScrolled = false
        listView.setOnScrollListener(this)

        return listView
    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter()
        filter.addAction(MediaPlaybackService.META_CHANGED)
        filter.addAction(MediaPlaybackService.QUEUE_CHANGED)
        activity.registerReceiver(mNowPlayingListener, filter)
    }

    override fun onServiceConnected(s: MediaPlayback) {
        service = s
        playQueueCursor = PlayQueueCursor(s, activity.contentResolver)
        listAdapter = SimpleCursorAdapterWithContextMenu(
                activity,
                R.layout.edit_track_list_item,
                playQueueCursor,
                PlayQueueCursor.COLUMNS,
                intArrayOf(R.id.line1, R.id.line2, R.id.duration, R.id.play_indicator, R.id.crossfade_indicator),
                0)
        listAdapter!!.viewBinder = object : SimpleCursorAdapter.ViewBinder {
            internal val unknownArtist = this@PlayQueueFragment.activity.getString(R.string.unknown_artist_name)

            override fun setViewValue(view: View, cursor: Cursor, columnIndex: Int): Boolean =
                when (view.id) {
                    R.id.line2 -> {
                        val name = cursor.getString(columnIndex)
                        if (name == null || name == MediaStore.UNKNOWN_STRING) {
                            (view as TextView).text = unknownArtist
                        } else {
                            (view as TextView).text = name
                        }
                        true
                    }

                    R.id.duration -> {
                        val secs = cursor.getInt(columnIndex)
                        if (secs == 0) {
                            (view as TextView).text = ""
                        } else {
                            (view as TextView).text = MusicUtils.formatDuration(this@PlayQueueFragment.activity, secs.toLong())
                        }
                        true
                    }

                    R.id.play_indicator -> {
                        service?.let { s ->
                            val cursorPosition = cursor.position
                            if (cursorPosition == s.queuePosition) {
                                view.visibility = View.VISIBLE
                            } else {
                                view.visibility = View.INVISIBLE
                            }
                            if (cursorPosition == s.crossfadeQueuePosition) {
                                view.visibility = View.VISIBLE
                            } else {
                                view.visibility = View.INVISIBLE
                            }
                            true
                        } ?: false
                    }

                    R.id.crossfade_indicator -> {
                        service?.let { s ->
                            val cursorPosition = cursor.position
                            if (cursorPosition == s.crossfadeQueuePosition) {
                                view.visibility = View.VISIBLE
                            } else {
                                view.visibility = View.INVISIBLE
                            }
                            true
                        } ?: false
                    }

                    else -> false
                }
        }
        setListAdapter(listAdapter)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        service?.let { s ->
            val clickOnSong = PreferenceManager.getDefaultSharedPreferences(activity).getString(
                    SettingsActivity.CLICK_ON_SONG, SettingsActivity.PLAY_NEXT)
            if (clickOnSong == SettingsActivity.PLAY_NOW) {
                s.queuePosition = position
            } else {
                if (!s.isPlaying) {
                    s.queuePosition = position
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        activity.unregisterReceiver(mNowPlayingListener)
        listScrolled = false
    }

    override fun onSaveInstanceState(outcicle: Bundle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putInt("selectedposition", mSelectedPosition)
        outcicle.putLong("selectedtrack", mSelectedId)

        super.onSaveInstanceState(outcicle)
    }

    private fun removePlaylistItem(which: Int): Boolean {
        val view = listView.getChildAt(which - listView.firstVisiblePosition)
        if (view == null) {
            Log.i(LOGTAG, "No view when removing playlist item " + which)
            return false
        }
        if (service != null && which != service!!.queuePosition) {
            deletedOneRow = true
        }
        view.visibility = View.GONE
        listView.invalidateViews()
        val ret = playQueueCursor!!.removeItem(which)
        listAdapter!!.notifyDataSetChanged()
        view.visibility = View.VISIBLE
        listView.invalidateViews()
        return ret
    }

    private val mNowPlayingListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The service could disappear while the broadcast was in flight,
            // so check to see if it's still valid
            service?.let { s ->
                if (intent.action == MediaPlaybackService.QUEUE_CHANGED) {
                    if (deletedOneRow) {
                        // This is the notification for a single row that was
                        // deleted previously, which is already reflected in the UI.
                        deletedOneRow = false
                        return
                    }
                    playQueueCursor!!.requery()
                    listAdapter!!.notifyDataSetChanged()
                }

                listView.invalidateViews()
                if (!listScrolled && !isQueueZoomed) listView.setSelection(s.queuePosition + 1)
            }
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfoIn: ContextMenu.ContextMenuInfo?) {
        if (menuInfoIn == null) return
        val mi = menuInfoIn as AdapterView.AdapterContextMenuInfo
        mSelectedPosition = mi.position
        playQueueCursor!!.moveToPosition(mSelectedPosition)
        mSelectedId = playQueueCursor!!.getLong(playQueueCursor!!.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID))

        menu.add(0, R.id.playqueue_play_now, 0, R.string.play_now)

        val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_to_playlist)
        MusicUtils.makePlaylistMenu(activity, sub, R.id.playqueue_new_playlist, R.id.playqueue_selected_playlist)

        menu.add(0, R.id.playqueue_delete, 0, R.string.delete_item)

        menu.add(0, R.id.playqueue_info, 0, R.string.info)

        menu.add(0, R.id.playqueue_share_via, 0, R.string.share_via)

        // only add the 'search' menu if the selected item is music
        if (MusicUtils.isMusic(playQueueCursor)) {
            menu.add(0, R.id.playqueue_search_for, 0, R.string.search_for)
        }

        menu.setHeaderTitle(
                playQueueCursor!!.getString(playQueueCursor!!.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)))
    }

    override fun onContextItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.playqueue_play_now -> {
                service?.let { s -> s.queuePosition = mSelectedPosition }
                true
            }

            R.id.playqueue_new_playlist -> {
                CreatePlaylist.showMe(activity, longArrayOf(mSelectedId))
                true
            }

            R.id.playqueue_selected_playlist -> {
                val playlist = item.intent.getLongExtra("playlist", 0)
                MusicUtils.addToPlaylist(activity, longArrayOf(mSelectedId), playlist)
                true
            }

            R.id.playqueue_delete -> {
                val list = LongArray(1)
                list[0] = mSelectedId.toInt().toLong()
                val f = getString(R.string.delete_song_desc)
                val desc = String.format(f,
                        playQueueCursor!!.getString(playQueueCursor!!.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)))

                AlertDialog.Builder(activity)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_song_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel) { dialog, which -> }
                        .setPositiveButton(R.string.delete_confirm_button_text) { dialog, which -> MusicUtils.deleteTracks(this@PlayQueueFragment.activity, list) }
                        .show()
                true
            }

            R.id.playqueue_info -> {
                TrackInfoFragment.showMe(activity,
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mSelectedId))

                true
            }

            R.id.playqueue_share_via -> {
                startActivity(MusicUtils.shareVia(
                        mSelectedId,
                        playQueueCursor!!.getString(playQueueCursor!!.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE)),
                        resources))
                true
            }

            R.id.playqueue_search_for -> {
                startActivity(MusicUtils.searchForTrack(
                        playQueueCursor!!.getString(playQueueCursor!!.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)),
                        playQueueCursor!!.getString(playQueueCursor!!.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)),
                        playQueueCursor!!.getString(playQueueCursor!!.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM)),
                        resources))
                true
            }
            else -> super.onContextItemSelected(item)
        }

    override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL
                || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING)
            listScrolled = true
    }

    override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {}

    override fun onServiceDisconnected() {
        service = null
    }

}
