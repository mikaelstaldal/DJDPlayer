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

import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import nu.staldal.djdplayer.FragmentServiceConnection
import nu.staldal.djdplayer.MediaPlayback
import nu.staldal.djdplayer.MediaPlaybackService
import nu.staldal.djdplayer.R

import kotlinx.android.synthetic.mobile.nowplaying.*

class NowPlayingFragment : Fragment(), FragmentServiceConnection {

    private var service: MediaPlayback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.nowplaying, container, false)

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view!!.setOnClickListener { startActivity(Intent(activity, MediaPlaybackActivity::class.java)) }

        view.findViewById(R.id.prev)?.setOnClickListener { service?.previousOrRestartCurrent() }
        view.findViewById(R.id.next)?.setOnClickListener { service?.next() }
        pause.setOnClickListener {
            service?.let { s ->
                if (s.isPlaying) {
                    s.pause()
                } else {
                    s.play()
                }
            }
        }
    }

    override fun onServiceConnected(s: MediaPlayback) {
        service = s
        updateNowPlaying()
    }

    override fun onResume() {
        super.onResume()

        val f = IntentFilter()
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED)
        f.addAction(MediaPlaybackService.META_CHANGED)
        f.addAction(MediaPlaybackService.QUEUE_CHANGED)
        activity.registerReceiver(mStatusListener, f)
    }

    override fun onPause() {
        activity.unregisterReceiver(mStatusListener)
        super.onPause()
    }

    override fun onServiceDisconnected() {
        service = null
    }

    private val mStatusListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when {
                intent.action == MediaPlaybackService.PLAYSTATE_CHANGED -> updateNowPlaying()
                intent.action == MediaPlaybackService.META_CHANGED -> updateNowPlaying()
                intent.action == MediaPlaybackService.QUEUE_CHANGED -> updateNowPlaying()
            }
        }
    }

    private fun updateNowPlaying() {
        if (service != null && service!!.audioId != -1L) {
            view!!.visibility = View.VISIBLE

            title.text = service!!.trackName

            val artistName = service!!.artistName
            if (MediaStore.UNKNOWN_STRING != artistName) {
                artist.text = artistName
            } else {
                artist.text = getString(R.string.unknown_artist_name)
            }

            pause.setImageResource(
                if (service!!.isPlaying)
                    android.R.drawable.ic_media_pause
                else
                    android.R.drawable.ic_media_play
            )
        } else {
            view!!.visibility = View.GONE
        }
    }
}
