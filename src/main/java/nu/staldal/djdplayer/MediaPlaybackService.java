/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012-2016 Mikael Ståldal
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

package nu.staldal.djdplayer;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public abstract class MediaPlaybackService extends Service implements MediaPlayback {
    private static final String LOGTAG = "MediaPlaybackService";

    public static final String PLAYSTATE_CHANGED = "nu.staldal.djdplayer.playstatechanged";
    public static final String META_CHANGED = "nu.staldal.djdplayer.metachanged";
    public static final String QUEUE_CHANGED = "nu.staldal.djdplayer.queuechanged";

    public static final String TOGGLEPAUSE_ACTION = "nu.staldal.djdplayer.musicservicecommand.togglepause";
    public static final String PLAY_ACTION = "nu.staldal.djdplayer.musicservicecommand.play";
    public static final String PAUSE_ACTION = "nu.staldal.djdplayer.musicservicecommand.pause";
    public static final String STOP_ACTION = "nu.staldal.djdplayer.musicservicecommand.stop";
    public static final String PREVIOUS_ACTION = "nu.staldal.djdplayer.musicservicecommand.previous";
    public static final String NEXT_ACTION = "nu.staldal.djdplayer.musicservicecommand.next";

    private static final int PLAYBACKSERVICE_STATUS = 1;

    private static final int FOCUSCHANGE = 4;
    private static final int DUCK = 5;
    private static final int FADEUP = 6;
    private static final int FADEDOWN = 7;
    private static final int CROSSFADE = 8;

    private static final char HEXDIGITS[] = new char[]{
            '0', '1', '2', '3',
            '4', '5', '6', '7',
            '8', '9', 'a', 'b',
            'c', 'd', 'e', 'f'
    };

    private static final String[] CURSOR_COLS = new String[]{
            "audio._id AS _id",
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.DATA,
            MediaStore.Audio.AudioColumns.MIME_TYPE,
            MediaStore.Audio.AudioColumns.ALBUM_ID,
            MediaStore.Audio.AudioColumns.ARTIST_ID
    };

    /**
     * Interval after which we stop the service when idle.
     */
    private static final int IDLE_DELAY_MILLIS = 60000;

    /**
     * Jump to previous song if less than this many milliseconds have been played already.
     */
    private static final int PREV_THRESHOLD_MILLIS = 2000;

    /**
     * Jump to next song if less than this many milliseconds remains.
     */
    private static final int NEXT_THRESHOLD_MILLIS = 2000;


    // Delegates

    protected AudioManager mAudioManager;
    protected MediaSession mSession;
    private SharedPreferences mPersistentState;
    private SharedPreferences mSettings;
    private final MyMediaPlayer[] mPlayers = new MyMediaPlayer[2];


    // Mutable state

    private int mRepeatMode = REPEAT_NONE;
    private long[] mPlayList = new long[0];
    private int mPlayListLen = 0;
    private int mPlayPos = -1;

    private String mGenreName = null;
    private long mGenreId = -1;
    private String mArtistName = null;
    private long mArtistId = -1;
    private String mAlbumName = null;
    private long mAlbumId = -1;
    private String mMimeType = null;
    private File mFolder = null;
    private String mTrackName = null;

    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private volatile boolean mIsSupposedToBePlaying = false;
    private boolean mQueueIsSaveable = true;
    private volatile boolean mPausedByTransientLossOfFocus = false; // Used to track what type of audio focus loss caused the playback to pause
    private float[] mCurrentVolume = new float[2];
    private volatile int mCurrentPlayer;
    private volatile int mNextPlayer;
    private int mCardId; // Used to distinguish between different cards when saving/restoring playlists.


    // Local Binder pattern

    private final IBinder mLocalBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public MediaPlayback getService() {
            return MediaPlaybackService.this;
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(LOGTAG, "onCreate");

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mPersistentState = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        mCardId = fetchCardId();

        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        iFilter.addDataScheme("file");
        registerReceiver(mUnmountReceiver, iFilter);

        // Needs to be done in this thread, since otherwise ApplicationContext.getPowerManager() crashes.
        mPlayers[0] = new MyMediaPlayer(this, new Handler() {
            @Override
            public void handleMessage(Message msg) {
                handlePlayerCallback(0, msg);
            }
        });
        mPlayers[1] = new MyMediaPlayer(this, new Handler() {
            @Override
            public void handleMessage(Message msg) {
                handlePlayerCallback(1, msg);
            }
        });
        mCurrentPlayer = 0;
        mNextPlayer = 1;

        mCurrentVolume[0] = 1.0f;
        mCurrentVolume[1] = 1.0f;

        reloadQueue();

        IntentFilter actionFilter = new IntentFilter();
        actionFilter.addAction(TOGGLEPAUSE_ACTION);
        actionFilter.addAction(PLAY_ACTION);
        actionFilter.addAction(PAUSE_ACTION);
        actionFilter.addAction(STOP_ACTION);
        actionFilter.addAction(NEXT_ACTION);
        actionFilter.addAction(PREVIOUS_ACTION);
        enrichActionFilter(actionFilter);
        actionFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mIntentReceiver, actionFilter);

        createMediaSession();

        additionalCreate();

        // If the service was idle, but got killed before it stopped itself, the
        // system will relaunch it. Make sure it gets stopped again in that case.
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY_MILLIS);
    }

    @SuppressWarnings("unused")
    protected void enrichActionFilter(IntentFilter actionFilter) { }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void createMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mSession = new MediaSession(this, getString(R.string.applabel));
            mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        }
    }

    protected void additionalCreate() { }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOGTAG, "onStartCommand intent=" + intent + " flags=" + flags + " startId=" + startId);

        mServiceStartId = startId;
        mDelayedStopHandler.removeCallbacksAndMessages(null);

        if (intent != null) {
            String action = intent.getAction();

            if (PREVIOUS_ACTION.equals(action)) {
                previousOrRestartCurrent();
            } else if (NEXT_ACTION.equals(action)) {
                next();
            } else if (TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                } else {
                    play();
                }
            } else if (PLAY_ACTION.equals(action)) {
                play();
            } else if (PAUSE_ACTION.equals(action)) {
                pause();
            } else if (STOP_ACTION.equals(action)) {
                pause();
                seek(0);
                if (mSession != null) {
                    deactivateMediaSession();
                }
            }
        }

        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY_MILLIS);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        return mLocalBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mServiceInUse = false;

        // Take a snapshot of the current playlist
        saveQueue(true);

        if (isPlaying() || mPausedByTransientLossOfFocus) {
            // something is currently playing, or will be playing once
            // an in-progress action requesting audio focus ends, so don't stop the service now.
            return true;
        }

        // If there is a playlist but playback is paused, then wait a while
        // before stopping the service, so that pause/resume isn't slow.
        if (mPlayListLen > 0) {
            Message msg = mDelayedStopHandler.obtainMessage();
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY_MILLIS);
            return true;
        }

        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId);
        return true;
    }

    @Override
    public void onDestroy() {
        Log.i(LOGTAG, "onDestroy");

        // Check that we're not being destroyed while something is still playing.
        if (isPlaying()) {
            Log.e(LOGTAG, "Service being destroyed while still playing.");
        }

        additionalDestroy();

        if (mSession != null) {
            releaseMediaSession();
        }

        for (MyMediaPlayer player : mPlayers) player.release();

        mAudioManager.abandonAudioFocus(mAudioFocusListener);

        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mPlaybackHander.removeCallbacksAndMessages(null);

        unregisterReceiver(mIntentReceiver);
        unregisterReceiver(mUnmountReceiver);

        super.onDestroy();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void releaseMediaSession() {
        mSession.release();
    }

    protected void additionalDestroy() { }

    private synchronized void handlePlayerCallback(int player, Message msg) {
        switch (msg.what) {
            case MyMediaPlayer.SERVER_DIED:
                Log.d(LOGTAG, "MediaPlayer died: " + player);
                if (mIsSupposedToBePlaying) {
                    next();
                } else {
                    // the server died when we were idle, so just reopen the same song
                    // (it will start again from the beginning though when the user restarts)
                    if (mPlayListLen > 0) {
                        if (prepare(mPlayList[mPlayPos])) {
                            fetchMetadata(mPlayList[mPlayPos]);
                        }
                    }
                }
                break;

            case MyMediaPlayer.RELEASE_WAKELOCK:
                Log.d(LOGTAG, "MediaPlayer release wakelock: " + player);
                mPlayers[player].releaseWakeLock();
                break;


            case MyMediaPlayer.TRACK_ENDED:
                int fadeSeconds = Integer.parseInt(mSettings.getString(SettingsActivity.FADE_SECONDS, "0"));
                mPlaybackHander.removeMessages(DUCK);
                mPlaybackHander.removeMessages(FADEDOWN);
                mPlaybackHander.removeMessages(CROSSFADE);
                switch (mRepeatMode) {
                    case REPEAT_STOPAFTER:
                        Log.d(LOGTAG, "MediaPlayer track ended, REPEAT_STOPAFTER: " + player);
                        if (mSession != null) {
                            deactivateMediaSession();
                        }
                        gotoIdleState();
                        notifyChange(PLAYSTATE_CHANGED);
                        break;

                    case REPEAT_CURRENT:
                        Log.d(LOGTAG, "MediaPlayer track ended, REPEAT_CURRENT: " + player);
                        seek(0);
                        if (fadeSeconds > 0) {
                            mCurrentVolume[mCurrentPlayer] = 0f;
                        }
                        play();
                        break;

                    case REPEAT_NONE:
                    case REPEAT_ALL:
                        Log.d(LOGTAG, "MediaPlayer track ended, REPEAT_NONE/REPEAT_ALL: " + player);
                        if (mPlayListLen <= 0) {
                            if (mSession != null) {
                                deactivateMediaSession();
                            }
                            gotoIdleState();
                            notifyChange(PLAYSTATE_CHANGED);
                            break;
                        }

                        if (mPlayPos >= mPlayListLen - 1) {  // we're at the end of the list
                            if (mRepeatMode == REPEAT_NONE) {
                                if (mSession != null) {
                                    deactivateMediaSession();
                                }
                                gotoIdleState();
                                notifyChange(PLAYSTATE_CHANGED);
                                break;
                            } else {
                                mPlayPos = 0;
                            }
                        } else {
                            mPlayPos++;
                        }

                        if (mPlayers[mCurrentPlayer].isInitialized()) {
                            mPlayers[mCurrentPlayer].stop();
                        }

                        swapPlayers();

                        if (!mPlayers[mCurrentPlayer].isInitialized()) {
                            while (!prepare(mPlayList[mPlayPos])) {
                                if (mPlayPos >= mPlayListLen - 1) { // we're at the end of the list
                                    Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
                                    break;
                                } else {
                                    mPlayPos++;
                                }
                            }
                        }

                        if (!mPlayers[mCurrentPlayer].isPlaying()) {
                            if (fadeSeconds > 0) {
                                mCurrentVolume[mCurrentPlayer] = 0f;
                                mPlayers[mCurrentPlayer].setVolume(mCurrentVolume[mCurrentPlayer]);
                            }
                            Log.d(LOGTAG, "Starting playback");
                            mPlayers[mCurrentPlayer].start();

                            mPlaybackHander.sendMessage(mPlaybackHander.obtainMessage(FADEUP, mCurrentPlayer, 0));
                        }

                        fetchMetadata(mPlayList[mPlayPos]);
                        startForeground(PLAYBACKSERVICE_STATUS, buildNotification());
                        notifyChange(META_CHANGED);
                        break;
                }
                break;
        }
    }

    private void swapPlayers() {
        int tmp = mCurrentPlayer;
        mCurrentPlayer = mNextPlayer;
        mNextPlayer = tmp;
    }

    private final Handler mPlaybackHander = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int fadeSeconds = Integer.parseInt(mSettings.getString(SettingsActivity.FADE_SECONDS, "0"));

            switch (msg.what) {
                case DUCK:
                    // Log.v(LOGTAG, "handleMessage DUCK: " + msg.arg1);
                    mCurrentVolume[msg.arg1] -= .05f;
                    if (mCurrentVolume[msg.arg1] > .2f) {
                        mPlaybackHander.sendMessageDelayed(mPlaybackHander.obtainMessage(DUCK, msg.arg1, 0), 10);
                    } else {
                        mCurrentVolume[msg.arg1] = .2f;
                    }
                    mPlayers[msg.arg1].setVolume(mCurrentVolume[msg.arg1]);
                    break;

                case FADEDOWN:
                    // Log.v(LOGTAG, "handleMessage FADEDOWN: " + msg.arg1);
                    mCurrentVolume[msg.arg1] -= .01f / Math.max(fadeSeconds, 1);
                    if (mCurrentVolume[msg.arg1] > 0.0f) {
                        mPlaybackHander.sendMessageDelayed(mPlaybackHander.obtainMessage(FADEDOWN, msg.arg1, 0), 10);
                    } else {
                        mCurrentVolume[msg.arg1] = 0.0f;
                    }
                    mPlayers[msg.arg1].setVolume(mCurrentVolume[msg.arg1]);
                    break;

                case FADEUP:
                    // Log.v(LOGTAG, "handleMessage FADEUP: " + msg.arg1);
                    mCurrentVolume[msg.arg1] += .01f / Math.max(fadeSeconds, 1);
                    if (mCurrentVolume[msg.arg1] < 1.0f) {
                        mPlaybackHander.sendMessageDelayed(mPlaybackHander.obtainMessage(FADEUP, msg.arg1, 0), 10);
                    } else {
                        mCurrentVolume[msg.arg1] = 1.0f;
                        scheduleFadeOut();
                    }
                    mPlayers[msg.arg1].setVolume(mCurrentVolume[msg.arg1]);
                    break;

                case FOCUSCHANGE:
                    // This code is here so we can better synchronize it with the code that handles fade-in
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS");
                            audioFocusLoss();
                            mAudioManager.abandonAudioFocus(mAudioFocusListener);
                            pause();
                            if (mSession != null) {
                                deactivateMediaSession();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                            mPlaybackHander.removeMessages(FADEUP);
                            mPlaybackHander.removeMessages(FADEDOWN);
                            mPlaybackHander.removeMessages(CROSSFADE);
                            mPlaybackHander.sendMessage(mPlaybackHander.obtainMessage(DUCK, mCurrentPlayer, 0));
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");
                            boolean wasPlaying = isPlaying();
                            pause();
                            if (wasPlaying) {
                                mPausedByTransientLossOfFocus = true;
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                            audioFocusGain();
                            if (mSession != null) {
                                activateMediaSession();
                            }

                            if (!isPlaying() && mPausedByTransientLossOfFocus) {
                                mPausedByTransientLossOfFocus = false;
                                mCurrentVolume[mCurrentPlayer] = 0f;
                                mPlayers[mCurrentPlayer].setVolume(mCurrentVolume[mCurrentPlayer]);
                                play(); // also queues a fade-in
                            } else if (isPlaying()) {
                                mPlaybackHander.removeMessages(DUCK);
                                mPlaybackHander.removeMessages(FADEDOWN);
                                mPlaybackHander.removeMessages(CROSSFADE);
                                mPlaybackHander.sendMessage(mPlaybackHander.obtainMessage(FADEUP, mCurrentPlayer, 0));
                            }
                            break;
                        default:
                            Log.w(LOGTAG, "Unknown audio focus change code: " + msg.arg1);
                    }
                    break;

                case CROSSFADE:
                    Log.d(LOGTAG, "handleMessage CROSSFADE");
                    if (!mPlayers[mNextPlayer].isInitialized()) {
                        if ((mRepeatMode == REPEAT_NONE || mRepeatMode == REPEAT_ALL) && (mPlayPos + 1) < mPlayListLen) {
                            long nextId = mPlayList[mPlayPos + 1];
                            Log.d(LOGTAG, "Preparing next song " + nextId);
                            mPlayers[mNextPlayer].prepare(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + String.valueOf(nextId));
                        }
                    }
                    if (mPlayers[mNextPlayer].isInitialized()) {
                        if (fadeSeconds > 0) {
                            mCurrentVolume[mNextPlayer] = 0f;
                            mPlayers[mNextPlayer].setVolume(mCurrentVolume[mNextPlayer]);
                        }
                        Log.d(LOGTAG, "Cross-fading");
                        mPlayers[mNextPlayer].start();

                        mPlaybackHander.sendMessage(mPlaybackHander.obtainMessage(FADEUP, mNextPlayer, 0));

                        notifyChange(META_CHANGED);
                    }

                    break;
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void activateMediaSession() {
        mSession.setActive(true);
    }

    protected void audioFocusGain() { }

    protected void audioFocusLoss() {}

    private final Handler mDelayedStopHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Check again to make sure nothing is playing right now
            if (isPlaying() || mPausedByTransientLossOfFocus || mServiceInUse) {
                return;
            }
            // save the queue again, because it might have changed
            // since the user exited the music app (because of
            // the play-position changed)
            saveQueue(true);
            stopSelf(mServiceStartId);
        }
    };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(LOGTAG, "mIntentReceiver.onReceive: " + action);
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                pause();
            } else if (PREVIOUS_ACTION.equals(action)) {
                previousOrRestartCurrent();
            } else if (NEXT_ACTION.equals(action)) {
                next();
            } else if (TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                } else {
                    play();
                }
            } else if (PLAY_ACTION.equals(action)) {
                play();
            } else if (PAUSE_ACTION.equals(action)) {
                pause();
            } else if (STOP_ACTION.equals(action)) {
                pause();
                seek(0);
                if (mSession != null) {
                    deactivateMediaSession();
                }
            } else {
                handleAdditionalActions(intent);
            }
        }
    };

    @SuppressWarnings("unused")
    protected void handleAdditionalActions(Intent intent) { }

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    private final BroadcastReceiver mUnmountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                saveQueue(true);
                mQueueIsSaveable = false;
                closeExternalStorageFiles();
            } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                mCardId = fetchCardId();
                reloadQueue();
                mQueueIsSaveable = true;
                notifyChange(QUEUE_CHANGED);
                notifyChange(META_CHANGED);
            }
        }
    };

    private int fetchCardId() {
        Cursor c = getContentResolver().query(Uri.parse("content://media/external/fs_id"), null, null, null, null);
        int id = -1;
        if (c != null) {
            c.moveToFirst();
            id = c.getInt(0);
            c.close();
        }
        return id;
    }

    private final OnAudioFocusChangeListener mAudioFocusListener =
            focusChange -> mPlaybackHander.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();

    private void saveQueue(boolean full) {
        if (!mQueueIsSaveable) {
            return;
        }

        Editor ed = mPersistentState.edit();
        //long start = System.currentTimeMillis();
        if (full) {
            StringBuilder q = new StringBuilder();

            // The current playlist is saved as a list of "reverse hexadecimal"
            // numbers, which we can generate faster than normal decimal or
            // hexadecimal numbers, which in turn allows us to save the playlist
            // more often without worrying too much about performance.
            // (saving the full state takes about 40 ms under no-load conditions
            // on the phone)
            int len = mPlayListLen;
            for (int i = 0; i < len; i++) {
                long n = mPlayList[i];
                if (n == 0) {
                    q.append("0;");
                } else if (n > 0) {
                    while (n != 0) {
                        int digit = (int) (n & 0xf);
                        n >>>= 4;
                        q.append(HEXDIGITS[digit]);
                    }
                    q.append(";");
                }
            }
            ed.putString(SettingsActivity.PLAYQUEUE, q.toString());
            ed.putInt(SettingsActivity.CARDID, mCardId);
        }
        ed.putInt(SettingsActivity.CURPOS, mPlayPos);
        if (mPlayers[mCurrentPlayer].isInitialized()) {
            ed.putLong(SettingsActivity.SEEKPOS, mPlayers[mCurrentPlayer].currentPosition());
        }
        ed.putInt(SettingsActivity.REPEATMODE, mRepeatMode);
        ed.apply();
    }

    private void reloadQueue() {
        String q = null;

        int id = mCardId;
        if (mPersistentState.contains(SettingsActivity.CARDID)) {
            id = mPersistentState.getInt(SettingsActivity.CARDID, ~mCardId);
        }
        if (id == mCardId) {
            // Only restore the saved playlist if the card is still
            // the same one as when the playlist was saved
            q = mPersistentState.getString(SettingsActivity.PLAYQUEUE, "");
        }
        int qlen = q != null ? q.length() : 0;
        if (qlen > 1) {
            int plen = 0;
            int n = 0;
            int shift = 0;
            for (int i = 0; i < qlen; i++) {
                char c = q.charAt(i);
                if (c == ';') {
                    ensurePlayListCapacity(plen + 1);
                    mPlayList[plen] = n;
                    plen++;
                    n = 0;
                    shift = 0;
                } else {
                    if (c >= '0' && c <= '9') {
                        n += ((c - '0') << shift);
                    } else if (c >= 'a' && c <= 'f') {
                        n += ((10 + c - 'a') << shift);
                    } else {
                        // bogus playlist data
                        plen = 0;
                        break;
                    }
                    shift += 4;
                }
            }
            mPlayListLen = plen;

            int pos = mPersistentState.getInt(SettingsActivity.CURPOS, 0);
            if (pos < 0 || pos >= mPlayListLen) {
                // The saved playlist is bogus, discard it
                mPlayListLen = 0;
                return;
            }
            mPlayPos = pos;

            // When reloadQueue is called in response to a card-insertion,
            // we might not be able to query the media provider right away.
            // To deal with this, try querying for the current file, and if
            // that fails, wait a while and try again. If that too fails,
            // assume there is a problem and don't restore the state.
            Cursor crsr = MusicUtils.query(this,
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{"_id"}, "_id=" + mPlayList[mPlayPos], null, null);
            if (crsr == null || crsr.getCount() == 0) {
                // wait a bit and try again
                SystemClock.sleep(3000);
                crsr = getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        CURSOR_COLS, "_id=" + mPlayList[mPlayPos], null, null);
            }
            if (crsr != null) {
                crsr.close();
            }

            if (mPlayListLen > 0) {
                stop();
                if (prepare(mPlayList[mPlayPos])) {
                    fetchMetadata(mPlayList[mPlayPos]);
                } else {
                    mPlayListLen = 0;
                    return;
                }
            }

            long seekpos = mPersistentState.getLong(SettingsActivity.SEEKPOS, 0);
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);

            int repmode = mPersistentState.getInt(SettingsActivity.REPEATMODE, REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;
        }
    }

    /**
     * Called when we receive a ACTION_MEDIA_EJECT notification.
     */
    private void closeExternalStorageFiles() {
        // stop playback and clean up if the SD card is going to be unmounted.
        stop();
        if (mSession != null) {
            deactivateMediaSession();
        }
        gotoIdleState();
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
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
    private void notifyChange(String what) {
        Intent i = new Intent(what);
        i.putExtra("id", getAudioId());
        i.putExtra("artist", getArtistName());
        i.putExtra("album", getAlbumName());
        i.putExtra("genre", getGenreName());
        i.putExtra("track", getTrackName());
        i.putExtra("playing", isPlaying());
        sendStickyBroadcast(i);

        if (what.equals(QUEUE_CHANGED)) {
            saveQueue(true);
        } else {
            saveQueue(false);
        }

        extraNotifyChange(what);
    }

    @SuppressWarnings("unused")
    protected void extraNotifyChange(String what) { }

    private void ensurePlayListCapacity(int size) {
        if (size > mPlayList.length) {
            // reallocate at 2x requested size so we don't
            // need to grow and copy the array for every
            // insert
            long[] newlist = new long[size * 2];
            System.arraycopy(mPlayList, 0, newlist, 0, mPlayList.length);
            mPlayList = newlist;
        }
        // FIXME: shrink the array when the needed size is much smaller
        // than the allocated size
    }

    // insert the list of songs at the specified position in the playlist
    private void addToPlayList(long[] list, int position) {
        addToPlaylistInternal(list, position);
        updatePlaylist();
    }

    private void addToPlaylistInternal(long[] list, int position) {
        if (position < 0) { // overwrite
            mPlayListLen = 0;
            position = 0;
        }
        ensurePlayListCapacity(mPlayListLen + list.length);
        if (position > mPlayListLen) {
            position = mPlayListLen;
        }

        // move part of list after insertion point
        int tailsize = mPlayListLen - position;
        if (tailsize > 0) System.arraycopy(mPlayList, position, mPlayList, position + list.length, tailsize);

        // copy list into playlist
        System.arraycopy(list, 0, mPlayList, position, list.length);
        mPlayListLen += list.length;
    }

    private void updatePlaylist() {
        if (mPlayListLen == 0) {
            resetMetadata();
            notifyChange(META_CHANGED);
        }
        notifyChange(QUEUE_CHANGED);
    }

    @Override
    public synchronized void enqueue(long[] list, int action) {
        if (list.length == 0) return;

        if ((action == NEXT || action == NOW) && mPlayPos + 1 < mPlayListLen) {
            addToPlayList(list, mPlayPos + 1);
            if (action == NOW) {
                stop();
                mPlayPos++;
                prepareAndPlay(mPlayList[mPlayPos]);
                return;
            }
        } else {
            addToPlayList(list, Integer.MAX_VALUE);
            if (action == NOW) {
                stop();
                mPlayPos = mPlayListLen - list.length;
                prepareAndPlay(mPlayList[mPlayPos]);
                return;
            }
        }
        if (mPlayPos < 0) {
            stop();
            mPlayPos = 0;
            prepareAndPlay(mPlayList[mPlayPos]);
        }
    }

    private void prepareAndPlay(long audioId) {
        if (prepare(audioId)) {
            fetchMetadata(audioId);
            play();
            notifyChange(META_CHANGED);
        } else {
            Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public synchronized void interleave(long[] newList, int currentCount, int newCount) {
        long[] destList = new long[mPlayListLen + newList.length];

        int destI = 0;
        int currentI = 0;
        int newI = 0;
        while (destI < destList.length) {
            for (int i = 0; i < currentCount; i++) {
                if (currentI >= mPlayListLen) break;
                destList[destI++] = mPlayList[currentI++];
            }
            for (int i = 0; i < newCount; i++) {
                if (newI >= newList.length) break;
                destList[destI++] = newList[newI++];
            }
        }

        mPlayList = destList;
        mPlayListLen = mPlayList.length;
        updatePlaylist();
    }

    @Override
    public synchronized void load(long[] list, int position) {
        int listlength = list.length;
        boolean newlist = true;
        if (mPlayListLen == listlength) {
            // possible fast path: list might be the same
            newlist = false;
            for (int i = 0; i < listlength; i++) {
                if (list[i] != mPlayList[i]) {
                    newlist = true;
                    break;
                }
            }
        }
        if (newlist) {
            addToPlayList(list, -1);
        }
        if (position >= 0) {
            mPlayPos = position;
        } else {
            mPlayPos = 0;
        }

        stop();
        prepareAndPlay(mPlayList[mPlayPos]);
    }

    @Override
    public synchronized void moveQueueItem(int index1, int index2) {
        if (index1 >= mPlayListLen) {
            index1 = mPlayListLen - 1;
        }
        if (index2 >= mPlayListLen) {
            index2 = mPlayListLen - 1;
        }
        if (index1 < index2) {
            long tmp = mPlayList[index1];
            System.arraycopy(mPlayList, index1 + 1, mPlayList, index1, index2 - index1);
            mPlayList[index2] = tmp;
            if (mPlayPos == index1) {
                mPlayPos = index2;
            } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                mPlayPos--;
            }
        } else if (index2 < index1) {
            long tmp = mPlayList[index1];
            System.arraycopy(mPlayList, index2, mPlayList, index2 + 1, index1 - index2);
            mPlayList[index2] = tmp;
            if (mPlayPos == index1) {
                mPlayPos = index2;
            } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                mPlayPos++;
            }
        }
        notifyChange(QUEUE_CHANGED);
    }

    @Override
    public synchronized long[] getQueue() {
        int len = mPlayListLen;
        long[] list = new long[len];
        System.arraycopy(mPlayList, 0, list, 0, len);
        return list;
    }

    @Override
    public synchronized int getQueueLength() {
        return mPlayListLen;
    }

    private boolean prepare(long audioId) {
        Log.d(LOGTAG, "Preparing song " + audioId);
        return mPlayers[mCurrentPlayer].prepare(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + String.valueOf(audioId));
    }

    private void fetchMetadata(long audioId) {
        resetMetadata();
        Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                CURSOR_COLS, "_id=" + String.valueOf(audioId), null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    mArtistName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST));
                    mArtistId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST_ID));
                    mAlbumName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM));
                    mAlbumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID));
                    mMimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE));
                    mFolder = new File(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA))).getParentFile();
                    mTrackName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE));

                    IdAndName idAndName = MusicUtils.fetchGenre(this, audioId);
                    if (idAndName != null) {
                        mGenreId = idAndName.id;
                        mGenreName = idAndName.name;
                    }

                    if (mSession != null) {
                        updateMediaMetadata();
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

    private void resetMetadata() {
        mGenreName = null;
        mGenreId = -1;
        mArtistName = null;
        mArtistId = -1;
        mAlbumName = null;
        mAlbumId = -1;
        mMimeType = null;
        mFolder = null;
        mTrackName = null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void updateMediaMetadata() {
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, getTrackName());
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, getArtistName());
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_GENRE, getGenreName());
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, getAlbumName());

        metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART,
                BitmapFactory.decodeResource(getResources(), R.drawable.app_icon));
        // TODO set small icon

        mSession.setMetadata(metadataBuilder.build());
    }

    @Override
    public synchronized void play() {
        int result = mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(LOGTAG, "Unable to gain audio focus: " + result);
            return;
        }

        beforePlay();

        if (mPlayers[mCurrentPlayer].isInitialized()) {
            // if we are at the end of the song, go to the next song first
            long duration = mPlayers[mCurrentPlayer].duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > NEXT_THRESHOLD_MILLIS &&
                    mPlayers[mCurrentPlayer].currentPosition() >= duration - NEXT_THRESHOLD_MILLIS) {
                next();
            }

            if (mSession != null) {
                activateMediaSession();
                updateMediaSession(true);
            }

            mPlayers[mCurrentPlayer].start();
            // make sure we fade in, in case a previous fadein was stopped because of another focus loss
            mPlaybackHander.removeMessages(DUCK);
            mPlaybackHander.removeMessages(FADEDOWN);
            mPlaybackHander.removeMessages(CROSSFADE);
            mPlaybackHander.sendMessage(mPlaybackHander.obtainMessage(FADEUP, mCurrentPlayer, 0));

            startForeground(PLAYBACKSERVICE_STATUS, buildNotification());

            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }
        }
    }

    protected void beforePlay() { }

    private Notification buildNotification() {
        String trackName;
        String artistName;
        if (getAudioId() < 0) { // streaming
            trackName = getString(R.string.streaming);
            artistName = null;
        } else {
            trackName = getTrackName();
            artistName = getArtistName();
            if (artistName == null || artistName.equals(MediaStore.UNKNOWN_STRING)) {
                artistName = getString(R.string.unknown_artist_name);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.stat_notify_musicplayer);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.app_icon));
        builder.setContentTitle(trackName);
        builder.setContentText(artistName);
        builder.setOngoing(true);
        builder.setWhen(0);
        builder.addAction(android.R.drawable.ic_media_previous, getResources().getString(R.string.prev),
                getPendingIntentForAction(PREVIOUS_ACTION));
        builder.addAction(android.R.drawable.ic_media_pause, getResources().getString(R.string.pause),
                getPendingIntentForAction(PAUSE_ACTION));
        builder.addAction(android.R.drawable.ic_media_next, getResources().getString(R.string.next),
                getPendingIntentForAction(NEXT_ACTION));
        builder.setStyle(new NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2));

        enrichNotification(builder);

        applyLillipopFunctionality(builder);

        return builder.build();
    }

    @SuppressWarnings("unused")
    protected void enrichNotification(NotificationCompat.Builder builder) { }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void applyLillipopFunctionality(NotificationCompat.Builder builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder
                    .setCategory(Notification.CATEGORY_TRANSPORT)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);
        }
    }

    private PendingIntent getPendingIntentForAction(String action) {
        return PendingIntent.getService(this, 0, new Intent(action).setClass(this, getClass()), 0);
    }

    private void stop() {
        mPlaybackHander.removeMessages(DUCK);
        mPlaybackHander.removeMessages(FADEUP);
        mPlaybackHander.removeMessages(FADEDOWN);
        mPlaybackHander.removeMessages(CROSSFADE);
        for (MyMediaPlayer player : mPlayers) player.stop();
        resetMetadata();
    }

    @Override
    public synchronized void pause() {
        mPlaybackHander.removeMessages(DUCK);
        mPlaybackHander.removeMessages(FADEUP);
        mPlaybackHander.removeMessages(FADEDOWN);
        mPlaybackHander.removeMessages(CROSSFADE);

        boolean wasPlaying = isPlaying();

        for (MyMediaPlayer player : mPlayers) {
            if (player.isPlaying()) player.pause();
        }
        gotoIdleState();

        if (wasPlaying) {
            notifyChange(PLAYSTATE_CHANGED);
        }

        mPausedByTransientLossOfFocus = false;
    }

    @Override
    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

    @Override
    public void previousOrRestartCurrent() {
        if (position() < PREV_THRESHOLD_MILLIS) {
            previous();
        } else {
            seek(0);
        }
    }

    @Override
    public synchronized void previous() {
        if (mPlayListLen <= 0) return;

        if (mPlayPos > 0) {
            mPlayPos--;
        } else {
            mPlayPos = mPlayListLen - 1;
        }
        stop();
        prepareAndPlay(mPlayList[mPlayPos]);
    }

    @Override
    public synchronized void next() {
        if (mPlayListLen <= 0) return;

        if (mPlayPos >= mPlayListLen - 1) {
            // we're at the end of the list
            mPlayPos = 0;
        } else {
            mPlayPos++;
        }
        stop();
        prepareAndPlay(mPlayList[mPlayPos]);
    }

    private void gotoIdleState() {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendMessageDelayed(mDelayedStopHandler.obtainMessage(), IDLE_DELAY_MILLIS);
        stopForeground(true);
        if (mSession != null) {
            if (isMediaSessionActive()) {
                updateMediaSession(false);
            }
        }
        mIsSupposedToBePlaying = false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean isMediaSessionActive() {
        return mSession.isActive();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void updateMediaSession(boolean isPlaying) {
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(isPlaying ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY);
        stateBuilder.setState(isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                mPlayers[mCurrentPlayer].currentPosition(), 1.0f);
        mSession.setPlaybackState(stateBuilder.build());
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void deactivateMediaSession() {
        mSession.setActive(false);
    }

    @Override
    public synchronized int removeTracks(int first, int last) {
        int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    private int removeTracksInternal(int first, int last) {
        if (last < first) return 0;
        if (first < 0) first = 0;
        if (last >= mPlayListLen) last = mPlayListLen - 1;

        boolean gotonext = false;
        if (first <= mPlayPos && mPlayPos <= last) {
            mPlayPos = first;
            gotonext = true;
        } else if (mPlayPos > last) {
            mPlayPos -= (last - first + 1);
        }
        int num = mPlayListLen - last - 1;
        for (int i = 0; i < num; i++) {
            mPlayList[first + i] = mPlayList[last + 1 + i];
        }
        mPlayListLen -= last - first + 1;

        if (gotonext) {
            if (mPlayListLen == 0) {
                stop();
                if (mSession != null) {
                    deactivateMediaSession();
                }
                gotoIdleState();
                mPlayPos = -1;
            } else {
                if (mPlayPos >= mPlayListLen) {
                    mPlayPos = 0;
                }
                boolean wasPlaying = isPlaying();
                stop();

                if (prepare(mPlayList[mPlayPos])) {
                    fetchMetadata(mPlayList[mPlayPos]);
                    if (wasPlaying) play();
                    notifyChange(META_CHANGED);
                }
            }
        }
        return last - first + 1;
    }

    @Override
    public synchronized int removeTrack(long id) {
        int numremoved = 0;
        for (int i = 0; i < mPlayListLen; i++) {
            if (mPlayList[i] == id) {
                numremoved += removeTracksInternal(i, i);
                i--;
            }
        }
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    @Override
    public synchronized void doShuffle() {
        Random random = new Random();
        for (int i = 0; i < mPlayListLen; i++) {
            if (i != mPlayPos) {
                int randomPosition = random.nextInt(mPlayListLen);
                while (randomPosition == mPlayPos) randomPosition = random.nextInt(mPlayListLen);
                long temp = mPlayList[i];
                mPlayList[i] = mPlayList[randomPosition];
                mPlayList[randomPosition] = temp;
            }
        }
        notifyChange(QUEUE_CHANGED);
    }

    @Override
    public synchronized void uniqueify() {
        if (!isPlaying()) {
            boolean modified = false;
            Set<Long> found = new HashSet<>();
            for (int i = mPlayListLen - 1; i >= 0; i--) {
                if (!found.add(mPlayList[i])) {
                    removeTracksInternal(i, i);
                    modified = true;
                }
            }
            if (modified) {
                notifyChange(QUEUE_CHANGED);
            }
        }
    }

    @Override
    public synchronized void setRepeatMode(int repeatmode) {
        mRepeatMode = repeatmode;
        saveQueue(false);
    }

    @Override
    public synchronized int getRepeatMode() {
        return mRepeatMode;
    }

    @Override
    public synchronized long getAudioId() {
        if (mPlayPos >= 0 && mPlayers[mCurrentPlayer].isInitialized()) {
            return mPlayList[mPlayPos];
        } else {
            return -1;
        }
    }

    @Override
    public synchronized long getCrossfadeAudioId() {
        if (mPlayPos >= 0 && mPlayers[mNextPlayer].isPlaying()) {
            return mPlayList[mPlayPos + 1];
        } else {
            return -1;
        }
    }

    @Override
    public synchronized int getQueuePosition() {
        return mPlayPos;
    }

    @Override
    public synchronized int getCrossfadeQueuePosition() {
        if (mPlayPos >= 0 && mPlayers[mNextPlayer].isPlaying()) {
            return mPlayPos + 1;
        } else {
            return -1;
        }
    }

    @Override
    public synchronized void setQueuePosition(int pos) {
        if (pos > mPlayListLen - 1) return;
        stop();
        mPlayPos = pos;
        prepareAndPlay(mPlayList[mPlayPos]);
    }

    @Override
    public synchronized String getArtistName() {
        return mArtistName;
    }

    @Override
    public synchronized long getArtistId() {
        return mArtistId;
    }

    @Override
    public synchronized String getAlbumName() {
        return mAlbumName;
    }

    @Override
    public synchronized long getAlbumId() {
        return mAlbumId;
    }

    @Override
    public synchronized String getGenreName() {
        return mGenreName;
    }

    @Override
    public synchronized long getGenreId() {
        return mGenreId;
    }

    @Override
    public synchronized String getMimeType() {
        return mMimeType;
    }

    @Override
    public synchronized File getFolder() {
        return mFolder;
    }

    @Override
    public synchronized String getTrackName() {
        return mTrackName;
    }

    @Override
    public long duration() {
        if (mPlayers[mCurrentPlayer].isInitialized()) {
            return mPlayers[mCurrentPlayer].duration();
        }
        return -1;
    }

    @Override
    public long position() {
        if (mPlayers[mCurrentPlayer].isInitialized()) {
            return mPlayers[mCurrentPlayer].currentPosition();
        }
        return -1;
    }

    @Override
    public synchronized void seek(long pos) {
        if (mPlayers[mCurrentPlayer].isInitialized()) {
            mPlaybackHander.removeMessages(DUCK);
            mPlaybackHander.removeMessages(FADEUP);
            mPlaybackHander.removeMessages(FADEDOWN);
            mPlaybackHander.removeMessages(CROSSFADE);
            if (pos < 0) pos = 0;
            if (pos > mPlayers[mCurrentPlayer].duration()) pos = mPlayers[mCurrentPlayer].duration();
            mPlayers[mCurrentPlayer].seek(pos);

            if (mIsSupposedToBePlaying) {
                scheduleFadeOut();
            }
        }
    }

    private void scheduleFadeOut() {
        int fadeOutSeconds = Integer.parseInt(mSettings.getString(SettingsActivity.FADE_SECONDS, "0"));
        boolean crossFade = mSettings.getBoolean(SettingsActivity.CROSS_FADE, false);

        if (fadeOutSeconds > 0) {
            long timeLeftMillis = mPlayers[mCurrentPlayer].duration() - mPlayers[mCurrentPlayer].currentPosition();
            if (timeLeftMillis > 0) {
                long delayMillis = timeLeftMillis - fadeOutSeconds * 1000;
                Log.d(LOGTAG, "Scheduling fade out " + fadeOutSeconds + " seconds with cross-fade=" + crossFade + " in " + delayMillis + " ms");
                if (crossFade) {
                    mPlaybackHander.sendEmptyMessageDelayed(CROSSFADE, delayMillis);
                }
                mPlaybackHander.sendMessageDelayed(mPlaybackHander.obtainMessage(FADEDOWN, mCurrentPlayer, 0), delayMillis);
            } else {
                Log.w(LOGTAG, "timeLeft is " + timeLeftMillis + " ms");
            }
        }
    }

    @Override
    public int getAudioSessionId() {
        return mPlayers[mCurrentPlayer].getAudioSessionId();
    }

}
