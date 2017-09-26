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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadataRetriever
import android.media.RemoteControlClient
import android.os.Build
import android.support.v7.app.NotificationCompat
import nu.staldal.djdplayer.MediaPlaybackService
import nu.staldal.djdplayer.R

class MobileMediaPlaybackService : MediaPlaybackService() {

    companion object {
        const val APPWIDGETUPDATE_ACTION = "nu.staldal.djdplayer.musicservicecommand.appwidgetupdate"
    }

    // Delegates

    private var mRemoteControlClient: RemoteControlClient? = null

    override fun enrichActionFilter(actionFilter: IntentFilter) {
        actionFilter.addAction(APPWIDGETUPDATE_ACTION)
    }

    override fun handleAdditionalActions(intent: Intent) {
        if (APPWIDGETUPDATE_ACTION == intent.action) {
            // Someone asked us to refresh a set of specific widgets, probably because they were just added.
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            MediaAppWidgetProvider.getInstance().performUpdate(this@MobileMediaPlaybackService, appWidgetIds)
        }
    }

    override fun additionalCreate() {
        mAudioManager!!.registerMediaButtonEventReceiver(ComponentName(this.packageName,
                MediaButtonIntentReceiver::class.java.name))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { // does not work in Lollipop
            setupRemoteControl()
        }
    }

    override fun audioFocusGain() {
        registerMediaButtonEventReceiverAndRemoteControl()
    }

    override fun audioFocusLoss() {
        unregisterMediaButtenEventReceiverAndRemoteControl()
    }

    private fun setupRemoteControl() {
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = ComponentName(this.packageName,
                MediaButtonIntentReceiver::class.java.name)
        mRemoteControlClient = RemoteControlClient(
                PendingIntent.getBroadcast(applicationContext, 0, mediaButtonIntent, 0))
        mRemoteControlClient!!.setTransportControlFlags(
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE or
                        RemoteControlClient.FLAG_KEY_MEDIA_NEXT or
                        RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS)
    }

    override fun extraNotifyChange(what: String) {
        // Share this notification directly with our widgets
        MediaAppWidgetProvider.getInstance().notifyChange(this, what)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { // does not work in Lollipop
            updateRemoteControl()
        }
    }

    override fun beforePlay() {
        registerMediaButtonEventReceiverAndRemoteControl()
    }

    private fun updateRemoteControl() {
        mRemoteControlClient!!.setPlaybackState(if (isPlaying)
            RemoteControlClient.PLAYSTATE_PLAYING
        else
            RemoteControlClient.PLAYSTATE_PAUSED)

        val metadataEditor = mRemoteControlClient!!.editMetadata(true)
        metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, trackName)
        metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artistName)
        metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, artistName)
        metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_GENRE, genreName)
        metadataEditor.apply()
    }

    private fun registerMediaButtonEventReceiverAndRemoteControl() {
        mAudioManager!!.registerMediaButtonEventReceiver(ComponentName(packageName,
                MediaButtonIntentReceiver::class.java.name))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { // does not work in Lollipop
            mAudioManager!!.registerRemoteControlClient(mRemoteControlClient)
        }
    }

    override fun enrichNotification(builder: NotificationCompat.Builder) {
        val activityClass = if (resources.getBoolean(R.bool.tablet_layout))
            MusicBrowserActivity::class.java
        else
            MediaPlaybackActivity::class.java

        val pendingIntent = PendingIntent.getActivity(this, 0,
                Intent(this, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                0)

        builder.setContentIntent(pendingIntent)
    }

    override fun additionalDestroy() {
        unregisterMediaButtenEventReceiverAndRemoteControl()
    }

    private fun unregisterMediaButtenEventReceiverAndRemoteControl() {
        mAudioManager!!.unregisterMediaButtonEventReceiver(ComponentName(this.packageName,
                MediaButtonIntentReceiver::class.java.name))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { // does not work in Lollipop
            mAudioManager!!.unregisterRemoteControlClient(mRemoteControlClient)
        }
    }

}
