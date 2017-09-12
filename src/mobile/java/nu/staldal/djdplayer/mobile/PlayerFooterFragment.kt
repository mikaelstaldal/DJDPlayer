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

import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import nu.staldal.djdplayer.FragmentServiceConnection
import nu.staldal.djdplayer.MediaPlayback
import nu.staldal.djdplayer.MediaPlaybackService
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import nu.staldal.ui.RepeatingImageButton

private const val REFRESH = 1

open class PlayerFooterFragment : Fragment(), FragmentServiceConnection {

    private var service: MediaPlayback? = null

    private var mainView: View? = null
    private var currentTime: TextView? = null
    private var totalTime: TextView? = null
    private var progressBar: ProgressBar? = null
    private var pauseButton: ImageButton? = null

    private var posOverride: Long = -1
    private var paused = true
    private var startSeekPos: Long = 0
    private var lastSeekEventTime: Long = 0
    private var fromTouch = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(layoutId, container, false)

        mainView = view.findViewById(R.id.player_footer)

        currentTime = view.findViewById(R.id.currenttime) as TextView
        totalTime = view.findViewById(R.id.totaltime) as TextView

        progressBar = view.findViewById(android.R.id.progress) as ProgressBar
        if (progressBar is SeekBar) {
            (progressBar as SeekBar).setOnSeekBarChangeListener(mSeekListener)
        }
        progressBar!!.max = 1000

        val prevButton = view.findViewById(R.id.prev) as RepeatingImageButton
        prevButton.setOnClickListener {
            service?.previousOrRestartCurrent()
        }
        prevButton.setRepeatListener({ _, howLong, repCount -> scanBackward(repCount, howLong) }, 260)
        pauseButton = view.findViewById(R.id.pause) as ImageButton
        pauseButton!!.requestFocus()
        pauseButton!!.setOnClickListener { doPauseResume() }
        val nextButton = view.findViewById(R.id.next) as RepeatingImageButton
        nextButton.setOnClickListener {
            service?.next()
        }
        nextButton.setRepeatListener({ _, howLong, repCount -> scanForward(repCount, howLong) }, 260)

        return view
    }

    protected open val layoutId: Int
        get() = R.layout.player_footer

    override fun onServiceConnected(s: MediaPlayback) {
        service = s

        if (s.audioId != -1L) {
            setPauseButtonImage()
            totalTime!!.text = MusicUtils.formatDuration(activity, s.duration())
        }
    }

    override fun onStart() {
        super.onStart()
        paused = false
        val next = refreshNow()
        queueNextRefresh(next)
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter()
        filter.addAction(MediaPlaybackService.META_CHANGED)
        filter.addAction(MediaPlaybackService.PLAYSTATE_CHANGED)
        activity.registerReceiver(mStatusListener, filter)

        setPauseButtonImage()
    }

    override fun onPause() {
        activity.unregisterReceiver(mStatusListener)

        super.onPause()
    }

    override fun onStop() {
        paused = true
        handler.removeMessages(REFRESH)

        super.onStop()
    }

    override fun onDestroyView() {
        if (progressBar is SeekBar) {
            (progressBar as SeekBar).setOnSeekBarChangeListener(null)
        }

        super.onDestroyView()
    }

    override fun onServiceDisconnected() {
        service = null
    }

    private val mStatusListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            service?.let { s ->
                when (intent.action) {
                    MediaPlaybackService.META_CHANGED -> {
                        totalTime!!.text = MusicUtils.formatDuration(activity, s.duration())
                        setPauseButtonImage()
                        queueNextRefresh(1)
                    }
                    MediaPlaybackService.PLAYSTATE_CHANGED -> setPauseButtonImage()
                }
            }
        }
    }

    private val mSeekListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(bar: SeekBar) {
            lastSeekEventTime = 0
            fromTouch = true
        }

        override fun onProgressChanged(bar: SeekBar, progress: Int, fromuser: Boolean) {
            service?.let { s ->
                if (fromuser) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSeekEventTime > 250) {
                        lastSeekEventTime = now
                        posOverride = s.duration() * progress / 1000
                        s.seek(posOverride)

                        // trackball event, allow progress updates
                        if (!fromTouch) {
                            refreshNow()
                            posOverride = -1
                        }
                    }
                }
            }
        }

        override fun onStopTrackingTouch(bar: SeekBar) {
            posOverride = -1
            fromTouch = false
        }
    }

    private fun scanBackward(repCount: Int, delta: Long) {
        service?.let { s ->
            var newDelta = delta
            if (repCount == 0) {
                startSeekPos = s.position()
                lastSeekEventTime = 0
            } else {
                if (newDelta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    newDelta = newDelta * 10
                } else {
                    // seek at 40x after that
                    newDelta = 50000 + (newDelta - 5000) * 40
                }
                var newpos = startSeekPos - newDelta
                if (newpos < 0) {
                    // move to previous track
                    s.previous()
                    val duration = s.duration()
                    startSeekPos += duration
                    newpos += duration
                }
                if (newDelta - lastSeekEventTime > 250 || repCount < 0) {
                    s.seek(newpos)
                    lastSeekEventTime = newDelta
                }
                if (repCount >= 0) {
                    posOverride = newpos
                } else {
                    posOverride = -1
                }
                refreshNow()
            }
        }
    }

    private fun scanForward(repCount: Int, delta: Long) {
        service?.let { s ->
            var newDelta = delta
            if (repCount == 0) {
                startSeekPos = s.position()
                lastSeekEventTime = 0
            } else {
                if (newDelta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    newDelta = newDelta * 10
                } else {
                    // seek at 40x after that
                    newDelta = 50000 + (newDelta - 5000) * 40
                }
                var newpos = startSeekPos + newDelta
                val duration = s.duration()
                if (newpos >= duration) {
                    // move to next track
                    s.next()
                    startSeekPos -= duration // is OK to go negative
                    newpos -= duration
                }
                if (newDelta - lastSeekEventTime > 250 || repCount < 0) {
                    s.seek(newpos)
                    lastSeekEventTime = newDelta
                }
                if (repCount >= 0) {
                    posOverride = newpos
                } else {
                    posOverride = -1
                }
                refreshNow()
            }
        }
    }

    private fun doPauseResume() {
        service?.let { s ->
            if (s.isPlaying) {
                s.pause()
            } else {
                s.play()
            }
            refreshNow()
            setPauseButtonImage()
        }
    }

    private fun setPauseButtonImage() {
        if (service != null && service!!.isPlaying) {
            pauseButton!!.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            pauseButton!!.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun queueNextRefresh(delay: Long) {
        if (!paused) {
            val msg = handler.obtainMessage(REFRESH)
            handler.removeMessages(REFRESH)
            handler.sendMessageDelayed(msg, delay)
        }
    }

    private fun refreshNow(): Long = service?.let { s -> calcRemaining(s) } ?: 500L

    private fun calcRemaining(s: MediaPlayback): Long {
        val pos = if (posOverride < 0) s.position() else posOverride
        var remaining = 1000 - pos % 1000
        val duration = s.duration()
        if (pos >= 0 && duration > 0) {
            currentTime!!.text = MusicUtils.formatDuration(activity, pos)

            if (s.isPlaying) {
                currentTime!!.visibility = View.VISIBLE
            } else {
                // blink the counter
                val vis = currentTime!!.visibility
                currentTime!!.visibility = if (vis == View.INVISIBLE) View.VISIBLE else View.INVISIBLE
                remaining = 500
            }

            progressBar!!.progress = (1000 * pos / duration).toInt()
        } else {
            currentTime!!.text = "--:--"
            progressBar!!.progress = 1000
        }
        // return the number of milliseconds until the next full second, so
        // the counter can be updated at just the right time
        return remaining
    }

    private val handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                REFRESH -> {
                    val next = refreshNow()
                    queueNextRefresh(next)
                }
            }
        }
    }

    fun show() {
        mainView!!.visibility = View.VISIBLE
    }

    fun hide() {
        mainView!!.visibility = View.GONE
    }
}
