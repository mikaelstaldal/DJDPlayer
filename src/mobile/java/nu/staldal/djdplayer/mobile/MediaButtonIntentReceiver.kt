/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import nu.staldal.djdplayer.MediaPlaybackService

private const val LOGTAG = "MediaButtonIntentRecv"

class MediaButtonIntentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return

            Log.d(LOGTAG, "Button: " + event.toString())

            // single quick press: pause/resume.
            // double press: next track

            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY ->
                    handleAction(context, event, MediaPlaybackService.PLAY_ACTION)
                KeyEvent.KEYCODE_MEDIA_PAUSE ->
                    handleAction(context, event, MediaPlaybackService.PAUSE_ACTION)
                KeyEvent.KEYCODE_MEDIA_STOP ->
                    handleAction(context, event, MediaPlaybackService.STOP_ACTION)
                KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                    handleAction(context, event, MediaPlaybackService.TOGGLEPAUSE_ACTION)
                KeyEvent.KEYCODE_MEDIA_NEXT ->
                    handleAction(context, event, MediaPlaybackService.NEXT_ACTION)
                KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                    handleAction(context, event, MediaPlaybackService.PREVIOUS_ACTION)
            }
        }
    }

    private fun handleAction(context: Context, event: KeyEvent, action: String) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (!mDown) {
                // if this isn't a repeat event

                // The service may or may not be running, but we need to send it a command.
                val i = Intent(context, MobileMediaPlaybackService::class.java)
                if (event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK && event.eventTime - mLastClickTime < 300) {
                    i.action = MediaPlaybackService.NEXT_ACTION
                    mLastClickTime = 0
                } else {
                    i.action = action
                    mLastClickTime = event.eventTime
                }
                context.startService(i)

                mDown = true
            }
        } else {
            mDown = false
        }
        if (isOrderedBroadcast) {
            abortBroadcast()
        }
    }

    companion object {
        private var mLastClickTime: Long = 0
        private var mDown = false
    }
}
