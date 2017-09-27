/*
 * Copyright (C) 2014-2017 Mikael Ståldal
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
import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.MediaStore
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import nu.staldal.djdplayer.FragmentServiceConnection
import nu.staldal.djdplayer.MediaPlayback
import nu.staldal.djdplayer.MediaPlaybackService
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.provider.MusicContract

import kotlinx.android.synthetic.mobile.player_header.*

private const val LOGTAG = "PlayerHeaderFragment"

class PlayerHeaderFragment : Fragment(), FragmentServiceConnection, View.OnLongClickListener {

    private var service: MediaPlayback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.player_header, container, false)

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        registerForContextMenu(trackname_container)
        artistname.setOnLongClickListener(this)
        genrename.setOnLongClickListener(this)

        context_menu.setOnClickListener { trackname_container.showContextMenu() }
    }

    override fun onServiceConnected(s: MediaPlayback) {
        service = s

        update()
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter()
        filter.addAction(MediaPlaybackService.META_CHANGED)
        filter.addAction(MediaPlaybackService.QUEUE_CHANGED)
        activity.registerReceiver(mStatusListener, filter)
    }

    override fun onPause() {
        activity.unregisterReceiver(mStatusListener)

        super.onPause()
    }

    override fun onServiceDisconnected() {
        service = null
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfoIn: ContextMenu.ContextMenuInfo?) {
        service?.let { s ->
            val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_to_playlist)
            MusicUtils.makePlaylistMenu(activity, sub, R.id.playerheader_new_playlist, R.id.playerheader_selected_playlist)

            menu.add(0, R.id.playerheader_delete, 0, R.string.delete_item)

            menu.add(0, R.id.playerheader_info, 0, R.string.info)

            menu.add(0, R.id.playerheader_share_via, 0, R.string.share_via)

            menu.add(0, R.id.playerheader_search_for, 0, R.string.search_for)

            menu.setHeaderTitle(s.trackName)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean = service?.let { s ->
        when (item.itemId) {
            R.id.playerheader_new_playlist -> {
                CreatePlaylist.showMe(activity, longArrayOf(s.audioId))
                true
            }

            R.id.playerheader_selected_playlist -> {
                val list = LongArray(1)
                list[0] = s.audioId
                val playlist = item.intent.getLongExtra("playlist", 0)
                MusicUtils.addToPlaylist(activity, list, playlist)
                true
            }

            R.id.playerheader_delete -> {
                val list = LongArray(1)
                list[0] = s.audioId
                val f = getString(R.string.delete_song_desc, s.trackName)
                AlertDialog.Builder(activity)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_song_title)
                        .setMessage(f)
                        .setNegativeButton(R.string.cancel) { _, _ -> }
                        .setPositiveButton(R.string.delete_confirm_button_text) { _, _ -> MusicUtils.deleteTracks(this@PlayerHeaderFragment.activity, list) }
                        .show()
                true
            }

            R.id.playerheader_info -> {
                TrackInfoFragment.showMe(activity,
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, s.audioId))
                true
            }

            R.id.playerheader_share_via -> {
                s.mimeType?.let { mimeType ->
                    startActivity(MusicUtils.shareVia(
                            s.audioId,
                            mimeType,
                            resources))
                }
                true
            }

            R.id.playerheader_search_for -> {
                val trackName = s.trackName
                val artistName = s.artistName
                if (trackName != null && artistName != null) {
                    startActivity(MusicUtils.searchForTrack(trackName, artistName, resources))
                }
                true
            }
            else -> super.onContextItemSelected(item)
        }
    } ?: false

    override fun onLongClick(view: View): Boolean = service?.let { s ->
        val audioId = s.audioId
        val artistId = s.artistId
        val genreId = s.genreId
        val song = s.trackName
        val artist = s.artistName
        val album = s.albumName

        if (audioId < 0) {
            return false
        }

        if (MediaStore.UNKNOWN_STRING == album &&
                MediaStore.UNKNOWN_STRING == artist &&
                song != null &&
                song.startsWith("recording")) {
            // not music
            return false
        }

        return when (view) {
            artistname -> {
                val intent = Intent(activity, MusicBrowserActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.action = Intent.ACTION_VIEW
                intent.data = MusicContract.Artist.getMembersUri(artistId)
                startActivity(intent)
                if (activity !is MusicBrowserActivity) activity.finish()
                true
            }
            genrename -> {
                val intent = Intent(activity, MusicBrowserActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.action = Intent.ACTION_VIEW
                intent.data = MusicContract.Genre.getMembersUri(genreId)
                startActivity(intent)
                if (activity !is MusicBrowserActivity) activity.finish()
                true
            }
            else -> throw RuntimeException("shouldn't be here")
        }
    } ?: true

    private val mStatusListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            update()
        }
    }

    private fun update() {
        service?.let { s ->
            if (s.audioId != -1L) {
                val trackName = s.trackName
                trackname.text = trackName
                var artistName = s.artistName
                if (MediaStore.UNKNOWN_STRING == artistName) {
                    artistName = getString(R.string.unknown_artist_name)
                }
                artistname.text = artistName
                var genreName = s.genreName
                if (MediaStore.UNKNOWN_STRING == genreName) {
                    genreName = getString(R.string.unknown_genre_name)
                }
                genrename.text = genreName
            } else {
                trackname.text = ""
                artistname.text = ""
                genrename.text = ""
            }
        }
    }

    fun show() {
        player_header.visibility = View.VISIBLE
    }

    fun hide() {
        player_header.visibility = View.GONE
    }

}
