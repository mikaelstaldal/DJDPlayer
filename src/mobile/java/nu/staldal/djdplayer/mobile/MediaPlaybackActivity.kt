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
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import nu.staldal.djdplayer.MediaPlayback
import nu.staldal.djdplayer.MediaPlaybackService
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.SettingsActivity

private const val LOGTAG = "MediaPlaybackActivity"

class MediaPlaybackActivity : Activity(), ServiceConnection {

    private var token: MusicUtils.ServiceToken? = null
    private var service: MediaPlayback? = null

    private var playerHeaderFragment: PlayerHeaderFragment? = null
    private var playQueueFragment: PlayQueueFragment? = null
    private var playerFooterFragment: PlayerFooterFragment? = null
    private var playerHeaderDivider: View? = null
    private var playerFooterDivider: View? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(LOGTAG, "onCreate - " + intent)

        volumeControlStream = AudioManager.STREAM_MUSIC

        actionBar.setHomeButtonEnabled(true)

        setContentView(R.layout.audio_player)

        playerHeaderFragment = fragmentManager.findFragmentById(R.id.player_header) as PlayerHeaderFragment
        playQueueFragment = fragmentManager.findFragmentById(R.id.playqueue) as PlayQueueFragment
        playerFooterFragment = fragmentManager.findFragmentById(R.id.player_footer) as PlayerFooterFragment
        playerHeaderDivider = findViewById(R.id.player_header_divider)
        playerFooterDivider = findViewById(R.id.player_footer_divider)

        token = MusicUtils.bindToService(this, this, MobileMediaPlaybackService::class.java)
    }

    public override fun onNewIntent(intent: Intent) {
        Log.i(LOGTAG, "onNewIntent - " + getIntent())
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()

        val f = IntentFilter()
        f.addAction(MediaPlaybackService.META_CHANGED)
        f.addAction(MediaPlaybackService.QUEUE_CHANGED)
        registerReceiver(mStatusListener, IntentFilter(f))
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as MediaPlaybackService.LocalBinder).service

        service!!.let { s ->
            playerHeaderFragment!!.onServiceConnected(s)
            playQueueFragment!!.onServiceConnected(s)
            playerFooterFragment!!.onServiceConnected(s)

            invalidateOptionsMenu()
            updateTrackInfo()

            // Assume something is playing when the service says it is,
            // but also if the audio ID is valid but the service is paused.
            if (s.audioId >= 0 || s.isPlaying) {
                // something is playing now, we're done
                return
            }
            // Service is dead or not playing anything. If we got here as part
            // of a "play this file" Intent, exit. Otherwise go to the Music app start screen.
            if (intent.data == null) {
                val intent = Intent(this@MediaPlaybackActivity, MusicBrowserActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            finish()
        }
    }

    public override fun onResume() {
        super.onResume()
        updateTrackInfo()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.player_menu, menu)

        if (MusicUtils.android44OrLater() || !MusicUtils.hasMenuKey(this)) {
            menu.findItem(R.id.shuffle).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }

        val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, 16, R.string.add_all_to_playlist)
        MusicUtils.makePlaylistMenu(this, sub, R.id.new_playlist, R.id.selected_playlist)

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        updateSoundEffectItem(menu)

        updateRepeatItem(menu)

        updatePlayingItems(menu)

        return true
    }

    private fun updateSoundEffectItem(menu: Menu) {
        val i = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        val item = menu.findItem(R.id.effect_panel)
        item.isVisible = packageManager.resolveActivity(i, 0) != null
    }

    private fun updateRepeatItem(menu: Menu) {
        val item = menu.findItem(R.id.repeat)

        service?.let { s ->
            when (s.repeatMode) {
                MediaPlayback.REPEAT_ALL -> item.setIcon(R.drawable.ic_mp_repeat_all_btn)
                MediaPlayback.REPEAT_CURRENT -> item.setIcon(R.drawable.ic_mp_repeat_once_btn)
                MediaPlayback.REPEAT_STOPAFTER -> item.setIcon(R.drawable.ic_mp_repeat_stopafter_btn)
                else -> item.setIcon(R.drawable.ic_mp_repeat_off_btn)
            }
        } ?: item.setIcon(R.drawable.ic_mp_repeat_off_btn)
    }

    private fun updatePlayingItems(menu: Menu) {
        menu.setGroupVisible(R.id.playing_items, service != null && !service!!.isPlaying)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                val intent = Intent(this, MusicBrowserActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
                true
            }

            R.id.zoom_queue -> {
                if (playQueueFragment!!.isQueueZoomed) {
                    playerHeaderFragment!!.show()
                    playerFooterFragment!!.show()
                    if (playerHeaderDivider != null) playerHeaderDivider!!.visibility = View.VISIBLE
                    if (playerFooterDivider != null) playerFooterDivider!!.visibility = View.VISIBLE
                    playQueueFragment!!.isQueueZoomed = false
                    item.setIcon(R.drawable.ic_menu_zoom)
                } else {
                    playerHeaderFragment!!.hide()
                    playerFooterFragment!!.hide()
                    if (playerHeaderDivider != null) playerHeaderDivider!!.visibility = View.GONE
                    if (playerFooterDivider != null) playerFooterDivider!!.visibility = View.GONE
                    playQueueFragment!!.isQueueZoomed = true
                    item.setIcon(R.drawable.ic_menu_unzoom)
                }
                true
            }

            R.id.repeat -> {
                cycleRepeat()
                true
            }

            R.id.shuffle -> {
                service?.doShuffle()
                true
            }

            R.id.uniqueify -> {
                service?.uniqueify()
                true
            }

            R.id.clear_queue -> {
                service?.removeTracks(0, Integer.MAX_VALUE)
                true
            }

            R.id.new_playlist -> {
                service?.let { s -> CreatePlaylist.showMe(this, s.queue) }
                true
            }

            R.id.selected_playlist -> {
                service?.let { s ->
                    val playlist = item.intent.getLongExtra("playlist", 0)
                    MusicUtils.addToPlaylist(this, s.queue, playlist)
                }
                true
            }

            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            R.id.search -> onSearchRequested()

            R.id.effect_panel -> {
                service?.let { s ->
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, s.audioSessionId)
                    startActivityForResult(intent, 0)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun cycleRepeat() {
        service?.let { s ->
            when (s.repeatMode) {
                MediaPlayback.REPEAT_NONE -> {
                    s.repeatMode = MediaPlayback.REPEAT_ALL
                    Toast.makeText(this, R.string.repeat_all_notif, Toast.LENGTH_SHORT).show()
                }
                MediaPlayback.REPEAT_ALL -> {
                    s.repeatMode = MediaPlayback.REPEAT_CURRENT
                    Toast.makeText(this, R.string.repeat_current_notif, Toast.LENGTH_SHORT).show()
                }
                MediaPlayback.REPEAT_CURRENT -> {
                    s.repeatMode = MediaPlayback.REPEAT_STOPAFTER
                    Toast.makeText(this, R.string.repeat_stopafter_notif, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    s.repeatMode = MediaPlayback.REPEAT_NONE
                    Toast.makeText(this, R.string.repeat_off_notif, Toast.LENGTH_SHORT).show()
                }
            }
            invalidateOptionsMenu()
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null

        playerHeaderFragment!!.onServiceDisconnected()
        playQueueFragment!!.onServiceDisconnected()
        playerFooterFragment!!.onServiceDisconnected()

        finish()
    }

    override fun onStop() {
        unregisterReceiver(mStatusListener)

        super.onStop()
    }

    public override fun onDestroy() {
        if (token != null) MusicUtils.unbindFromService(token)
        service = null

        super.onDestroy()
    }

    private val mStatusListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateTrackInfo()
        }
    }

    private fun updateTrackInfo() {
        service?.let { s ->
            if (s.queueLength > 0) {
                title = (s.queuePosition + 1).toString() + "/" + s.queueLength
            } else {
                finish()
            }
        }
    }

}
