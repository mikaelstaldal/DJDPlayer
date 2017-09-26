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

package nu.staldal.djdplayer

import android.annotation.TargetApi
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.SystemClock
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.v7.app.NotificationCompat
import android.util.Log
import android.widget.Toast

import java.io.File
import java.util.HashSet
import java.util.Random

private const val TAG = "MediaPlaybackService"

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
abstract class MediaPlaybackService : Service(), MediaPlayback {

    companion object {
        const val PLAYSTATE_CHANGED = "nu.staldal.djdplayer.playstatechanged"
        const val META_CHANGED = "nu.staldal.djdplayer.metachanged"
        const val QUEUE_CHANGED = "nu.staldal.djdplayer.queuechanged"

        const val TOGGLEPAUSE_ACTION = "nu.staldal.djdplayer.musicservicecommand.togglepause"
        const val PLAY_ACTION = "nu.staldal.djdplayer.musicservicecommand.play"
        const val PAUSE_ACTION = "nu.staldal.djdplayer.musicservicecommand.pause"
        const val STOP_ACTION = "nu.staldal.djdplayer.musicservicecommand.stop"
        const val PREVIOUS_ACTION = "nu.staldal.djdplayer.musicservicecommand.previous"
        const val NEXT_ACTION = "nu.staldal.djdplayer.musicservicecommand.next"

        private const val PLAYBACKSERVICE_STATUS = 1

        private const val FOCUSCHANGE = 4
        private const val DUCK = 5
        private const val FADEUP = 6
        private const val FADEDOWN = 7
        private const val CROSSFADE = 8

        private val HEXDIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

        private val CURSOR_COLS = arrayOf(
                "audio._id AS _id",
                MediaStore.Audio.AudioColumns.ARTIST,
                MediaStore.Audio.AudioColumns.ALBUM,
                MediaStore.Audio.AudioColumns.TITLE,
                MediaStore.Audio.AudioColumns.DATA,
                MediaStore.Audio.AudioColumns.MIME_TYPE,
                MediaStore.Audio.AudioColumns.ALBUM_ID,
                MediaStore.Audio.AudioColumns.ARTIST_ID)

        /**
         * Interval after which we stop the service when idle.
         */
        private const val IDLE_DELAY_MILLIS = 60000

        /**
         * Jump to previous song if less than this many milliseconds have been played already.
         */
        private const val PREV_THRESHOLD_MILLIS = 2000

        /**
         * Jump to next song if less than this many milliseconds remains.
         */
        private const val NEXT_THRESHOLD_MILLIS = 2000
    }

    // Delegates

    protected var mAudioManager: AudioManager? = null
    protected var mSession: MediaSession? = null
    private var mPersistentState: SharedPreferences? = null
    private var mSettings: SharedPreferences? = null
    private val mPlayers = arrayOfNulls<MyMediaPlayer>(2)


    // Mutable state

    private var mRepeatMode = MediaPlayback.REPEAT_NONE
    private var mPlayList = LongArray(0)
    private var mPlayListLen = 0
    private var mPlayPos = -1

    private var mGenreName: String? = null
    private var mGenreId: Long = -1
    private var mArtistName: String? = null
    private var mArtistId: Long = -1
    private var mAlbumName: String? = null
    private var mAlbumId: Long = -1
    private var mMimeType: String? = null
    private var mFolder: File? = null
    private var mTrackName: String? = null

    private var mServiceStartId = -1
    private var mServiceInUse = false
    @Volatile private var mIsSupposedToBePlaying = false
    private var mQueueIsSaveable = true
    @Volatile private var mPausedByTransientLossOfFocus = false // Used to track what type of audio focus loss caused the playback to pause
    private val mCurrentVolume = FloatArray(2)
    @Volatile private var mCurrentPlayer: Int = 0
    @Volatile private var mNextPlayer: Int = 0
    private var mCardId: Int = 0 // Used to distinguish between different cards when saving/restoring playlists.


    // Local Binder pattern

    private val mLocalBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: MediaPlayback
            get() = this@MediaPlaybackService
    }


    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "onCreate")

        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mPersistentState = getSharedPreferences(packageName, Context.MODE_PRIVATE)

        mSettings = PreferenceManager.getDefaultSharedPreferences(this)

        mCardId = fetchCardId()

        val iFilter = IntentFilter()
        iFilter.addAction(Intent.ACTION_MEDIA_EJECT)
        iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED)
        iFilter.addDataScheme("file")
        registerReceiver(mUnmountReceiver, iFilter)

        // Needs to be done in this thread, since otherwise ApplicationContext.getPowerManager() crashes.
        mPlayers[0] = MyMediaPlayer(this, object : Handler() {
            override fun handleMessage(msg: Message) {
                handlePlayerCallback(0, msg)
            }
        })
        mPlayers[1] = MyMediaPlayer(this, object : Handler() {
            override fun handleMessage(msg: Message) {
                handlePlayerCallback(1, msg)
            }
        })
        mCurrentPlayer = 0
        mNextPlayer = 1

        mCurrentVolume[0] = 1.0f
        mCurrentVolume[1] = 1.0f

        reloadQueue()

        val actionFilter = IntentFilter()
        actionFilter.addAction(TOGGLEPAUSE_ACTION)
        actionFilter.addAction(PLAY_ACTION)
        actionFilter.addAction(PAUSE_ACTION)
        actionFilter.addAction(STOP_ACTION)
        actionFilter.addAction(NEXT_ACTION)
        actionFilter.addAction(PREVIOUS_ACTION)
        enrichActionFilter(actionFilter)
        actionFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(mIntentReceiver, actionFilter)

        createMediaSession()

        additionalCreate()

        // If the service was idle, but got killed before it stopped itself, the
        // system will relaunch it. Make sure it gets stopped again in that case.
        val msg = mDelayedStopHandler.obtainMessage()
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY_MILLIS.toLong())
    }

    protected open fun enrichActionFilter(actionFilter: IntentFilter) {}

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mSession = MediaSession(this, getString(R.string.applabel))
            mSession!!.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        }
    }

    protected open fun additionalCreate() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand intent=$intent flags=$flags startId=$startId")

        mServiceStartId = startId
        mDelayedStopHandler.removeCallbacksAndMessages(null)

        intent?.action?.let { action ->
            when (action) {
                PREVIOUS_ACTION -> previousOrRestartCurrent()
                NEXT_ACTION -> next()
                TOGGLEPAUSE_ACTION -> if (isPlaying) {
                    pause()
                } else {
                    play()
                }
                PLAY_ACTION -> play()
                PAUSE_ACTION -> pause()
                STOP_ACTION -> {
                    pause()
                    seek(0)
                    if (mSession != null) {
                        deactivateMediaSession()
                    }
                }
            }
        }

        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        val msg = mDelayedStopHandler.obtainMessage()
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY_MILLIS.toLong())
        return Service.START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mServiceInUse = true
        return mLocalBinder
    }

    override fun onRebind(intent: Intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mServiceInUse = true
    }

    override fun onUnbind(intent: Intent): Boolean {
        mServiceInUse = false

        // Take a snapshot of the current playlist
        saveQueue(true)

        if (isPlaying || mPausedByTransientLossOfFocus) {
            // something is currently playing, or will be playing once
            // an in-progress action requesting audio focus ends, so don't stop the service now.
            return true
        }

        // If there is a playlist but playback is paused, then wait a while
        // before stopping the service, so that pause/resume isn't slow.
        if (mPlayListLen > 0) {
            val msg = mDelayedStopHandler.obtainMessage()
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY_MILLIS.toLong())
            return true
        }

        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId)
        return true
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")

        // Check that we're not being destroyed while something is still playing.
        if (isPlaying) {
            Log.e(TAG, "Service being destroyed while still playing.")
        }

        additionalDestroy()

        if (mSession != null) {
            releaseMediaSession()
        }

        for (player in mPlayers) player!!.release()

        mAudioManager!!.abandonAudioFocus(mAudioFocusListener)

        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mPlaybackHander.removeCallbacksAndMessages(null)

        unregisterReceiver(mIntentReceiver)
        unregisterReceiver(mUnmountReceiver)

        super.onDestroy()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun releaseMediaSession() {
        mSession!!.release()
    }

    protected open fun additionalDestroy() {}

    @Synchronized
    private fun handlePlayerCallback(player: Int, msg: Message) {
        when (msg.what) {
            MyMediaPlayer.SERVER_DIED -> {
                Log.d(TAG, "MediaPlayer died: " + player)
                if (mIsSupposedToBePlaying) {
                    next()
                } else {
                    // the server died when we were idle, so just reopen the same song
                    // (it will start again from the beginning though when the user restarts)
                    if (mPlayListLen > 0) {
                        if (prepare(mPlayList[mPlayPos])) {
                            fetchMetadata(mPlayList[mPlayPos])
                        }
                    }
                }
            }

            MyMediaPlayer.RELEASE_WAKELOCK -> {
                Log.d(TAG, "MediaPlayer release wakelock: " + player)
                mPlayers[player]!!.releaseWakeLock()
            }


            MyMediaPlayer.TRACK_ENDED -> {
                val fadeSeconds = Integer.parseInt(mSettings!!.getString(SettingsActivity.FADE_SECONDS, "0"))
                mPlaybackHander.removeMessages(DUCK)
                mPlaybackHander.removeMessages(FADEDOWN)
                mPlaybackHander.removeMessages(CROSSFADE)
                when (mRepeatMode) {
                    MediaPlayback.REPEAT_STOPAFTER -> {
                        Log.d(TAG, "MediaPlayer track ended, REPEAT_STOPAFTER: " + player)
                        if (mSession != null) {
                            deactivateMediaSession()
                        }
                        gotoIdleState()
                        notifyChange(PLAYSTATE_CHANGED)
                    }

                    MediaPlayback.REPEAT_CURRENT -> {
                        Log.d(TAG, "MediaPlayer track ended, REPEAT_CURRENT: " + player)
                        seek(0)
                        if (fadeSeconds > 0) {
                            mCurrentVolume[mCurrentPlayer] = 0f
                        }
                        play()
                    }

                    MediaPlayback.REPEAT_NONE, MediaPlayback.REPEAT_ALL -> {
                        repeatNoneAll(player, fadeSeconds)
                    }
                }
            }
        }
    }

    private fun repeatNoneAll(player: Int, fadeSeconds: Int) {
        Log.d(TAG, "MediaPlayer track ended, REPEAT_NONE/REPEAT_ALL: " + player)
        if (mPlayListLen <= 0) {
            if (mSession != null) {
                deactivateMediaSession()
            }
            gotoIdleState()
            notifyChange(PLAYSTATE_CHANGED)
            return
        }

        if (mPlayPos >= mPlayListLen - 1) {  // we're at the end of the list
            if (mRepeatMode == MediaPlayback.REPEAT_NONE) {
                if (mSession != null) {
                    deactivateMediaSession()
                }
                gotoIdleState()
                notifyChange(PLAYSTATE_CHANGED)
                return
            } else {
                mPlayPos = 0
            }
        } else {
            mPlayPos++
        }

        if (mPlayers[mCurrentPlayer]!!.isInitialized()) {
            mPlayers[mCurrentPlayer]!!.stop()
        }

        swapPlayers()

        if (!mPlayers[mCurrentPlayer]!!.isInitialized()) {
            while (!prepare(mPlayList[mPlayPos])) {
                if (mPlayPos >= mPlayListLen - 1) { // we're at the end of the list
                    Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show()
                    break
                } else {
                    mPlayPos++
                }
            }
        }

        if (!mPlayers[mCurrentPlayer]!!.isPlaying()) {
            if (fadeSeconds > 0) {
                mCurrentVolume[mCurrentPlayer] = 0f
                mPlayers[mCurrentPlayer]!!.setVolume(mCurrentVolume[mCurrentPlayer])
            }
            Log.d(TAG, "Starting playback")
            mPlayers[mCurrentPlayer]!!.start()

            mPlaybackHander.sendMessage(mPlaybackHander.obtainMessage(FADEUP, mCurrentPlayer, 0))
        }

        fetchMetadata(mPlayList[mPlayPos])
        startForeground(PLAYBACKSERVICE_STATUS, buildNotification())
        notifyChange(META_CHANGED)
    }

    private fun swapPlayers() {
        val tmp = mCurrentPlayer
        mCurrentPlayer = mNextPlayer
        mNextPlayer = tmp
    }

    private val mPlaybackHander: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            val fadeSeconds = Integer.parseInt(mSettings!!.getString(SettingsActivity.FADE_SECONDS, "0"))

            when (msg.what) {
                DUCK -> {
                    // Log.v(TAG, "handleMessage DUCK: " + msg.arg1);
                    mCurrentVolume[msg.arg1] -= .05f
                    if (mCurrentVolume[msg.arg1] > .2f) {
                        this.sendMessageDelayed(this.obtainMessage(DUCK, msg.arg1, 0), 10)
                    } else {
                        mCurrentVolume[msg.arg1] = .2f
                    }
                    mPlayers[msg.arg1]!!.setVolume(mCurrentVolume[msg.arg1])
                }

                FADEDOWN -> {
                    // Log.v(TAG, "handleMessage FADEDOWN: " + msg.arg1);
                    mCurrentVolume[msg.arg1] -= .01f / Math.max(fadeSeconds, 1)
                    if (mCurrentVolume[msg.arg1] > 0.0f) {
                        this.sendMessageDelayed(this.obtainMessage(FADEDOWN, msg.arg1, 0), 10)
                    } else {
                        mCurrentVolume[msg.arg1] = 0.0f
                    }
                    mPlayers[msg.arg1]!!.setVolume(mCurrentVolume[msg.arg1])
                }

                FADEUP -> {
                    // Log.v(TAG, "handleMessage FADEUP: " + msg.arg1);
                    mCurrentVolume[msg.arg1] += .01f / Math.max(fadeSeconds, 1)
                    if (mCurrentVolume[msg.arg1] < 1.0f) {
                        this.sendMessageDelayed(this.obtainMessage(FADEUP, msg.arg1, 0), 10)
                    } else {
                        mCurrentVolume[msg.arg1] = 1.0f
                        scheduleFadeOut()
                    }
                    mPlayers[msg.arg1]!!.setVolume(mCurrentVolume[msg.arg1])
                }

                FOCUSCHANGE ->
                    // This code is here so we can better synchronize it with the code that handles fade-in
                    when (msg.arg1) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            Log.d(TAG, "AudioFocus: received AUDIOFOCUS_LOSS")
                            audioFocusLoss()
                            mAudioManager!!.abandonAudioFocus(mAudioFocusListener)
                            pause()
                            if (mSession != null) {
                                deactivateMediaSession()
                            }
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            Log.d(TAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                            this.removeMessages(FADEUP)
                            this.removeMessages(FADEDOWN)
                            this.removeMessages(CROSSFADE)
                            this.sendMessage(this.obtainMessage(DUCK, mCurrentPlayer, 0))
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            Log.d(TAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT")
                            val wasPlaying = isPlaying
                            pause()
                            if (wasPlaying) {
                                mPausedByTransientLossOfFocus = true
                            }
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            Log.d(TAG, "AudioFocus: received AUDIOFOCUS_GAIN")
                            audioFocusGain()
                            if (mSession != null) {
                                activateMediaSession()
                            }

                            if (!isPlaying && mPausedByTransientLossOfFocus) {
                                mPausedByTransientLossOfFocus = false
                                mCurrentVolume[mCurrentPlayer] = 0f
                                mPlayers[mCurrentPlayer]!!.setVolume(mCurrentVolume[mCurrentPlayer])
                                play() // also queues a fade-in
                            } else if (isPlaying) {
                                this.removeMessages(DUCK)
                                this.removeMessages(FADEDOWN)
                                this.removeMessages(CROSSFADE)
                                this.sendMessage(this.obtainMessage(FADEUP, mCurrentPlayer, 0))
                            }
                        }
                        else -> Log.w(TAG, "Unknown audio focus change code: " + msg.arg1)
                    }

                CROSSFADE -> {
                    Log.d(TAG, "handleMessage CROSSFADE")
                    if (!mPlayers[mNextPlayer]!!.isInitialized()) {
                        if ((mRepeatMode == MediaPlayback.REPEAT_NONE || mRepeatMode == MediaPlayback.REPEAT_ALL) && mPlayPos + 1 < mPlayListLen) {
                            val nextId = mPlayList[mPlayPos + 1]
                            Log.d(TAG, "Preparing next song " + nextId)
                            mPlayers[mNextPlayer]!!.prepare(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString() + "/" + nextId.toString())
                        }
                    }
                    if (mPlayers[mNextPlayer]!!.isInitialized()) {
                        if (fadeSeconds > 0) {
                            mCurrentVolume[mNextPlayer] = 0f
                            mPlayers[mNextPlayer]!!.setVolume(mCurrentVolume[mNextPlayer])
                        }
                        Log.d(TAG, "Cross-fading")
                        mPlayers[mNextPlayer]!!.start()

                        this.sendMessage(this.obtainMessage(FADEUP, mNextPlayer, 0))

                        notifyChange(META_CHANGED)
                    }
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun activateMediaSession() {
        mSession!!.isActive = true
    }

    protected open fun audioFocusGain() {}

    protected open fun audioFocusLoss() {}

    private val mDelayedStopHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            // Check again to make sure nothing is playing right now
            if (isPlaying || mPausedByTransientLossOfFocus || mServiceInUse) {
                return
            }

            Log.d(TAG, "idle timeout, quitting")

            // save the queue again, because it might have changed
            // since the user exited the music app (because of
            // the play-position changed)
            saveQueue(true)
            stopSelf(mServiceStartId)
        }
    }

    private val mIntentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.action?.let { action ->
                Log.i(TAG, "mIntentReceiver.onReceive: " + action)
                when (action) {
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> pause()
                    PREVIOUS_ACTION -> previousOrRestartCurrent()
                    NEXT_ACTION -> next()
                    TOGGLEPAUSE_ACTION -> if (isPlaying) {
                        pause()
                    } else {
                        play()
                    }
                    PLAY_ACTION -> play()
                    PAUSE_ACTION -> pause()
                    STOP_ACTION -> {
                        pause()
                        seek(0)
                        if (mSession != null) {
                            deactivateMediaSession()
                        }
                    }
                    else -> handleAdditionalActions(intent)
                }
            }
        }
    }

    protected open fun handleAdditionalActions(intent: Intent) {}

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    private val mUnmountReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.action?.let { action ->
                when (action) {
                    Intent.ACTION_MEDIA_EJECT -> {
                        saveQueue(true)
                        mQueueIsSaveable = false
                        closeExternalStorageFiles()
                    }
                    Intent.ACTION_MEDIA_MOUNTED -> {
                        mCardId = fetchCardId()
                        reloadQueue()
                        mQueueIsSaveable = true
                        notifyChange(QUEUE_CHANGED)
                        notifyChange(META_CHANGED)
                    }
                }
            }
        }
    }

    private fun fetchCardId(): Int =
        contentResolver.query(Uri.parse("content://media/external/fs_id"), null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        } ?: -1

    private val mAudioFocusListener = OnAudioFocusChangeListener { focusChange -> mPlaybackHander.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget() }

    private fun saveQueue(full: Boolean) {
        if (!mQueueIsSaveable) {
            return
        }

        val ed = mPersistentState!!.edit()
        if (full) {
            val q = StringBuilder()

            // The current playlist is saved as a list of "reverse hexadecimal"
            // numbers, which we can generate faster than normal decimal or
            // hexadecimal numbers, which in turn allows us to save the playlist
            // more often without worrying too much about performance.
            // (saving the full state takes about 40 ms under no-load conditions
            // on the phone)
            for (i in 0 until mPlayListLen) {
                var n = mPlayList[i]
                if (n == 0L) {
                    q.append("0;")
                } else if (n > 0) {
                    while (n != 0L) {
                        val digit = (n and 0xf).toInt()
                        n = n ushr 4
                        q.append(HEXDIGITS[digit])
                    }
                    q.append(";")
                }
            }
            ed.putString(SettingsActivity.PLAYQUEUE, q.toString())
            ed.putInt(SettingsActivity.CARDID, mCardId)
        }
        ed.putInt(SettingsActivity.CURPOS, mPlayPos)
        if (mPlayers[mCurrentPlayer]!!.isInitialized()) {
            ed.putLong(SettingsActivity.SEEKPOS, mPlayers[mCurrentPlayer]!!.currentPosition())
        }
        ed.putInt(SettingsActivity.REPEATMODE, mRepeatMode)
        ed.apply()
    }

    private fun reloadQueue() {
        val id =
            if (mPersistentState!!.contains(SettingsActivity.CARDID)) {
                mPersistentState!!.getInt(SettingsActivity.CARDID, mCardId.inv())
            } else {
                mCardId
            }
        if (id == mCardId) {
            // Only restore the saved playlist if the card is still
            // the same one as when the playlist was saved
            val queue: String = mPersistentState!!.getString(SettingsActivity.PLAYQUEUE, "")
            if (queue.length > 1) {
                var plen = 0
                var n = 0
                var shift = 0
                for (i in 0 until queue.length) {
                    val c = queue[i]
                    if (c == ';') {
                        ensurePlayListCapacity(plen + 1)
                        mPlayList[plen] = n.toLong()
                        plen++
                        n = 0
                        shift = 0
                    } else {
                        if (c >= '0' && c <= '9') {
                            n += c - '0' shl shift
                        } else if (c >= 'a' && c <= 'f') {
                            n += 10 + c.toInt() - 'a'.toInt() shl shift
                        } else {
                            // bogus playlist data
                            plen = 0
                            break
                        }
                        shift += 4
                    }
                }
                mPlayListLen = plen

                val pos = mPersistentState!!.getInt(SettingsActivity.CURPOS, 0)
                if (pos < 0 || pos >= mPlayListLen) {
                    // The saved playlist is bogus, discard it
                    mPlayListLen = 0
                    return
                }
                mPlayPos = pos

                // When reloadQueue is called in response to a card-insertion,
                // we might not be able to query the media provider right away.
                // To deal with this, try querying for the current file, and if
                // that fails, wait a while and try again. If that too fails,
                // assume there is a problem and don't restore the state.
                var crsr = MusicUtils.query(this,
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        arrayOf("_id"), "_id=" + mPlayList[mPlayPos], null, null)
                if (crsr == null || crsr.count == 0) {
                    // wait a bit and try again
                    SystemClock.sleep(3000)
                    crsr = contentResolver.query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            CURSOR_COLS, "_id=" + mPlayList[mPlayPos], null, null)
                }
                if (crsr != null) {
                    crsr.close()
                }

                if (mPlayListLen > 0) {
                    stop()
                    if (prepare(mPlayList[mPlayPos])) {
                        fetchMetadata(mPlayList[mPlayPos])
                    } else {
                        mPlayListLen = 0
                        return
                    }
                }

                val seekpos = mPersistentState!!.getLong(SettingsActivity.SEEKPOS, 0)
                seek(if (seekpos >= 0 && seekpos < duration()) seekpos else 0)

                var repmode = mPersistentState!!.getInt(SettingsActivity.REPEATMODE, MediaPlayback.REPEAT_NONE)
                if (repmode != MediaPlayback.REPEAT_ALL && repmode != MediaPlayback.REPEAT_CURRENT) {
                    repmode = MediaPlayback.REPEAT_NONE
                }
                mRepeatMode = repmode
            }
        }
    }

    /**
     * Called when we receive a ACTION_MEDIA_EJECT notification.
     */
    private fun closeExternalStorageFiles() {
        // stop playback and clean up if the SD card is going to be unmounted.
        stop()
        if (mSession != null) {
            deactivateMediaSession()
        }
        gotoIdleState()
        notifyChange(QUEUE_CHANGED)
        notifyChange(META_CHANGED)
    }

    /**
     * Notify the change-receivers that something has changed.
     * The intent that is sent contains the following data
     * for the currently playing track:
     * "id" - Long: the database row ID
     * "artist" - String: the name of the artist
     * "album" - String: the name of the album
     * "genre" - String: the name of the genre
     * "track" - String: the name of the track
     * "playing" - Boolean: is playing now
     * The intent has an action that is one of
     * "nu.staldal.djdplayer.metachanged"
     * "nu.staldal.djdplayer.queuechanged",
     * "nu.staldal.djdplayer.playstatechanged"
     * respectively indicating that a new track has
     * started playing, that the playback queue has
     * changed, that playback has stopped because
     * the last file in the list has been played,
     * or that the play-state changed (paused/resumed).
     */
    private fun notifyChange(what: String) {
        val i = Intent(what)
        i.putExtra("id", audioId)
        i.putExtra("artist", artistName)
        i.putExtra("album", albumName)
        i.putExtra("genre", genreName)
        i.putExtra("track", trackName)
        i.putExtra("playing", isPlaying)
        sendStickyBroadcast(i)

        if (what == QUEUE_CHANGED) {
            saveQueue(true)
        } else {
            saveQueue(false)
        }

        extraNotifyChange(what)
    }

    protected open fun extraNotifyChange(what: String) {}

    private fun ensurePlayListCapacity(size: Int) {
        if (size > mPlayList.size) {
            // reallocate at 2x requested size so we don't
            // need to grow and copy the array for every insert
            val newlist = LongArray(size * 2)
            System.arraycopy(mPlayList, 0, newlist, 0, mPlayList.size)
            mPlayList = newlist
        }
        // FIXME: shrink the array when the needed size is much smaller than the allocated size
    }

    /**
     * Insert the list of songs at the specified position in the playlist.
     */
    private fun addToPlayList(list: LongArray, position: Int) {
        addToPlaylistInternal(list, position)
        updatePlaylist()
    }

    private fun addToPlaylistInternal(list: LongArray, position: Int) {
        var pos = position
        if (pos < 0) { // overwrite
            mPlayListLen = 0
            pos = 0
        }
        ensurePlayListCapacity(mPlayListLen + list.size)
        if (pos > mPlayListLen) {
            pos = mPlayListLen
        }

        // move part of list after insertion point
        val tailsize = mPlayListLen - pos
        if (tailsize > 0) System.arraycopy(mPlayList, pos, mPlayList, pos + list.size, tailsize)

        // copy list into playlist
        System.arraycopy(list, 0, mPlayList, pos, list.size)
        mPlayListLen += list.size
    }

    private fun updatePlaylist() {
        if (mPlayListLen == 0) {
            resetMetadata()
            notifyChange(META_CHANGED)
        }
        notifyChange(QUEUE_CHANGED)
    }

    @Synchronized
    override fun enqueue(list: LongArray, action: Int) {
        if (list.isEmpty()) return

        if ((action == MediaPlayback.NEXT || action == MediaPlayback.NOW) && mPlayPos + 1 < mPlayListLen) {
            addToPlayList(list, mPlayPos + 1)
            if (action == MediaPlayback.NOW) {
                stop()
                mPlayPos++
                prepareAndPlay(mPlayList[mPlayPos])
                return
            }
        } else {
            addToPlayList(list, Integer.MAX_VALUE)
            if (action == MediaPlayback.NOW) {
                stop()
                mPlayPos = mPlayListLen - list.size
                prepareAndPlay(mPlayList[mPlayPos])
                return
            }
        }
        if (mPlayPos < 0) {
            stop()
            mPlayPos = 0
            prepareAndPlay(mPlayList[mPlayPos])
        }
    }

    private fun prepareAndPlay(audioId: Long) {
        if (prepare(audioId)) {
            fetchMetadata(audioId)
            play()
            notifyChange(META_CHANGED)
        } else {
            Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show()
        }
    }

    @Synchronized
    override fun interleave(newList: LongArray, currentCount: Int, newCount: Int) {
        val destList = LongArray(mPlayListLen + newList.size)

        var destI = 0
        var currentI = 0
        var newI = 0
        while (destI < destList.size) {
            for (i in 0 until currentCount) {
                if (currentI >= mPlayListLen) break
                destList[destI++] = mPlayList[currentI++]
            }
            for (i in 0 until newCount) {
                if (newI >= newList.size) break
                destList[destI++] = newList[newI++]
            }
        }

        mPlayList = destList
        mPlayListLen = mPlayList.size
        updatePlaylist()
    }

    @Synchronized
    override fun load(list: LongArray, position: Int) {
        val listLength = list.size
        var newlist = true
        if (mPlayListLen == listLength) {
            // possible fast path: list might be the same
            newlist = false
            for (i in 0 until listLength) {
                if (list[i] != mPlayList[i]) {
                    newlist = true
                    break
                }
            }
        }
        if (newlist) {
            addToPlayList(list, -1)
        }
        if (position >= 0) {
            mPlayPos = position
        } else {
            mPlayPos = 0
        }

        stop()
        prepareAndPlay(mPlayList[mPlayPos])
    }

    @Synchronized
    override fun moveQueueItem(index1: Int, index2: Int) {
        var i1 = index1
        var i2 = index2
        if (i1 >= mPlayListLen) {
            i1 = mPlayListLen - 1
        }
        if (i2 >= mPlayListLen) {
            i2 = mPlayListLen - 1
        }
        if (i1 < i2) {
            val tmp = mPlayList[i1]
            System.arraycopy(mPlayList, i1 + 1, mPlayList, i1, i2 - i1)
            mPlayList[i2] = tmp
            if (mPlayPos == i1) {
                mPlayPos = i2
            } else if (mPlayPos >= i1 && mPlayPos <= i2) {
                mPlayPos--
            }
        } else if (i2 < i1) {
            val tmp = mPlayList[i1]
            System.arraycopy(mPlayList, i2, mPlayList, i2 + 1, i1 - i2)
            mPlayList[i2] = tmp
            if (mPlayPos == i1) {
                mPlayPos = i2
            } else if (mPlayPos >= i2 && mPlayPos <= i1) {
                mPlayPos++
            }
        }
        notifyChange(QUEUE_CHANGED)
    }

    override val queue: LongArray
        @Synchronized get() {
            val len = mPlayListLen
            val list = LongArray(len)
            System.arraycopy(mPlayList, 0, list, 0, len)
            return list
        }

    override val queueLength: Int @Synchronized get() = mPlayListLen

    private fun prepare(audioId: Long): Boolean {
        Log.d(TAG, "Preparing song " + audioId)
        return mPlayers[mCurrentPlayer]!!.prepare(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString() + "/" + audioId.toString())
    }

    private fun fetchMetadata(audioId: Long) {
        resetMetadata()
        contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                CURSOR_COLS, "_id=" + audioId.toString(), null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                mArtistName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST))
                mArtistId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST_ID))
                mAlbumName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM))
                mAlbumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID))
                mMimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE))
                mFolder = File(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA))).parentFile
                mTrackName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE))

                val idAndName = MusicUtils.fetchGenre(this, audioId)
                if (idAndName != null) {
                    mGenreId = idAndName.id
                    mGenreName = idAndName.name
                }

                if (mSession != null) {
                    updateMediaMetadata()
                }
            }
        }
    }

    private fun resetMetadata() {
        mGenreName = null
        mGenreId = -1
        mArtistName = null
        mArtistId = -1
        mAlbumName = null
        mAlbumId = -1
        mMimeType = null
        mFolder = null
        mTrackName = null
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun updateMediaMetadata() {
        val metadataBuilder = MediaMetadata.Builder()
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, trackName)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_GENRE, genreName)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, albumName)

        metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART,
                BitmapFactory.decodeResource(resources, R.drawable.app_icon))
        // TODO set small icon

        mSession!!.setMetadata(metadataBuilder.build())
    }

    @Synchronized
    override fun play() {
        val result = mAudioManager!!.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Unable to gain audio focus: " + result)
            return
        }

        beforePlay()

        if (mPlayers[mCurrentPlayer]!!.isInitialized()) {
            // if we are at the end of the song, go to the next song first
            val duration = mPlayers[mCurrentPlayer]!!.duration()
            if (mRepeatMode != MediaPlayback.REPEAT_CURRENT && duration > NEXT_THRESHOLD_MILLIS &&
                    mPlayers[mCurrentPlayer]!!.currentPosition() >= duration - NEXT_THRESHOLD_MILLIS) {
                next()
            }

            if (mSession != null) {
                activateMediaSession()
                updateMediaSession(true)
            }

            mPlayers[mCurrentPlayer]!!.start()
            // make sure we fade in, in case a previous fadein was stopped because of another focus loss
            mPlaybackHander.removeMessages(DUCK)
            mPlaybackHander.removeMessages(FADEDOWN)
            mPlaybackHander.removeMessages(CROSSFADE)
            mPlaybackHander.sendMessage(mPlaybackHander.obtainMessage(FADEUP, mCurrentPlayer, 0))

            startForeground(PLAYBACKSERVICE_STATUS, buildNotification())

            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true
                notifyChange(PLAYSTATE_CHANGED)
            }
        }
    }

    protected open fun beforePlay() {}

    private fun buildNotification(): Notification {
        val trackName2: String?
        var artistName2: String?
        if (audioId < 0) { // streaming
            trackName2 = getString(R.string.streaming)
            artistName2 = null
        } else {
            trackName2 = trackName
            artistName2 = artistName
            if (artistName2 == null || artistName2 == MediaStore.UNKNOWN_STRING) {
                artistName2 = getString(R.string.unknown_artist_name)
            }
        }

        val builder = NotificationCompat.Builder(this)
        builder.setSmallIcon(R.drawable.stat_notify_musicplayer)
        builder.setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.app_icon))
        builder.setContentTitle(trackName2)
        builder.setContentText(artistName2)
        builder.setOngoing(true)
        builder.setWhen(0)
        builder.addAction(android.R.drawable.ic_media_previous, resources.getString(R.string.prev),
                getPendingIntentForAction(PREVIOUS_ACTION))
        builder.addAction(android.R.drawable.ic_media_pause, resources.getString(R.string.pause),
                getPendingIntentForAction(PAUSE_ACTION))
        builder.addAction(android.R.drawable.ic_media_next, resources.getString(R.string.next),
                getPendingIntentForAction(NEXT_ACTION))
        builder.setStyle(NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))

        enrichNotification(builder)

        applyLillipopFunctionality(builder)

        return builder.build()
    }

    protected open fun enrichNotification(builder: NotificationCompat.Builder) {}

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun applyLillipopFunctionality(builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder
                    .setCategory(Notification.CATEGORY_TRANSPORT)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
        }
    }

    private fun getPendingIntentForAction(action: String): PendingIntent {
        return PendingIntent.getService(this, 0, Intent(action).setClass(this, javaClass), 0)
    }

    private fun stop() {
        mPlaybackHander.removeMessages(DUCK)
        mPlaybackHander.removeMessages(FADEUP)
        mPlaybackHander.removeMessages(FADEDOWN)
        mPlaybackHander.removeMessages(CROSSFADE)
        for (player in mPlayers) player!!.stop()
        resetMetadata()
    }

    @Synchronized
    override fun pause() {
        mPlaybackHander.removeMessages(DUCK)
        mPlaybackHander.removeMessages(FADEUP)
        mPlaybackHander.removeMessages(FADEDOWN)
        mPlaybackHander.removeMessages(CROSSFADE)

        val wasPlaying = isPlaying

        for (player in mPlayers) {
            if (player!!.isPlaying()) player.pause()
        }
        gotoIdleState()

        if (wasPlaying) {
            notifyChange(PLAYSTATE_CHANGED)
        }

        mPausedByTransientLossOfFocus = false
    }

    override val isPlaying: Boolean get() = mIsSupposedToBePlaying

    override fun previousOrRestartCurrent() {
        if (position() < PREV_THRESHOLD_MILLIS) {
            previous()
        } else {
            seek(0)
        }
    }

    @Synchronized
    override fun previous() {
        if (mPlayListLen <= 0) return

        if (mPlayPos > 0) {
            mPlayPos--
        } else {
            mPlayPos = mPlayListLen - 1
        }
        stop()
        prepareAndPlay(mPlayList[mPlayPos])
    }

    @Synchronized
    override fun next() {
        if (mPlayListLen <= 0) return

        if (mPlayPos >= mPlayListLen - 1) {
            // we're at the end of the list
            mPlayPos = 0
        } else {
            mPlayPos++
        }
        stop()
        prepareAndPlay(mPlayList[mPlayPos])
    }

    private fun gotoIdleState() {
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendMessageDelayed(mDelayedStopHandler.obtainMessage(), IDLE_DELAY_MILLIS.toLong())
        stopForeground(true)
        if (mSession != null) {
            if (isMediaSessionActive()) {
                updateMediaSession(false)
            }
        }
        mIsSupposedToBePlaying = false
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun isMediaSessionActive(): Boolean = mSession!!.isActive

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun updateMediaSession(isPlaying: Boolean) {
        val stateBuilder = PlaybackState.Builder()
                .setActions(if (isPlaying) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY)
        stateBuilder.setState(if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                mPlayers[mCurrentPlayer]!!.currentPosition(), 1.0f)
        mSession!!.setPlaybackState(stateBuilder.build())
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun deactivateMediaSession() {
        mSession!!.isActive = false
    }

    @Synchronized
    override fun removeTracks(first: Int, last: Int): Int {
        val numRemoved = removeTracksInternal(first, last)
        if (numRemoved > 0) {
            notifyChange(QUEUE_CHANGED)
        }
        return numRemoved
    }

    private fun removeTracksInternal(firstTrack: Int, lastTrack: Int): Int {
        var first = firstTrack
        var last = lastTrack
        if (last < first) return 0
        if (first < 0) first = 0
        if (last >= mPlayListLen) last = mPlayListLen - 1

        var gotonext = false
        if (first <= mPlayPos && mPlayPos <= last) {
            mPlayPos = first
            gotonext = true
        } else if (mPlayPos > last) {
            mPlayPos -= last - first + 1
        }
        val num = mPlayListLen - last - 1
        for (i in 0 until num) {
            mPlayList[first + i] = mPlayList[last + 1 + i]
        }
        mPlayListLen -= last - first + 1

        if (gotonext) {
            if (mPlayListLen == 0) {
                stop()
                if (mSession != null) {
                    deactivateMediaSession()
                }
                gotoIdleState()
                mPlayPos = -1
            } else {
                if (mPlayPos >= mPlayListLen) {
                    mPlayPos = 0
                }
                val wasPlaying = isPlaying
                stop()

                if (prepare(mPlayList[mPlayPos])) {
                    fetchMetadata(mPlayList[mPlayPos])
                    if (wasPlaying) play()
                    notifyChange(META_CHANGED)
                }
            }
        }
        return last - first + 1
    }

    @Synchronized
    override fun removeTrack(id: Long): Int {
        var numRemoved = 0
        var i = 0
        while (i < mPlayListLen) {
            if (mPlayList[i] == id) {
                numRemoved += removeTracksInternal(i, i)
                i--
            }
            i++
        }
        if (numRemoved > 0) {
            notifyChange(QUEUE_CHANGED)
        }
        return numRemoved
    }

    @Synchronized
    override fun doShuffle() {
        val random = Random()
        for (i in 0 until mPlayListLen) {
            if (i != mPlayPos) {
                var randomPosition = random.nextInt(mPlayListLen)
                while (randomPosition == mPlayPos) randomPosition = random.nextInt(mPlayListLen)
                val temp = mPlayList[i]
                mPlayList[i] = mPlayList[randomPosition]
                mPlayList[randomPosition] = temp
            }
        }
        notifyChange(QUEUE_CHANGED)
    }

    @Synchronized
    override fun uniqueify() {
        if (!isPlaying) {
            var modified = false
            val found = HashSet<Long>()
            for (i in mPlayListLen - 1 downTo 0) {
                if (!found.add(mPlayList[i])) {
                    removeTracksInternal(i, i)
                    modified = true
                }
            }
            if (modified) {
                notifyChange(QUEUE_CHANGED)
            }
        }
    }

    override var repeatMode: Int
        @Synchronized get() = mRepeatMode
        @Synchronized set(newRepeatMode) {
            mRepeatMode = newRepeatMode
            saveQueue(false)
        }

    override val audioId: Long
        @Synchronized get() =
            if (mPlayPos >= 0 && mPlayers[mCurrentPlayer]!!.isInitialized()) {
                mPlayList[mPlayPos]
            } else {
                -1
            }

    override val crossfadeAudioId: Long
        @Synchronized get() =
            if (mPlayPos >= 0 && mPlayers[mNextPlayer]!!.isPlaying()) {
                mPlayList[mPlayPos + 1]
            } else {
                -1
            }

    override var queuePosition: Int
        @Synchronized get() = mPlayPos
        @Synchronized set(position) {
            if (position > mPlayListLen - 1) return
            stop()
            mPlayPos = position
            prepareAndPlay(mPlayList[mPlayPos])
        }

    override val crossfadeQueuePosition: Int
        @Synchronized get() =
            if (mPlayPos >= 0 && mPlayers[mNextPlayer]!!.isPlaying()) {
                mPlayPos + 1
            } else {
                -1
            }

    override val artistName: String? @Synchronized get() = mArtistName

    override val artistId: Long @Synchronized get() = mArtistId

    override val albumName: String? @Synchronized get() = mAlbumName

    override val albumId: Long @Synchronized get() = mAlbumId

    override val genreName: String? @Synchronized get() = mGenreName

    override val genreId: Long @Synchronized get() = mGenreId

    override val mimeType: String? @Synchronized get() = mMimeType

    override val folder: File? @Synchronized get() = mFolder

    override val trackName: String? @Synchronized get() = mTrackName

    override fun duration(): Long =
        if (mPlayers[mCurrentPlayer]!!.isInitialized()) {
            mPlayers[mCurrentPlayer]!!.duration()
        } else -1

    override fun position(): Long =
        if (mPlayers[mCurrentPlayer]!!.isInitialized()) {
            mPlayers[mCurrentPlayer]!!.currentPosition()
        } else -1

    @Synchronized
    override fun seek(position: Long) {
        var pos = position
        if (mPlayers[mCurrentPlayer]!!.isInitialized()) {
            mPlaybackHander.removeMessages(DUCK)
            mPlaybackHander.removeMessages(FADEUP)
            mPlaybackHander.removeMessages(FADEDOWN)
            mPlaybackHander.removeMessages(CROSSFADE)
            if (pos < 0) pos = 0
            if (pos > mPlayers[mCurrentPlayer]!!.duration()) pos = mPlayers[mCurrentPlayer]!!.duration()
            mPlayers[mCurrentPlayer]!!.seek(pos)

            if (mIsSupposedToBePlaying) {
                scheduleFadeOut()
            }
        }
    }

    private fun scheduleFadeOut() {
        val fadeOutSeconds = Integer.parseInt(mSettings!!.getString(SettingsActivity.FADE_SECONDS, "0"))
        val crossFade = mSettings!!.getBoolean(SettingsActivity.CROSS_FADE, false)

        if (fadeOutSeconds > 0) {
            val timeLeftMillis = mPlayers[mCurrentPlayer]!!.duration() - mPlayers[mCurrentPlayer]!!.currentPosition()
            if (timeLeftMillis > 0) {
                val delayMillis = timeLeftMillis - fadeOutSeconds * 1000
                Log.d(TAG, "Scheduling fade out $fadeOutSeconds seconds with cross-fade=$crossFade in $delayMillis ms")
                if (crossFade) {
                    mPlaybackHander.sendEmptyMessageDelayed(CROSSFADE, delayMillis)
                }
                mPlaybackHander.sendMessageDelayed(mPlaybackHander.obtainMessage(FADEDOWN, mCurrentPlayer, 0), delayMillis)
            } else {
                Log.w(TAG, "timeLeft is $timeLeftMillis ms")
            }
        }
    }

    override val audioSessionId: Int get() = mPlayers[mCurrentPlayer]!!.getAudioSessionId()

}
