/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012-2015 Mikael Ståldal
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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.File;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class MediaPlaybackService extends Service implements MediaPlayback {
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
    public static final String APPWIDGETUPDATE_ACTION = "nu.staldal.djdplayer.musicservicecommand.appwidgetupdate";

    private static final int PLAYBACKSERVICE_STATUS = 1;

    private static final int FOCUSCHANGE = 4;
    private static final int DUCK = 5;
    private static final int FADEUP = 6;
    private static final int FADEDOWN = 7;

    private static final char HEXDIGITS[] = new char[]{
            '0', '1', '2', '3',
            '4', '5', '6', '7',
            '8', '9', 'a', 'b',
            'c', 'd', 'e', 'f'
    };

    private static final String[] CURSOR_COLS = new String[]{
            "audio._id AS _id",             // index must match IDCOLIDX below
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.DATA,
            MediaStore.Audio.AudioColumns.MIME_TYPE,
            MediaStore.Audio.AudioColumns.ALBUM_ID,
            MediaStore.Audio.AudioColumns.ARTIST_ID,
            MediaStore.Audio.AudioColumns.IS_PODCAST, // index must match PODCASTCOLIDX below
            MediaStore.Audio.AudioColumns.BOOKMARK    // index must match BOOKMARKCOLIDX below
    };
    private static final int IDCOLIDX = 0;
    private static final int PODCASTCOLIDX = 8;
    private static final int BOOKMARKCOLIDX = 9;

    /**
     * Interval after which we stop the service when idle.
     */
    private static final int IDLE_DELAY = 60000;


    // Delegates

    private AudioManager mAudioManager;
    private SharedPreferences mPersistentState;
    private SharedPreferences mSettings;
    private MyMediaPlayer mPlayer;
    private RemoteControlClient mRemoteControlClient;


    // Mutable state

    private int mRepeatMode = REPEAT_NONE;
    private long[] mPlayList = new long[0];
    private int mPlayListLen = 0;
    private int mPlayPos = -1;
    private Cursor mCursor = null;
    private long mGenreId = -1;
    private String mGenreName = null;
    private int mOpenFailedCounter = 0;
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQuietMode = false;
    private boolean mQueueIsSaveable = true;
    private boolean mPausedByTransientLossOfFocus = false; // Used to track what type of audio focus loss caused the playback to pause
    private float mCurrentVolume = 1.0f;
    private int mCardId; // Used to distinguish between different cards when saving/restoring playlists.


    private final Handler mMediaplayerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(LOGTAG, "handleMessage " + msg.what);
            int fadeOutSeconds = Integer.parseInt(mSettings.getString(SettingsActivity.FADE_OUT_SECONDS, "0"));
            int fadeInSeconds = Integer.parseInt(mSettings.getString(SettingsActivity.FADE_IN_SECONDS, "0"));
            switch (msg.what) {
                case DUCK:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(DUCK, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    mPlayer.setVolume(mCurrentVolume);
                    break;
                case FADEDOWN:
                    mCurrentVolume -= .01f / Math.max(fadeOutSeconds, 1);
                    if (mCurrentVolume > 0.0f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        mCurrentVolume = 0.0f;
                    }
                    mPlayer.setVolume(mCurrentVolume);
                    break;
                case FADEUP:
                    mCurrentVolume += .01f / Math.max(fadeInSeconds, 1);
                    if (mCurrentVolume < 1.0f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEUP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                        if (fadeOutSeconds > 0) {
                            long timeLeftMillis = mPlayer.duration() - mPlayer.currentPosition();
                            mMediaplayerHandler.sendEmptyMessageDelayed(FADEDOWN, timeLeftMillis-fadeOutSeconds*1000);
                        }
                    }
                    mPlayer.setVolume(mCurrentVolume);
                    break;
                case MyMediaPlayer.SERVER_DIED:
                    if (mIsSupposedToBePlaying) {
                        next(true);
                    } else {
                        // the server died when we were idle, so just
                        // reopen the same song (it will start again
                        // from the beginning though when the user
                        // restarts)
                        openCurrent();
                    }
                    break;
                case MyMediaPlayer.TRACK_ENDED:
                    switch (mRepeatMode) {
                        case REPEAT_CURRENT:
                            seek(0);
                            if (fadeInSeconds > 0) {
                                mCurrentVolume = 0f;
                            }
                            play();
                            break;

                        case REPEAT_STOPAFTER:
                            gotoIdleState();
                            mIsSupposedToBePlaying = false;
                            notifyChange(PLAYSTATE_CHANGED);
                            break;

                        default:
                            if (fadeInSeconds > 0) {
                                mCurrentVolume = 0f;
                            }
                            next(false);
                    }
                    break;
                case MyMediaPlayer.RELEASE_WAKELOCK:
                    mPlayer.releaseWakeLock();
                    break;

                case FOCUSCHANGE:
                    // This code is here so we can better synchronize it with the code that handles fade-in
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS");
                            mAudioManager.unregisterMediaButtonEventReceiver(new ComponentName(
                                    MediaPlaybackService.this.getPackageName(), MediaButtonIntentReceiver.class.getName()));
                            mAudioManager.unregisterRemoteControlClient(mRemoteControlClient);
                            mAudioManager.abandonAudioFocus(mAudioFocusListener);
                            if (isPlaying()) {
                                mPausedByTransientLossOfFocus = false;
                            }
                            pause();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                            mMediaplayerHandler.removeMessages(FADEUP);
                            mMediaplayerHandler.removeMessages(FADEDOWN);
                            mMediaplayerHandler.sendEmptyMessage(DUCK);
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");
                            if (isPlaying()) {
                                mPausedByTransientLossOfFocus = true;
                            }
                            pause();
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                            mAudioManager.registerMediaButtonEventReceiver(new ComponentName(
                                    MediaPlaybackService.this.getPackageName(), MediaButtonIntentReceiver.class.getName()));
                            mAudioManager.registerRemoteControlClient(mRemoteControlClient);

                            if (!isPlaying() && mPausedByTransientLossOfFocus) {
                                mPausedByTransientLossOfFocus = false;
                                mCurrentVolume = 0f;
                                mPlayer.setVolume(mCurrentVolume);
                                play(); // also queues a fade-in
                            } else {
                                mMediaplayerHandler.removeMessages(DUCK);
                                mMediaplayerHandler.removeMessages(FADEDOWN);
                                mMediaplayerHandler.sendEmptyMessage(FADEUP);
                            }
                            break;
                        default:
                            Log.e(LOGTAG, "Unknown audio focus change code");
                    }
                    break;

                default:
                    break;
            }
        }
    };

    private final Handler mDelayedStopHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Check again to make sure nothing is playing right now
            if (isPlaying() || mPausedByTransientLossOfFocus || mServiceInUse
                    || mMediaplayerHandler.hasMessages(MyMediaPlayer.TRACK_ENDED)) {
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
                mPausedByTransientLossOfFocus = false;
            } else if (NEXT_ACTION.equals(action)) {
                next(true);
            } else if (PREVIOUS_ACTION.equals(action)) {
                prev();
            } else if (TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
            } else if (PLAY_ACTION.equals(action)) {
                play();
            } else if (PAUSE_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else if (STOP_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                seek(0);
            } else if (APPWIDGETUPDATE_ACTION.equals(action)) {
                // Someone asked us to refresh a set of specific widgets, probably
                // because they were just added.
                int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                MediaAppWidgetProvider.getInstance().performUpdate(MediaPlaybackService.this, appWidgetIds);
            }
        }
    };

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
                mCardId = MusicUtils.getCardId(MediaPlaybackService.this);
                reloadQueue();
                mQueueIsSaveable = true;
                notifyChange(QUEUE_CHANGED);
                notifyChange(META_CHANGED);
            }
        }
    };

    private final OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            mMediaplayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };

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

        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(this.getPackageName(),
                MediaButtonIntentReceiver.class.getName()));

        mPersistentState = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        mCardId = MusicUtils.getCardId(this);

        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        iFilter.addDataScheme("file");
        registerReceiver(mUnmountReceiver, iFilter);

        // Needs to be done in this thread, since otherwise ApplicationContext.getPowerManager() crashes.
        mPlayer = new MyMediaPlayer(this, mMediaplayerHandler);

        reloadQueue();

        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PLAY_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(STOP_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        commandFilter.addAction(PREVIOUS_ACTION);
        commandFilter.addAction(APPWIDGETUPDATE_ACTION);
        commandFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mIntentReceiver, commandFilter);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(new ComponentName(this.getPackageName(),
                MediaButtonIntentReceiver.class.getName()));
        mRemoteControlClient = new RemoteControlClient(
                PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, 0));
        mRemoteControlClient.setTransportControlFlags(
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE |
                        RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
                        RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS);

        // If the service was idle, but got killed before it stopped itself, the
        // system will relaunch it. Make sure it gets stopped again in that case.
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOGTAG, "onStartCommand intent=" + intent + " flags=" + flags + " startId=" + startId);

        mServiceStartId = startId;
        mDelayedStopHandler.removeCallbacksAndMessages(null);

        if (intent != null) {
            String action = intent.getAction();

            if (NEXT_ACTION.equals(action)) {
                next(true);
            } else if (PREVIOUS_ACTION.equals(action)) {
                if (position() < 2000) {
                    prev();
                } else {
                    seek(0);
                    play();
                }
            } else if (TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
            } else if (PLAY_ACTION.equals(action)) {
                play();
            } else if (PAUSE_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else if (STOP_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                seek(0);
            }
        }

        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
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
        // Also delay stopping the service if we're transitioning between tracks.
        if (mPlayListLen > 0 || mMediaplayerHandler.hasMessages(MyMediaPlayer.TRACK_ENDED)) {
            Message msg = mDelayedStopHandler.obtainMessage();
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
            return true;
        }

        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId);
        return true;
    }

    @Override
    public void onDestroy() {
        // Check that we're not being destroyed while something is still playing.
        if (isPlaying()) {
            Log.e(LOGTAG, "Service being destroyed while still playing.");
        }

        mAudioManager.unregisterMediaButtonEventReceiver(new ComponentName(this.getPackageName(),
                MediaButtonIntentReceiver.class.getName()));
        mAudioManager.unregisterRemoteControlClient(mRemoteControlClient);

        mPlayer.release();

        mAudioManager.abandonAudioFocus(mAudioFocusListener);

        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mMediaplayerHandler.removeCallbacksAndMessages(null);

        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            mGenreId = -1;
            mGenreName = null;
        }

        unregisterReceiver(mIntentReceiver);
        unregisterReceiver(mUnmountReceiver);

        super.onDestroy();
    }

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
                        int digit = (int)(n & 0xf);
                        n >>>= 4;
                        q.append(HEXDIGITS[digit]);
                    }
                    q.append(";");
                }
            }
            //Log.i("@@@@ service", "created queue string in " + (System.currentTimeMillis() - start) + " ms");
            ed.putString(SettingsActivity.PLAYQUEUE, q.toString());
            ed.putInt(SettingsActivity.CARDID, mCardId);
        }
        ed.putInt(SettingsActivity.CURPOS, mPlayPos);
        if (mPlayer.isInitialized()) {
            ed.putLong(SettingsActivity.SEEKPOS, mPlayer.currentPosition());
        }
        ed.putInt(SettingsActivity.REPEATMODE, mRepeatMode);
        ed.apply();

        //Log.i("@@@@ service", "saved state in " + (System.currentTimeMillis() - start) + " ms");
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
            //Log.i("@@@@ service", "loaded queue: " + q);
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

            // Make sure we don't auto-skip to the next song, since that
            // also starts playback. What could happen in that case is:
            // - music is paused
            // - go to UMS and delete some files, including the currently playing one
            // - come back from UMS
            // (time passes)
            // - music app is killed for some reason (out of memory)
            // - music service is restarted, service restores state, doesn't find
            //   the "current" file, goes to the next and: playback starts on its
            //   own, potentially at some random inconvenient time.
            mOpenFailedCounter = 20;
            mQuietMode = true;
            openCurrent();
            mQuietMode = false;
            if (!mPlayer.isInitialized()) {
                // couldn't restore the saved state
                mPlayListLen = 0;
                return;
            }

            long seekpos = mPersistentState.getLong(SettingsActivity.SEEKPOS, 0);
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);
            //Log.d(LOGTAG, "restored queue, currently at position " + position() + "/" + duration() + " (requested " + seekpos + ")");

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
        stop(true);
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
    }

    /**
     * Notify the change-receivers that something has changed.
     * The intent that is sent contains the following data
     * for the currently playing track:
     * "id" - Integer: the database row ID
     * "artist" - String: the name of the artist
     * "album" - String: the name of the album
     * "track" - String: the name of the track
     * The intent has an action that is one of
     * "nu.staldal.djdplayer.metachanged"
     * "nu.staldal.djdplayer.queuechanged",
     * "nu.staldal.djdplayer.playbackcomplete"
     * "nu.staldal.djdplayer.playstatechanged"
     * respectively indicating that a new track has
     * started playing, that the playback queue has
     * changed, that playback has stopped because
     * the last file in the list has been played,
     * or that the play-state changed (paused/resumed).
     */
    private void notifyChange(String what) {
        Intent i = new Intent(what);
        i.putExtra("id", Long.valueOf(getAudioId()));
        i.putExtra("artist", getArtistName());
        i.putExtra("album", getAlbumName());
        i.putExtra("track", getTrackName());
        i.putExtra("playing", isPlaying());
        sendStickyBroadcast(i);

        if (what.equals(QUEUE_CHANGED)) {
            saveQueue(true);
        } else {
            saveQueue(false);
        }

        // Share this notification directly with our widgets
        MediaAppWidgetProvider.getInstance().notifyChange(this, what);

        mRemoteControlClient.setPlaybackState(isPlaying()
                ? RemoteControlClient.PLAYSTATE_PLAYING
                : RemoteControlClient.PLAYSTATE_PAUSED);

        RemoteControlClient.MetadataEditor metadataEditor = mRemoteControlClient.editMetadata(true);
        metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, getTrackName());
        metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, getArtistName());
        metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, getArtistName());
        metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_GENRE, getGenreName());
        metadataEditor.apply();
    }

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
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
                mGenreId = -1;
                mGenreName = null;
            }
            notifyChange(META_CHANGED);
        }
        notifyChange(QUEUE_CHANGED);
    }

    @Override
    public void enqueue(long[] list, int action) {
        synchronized (this) {
            if ((action == NEXT || action == NOW) && mPlayPos + 1 < mPlayListLen) {
                addToPlayList(list, mPlayPos + 1);
                if (action == NOW) {
                    mPlayPos++;
                    openCurrent();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                }
            } else {
                addToPlayList(list, Integer.MAX_VALUE);
                if (action == NOW) {
                    mPlayPos = mPlayListLen - list.length;
                    openCurrent();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrent();
                play();
                notifyChange(META_CHANGED);
            }
        }
    }

    @Override
    public void interleave(long[] newList, int currentCount, int newCount) {
        synchronized (this) {
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
    }

    @Override
    public void open(long[] list, int position) {
        synchronized (this) {
            long oldId = getAudioId();
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

            saveBookmarkIfNeeded();
            openCurrent();
            if (oldId != getAudioId()) {
                notifyChange(META_CHANGED);
            }
        }
    }

    @Override
    public void moveQueueItem(int index1, int index2) {
        synchronized (this) {
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
    }

    @Override
    public long[] getQueue() {
        synchronized (this) {
            int len = mPlayListLen;
            long[] list = new long[len];
            System.arraycopy(mPlayList, 0, list, 0, len);
            return list;
        }
    }

    @Override
    public int getQueueLength() {
        synchronized (this) {
            return mPlayListLen;
        }
    }

    private void openCurrent() {
        synchronized (this) {
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
                mGenreId = -1;
                mGenreName = null;
            }

            if (mPlayListLen == 0) {
                return;
            }
            stop(false);

            String id = String.valueOf(mPlayList[mPlayPos]);

            mCursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    CURSOR_COLS, "_id=" + id, null, null);
            if (mCursor != null) {
                if (mCursor.moveToFirst()) {
                    IdAndName idAndName = MusicUtils.fetchGenre(this, mCursor.getLong(IDCOLIDX));
                    if (idAndName != null) {
                        mGenreId = idAndName.id;
                        mGenreName = idAndName.name;
                    }
                }
                open(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + id);
                // go to bookmark if needed
                if (isPodcast()) {
                    long bookmark = getBookmark();
                    // Start playing a little bit before the bookmark,
                    // so it's easier to get back in to the narrative.
                    seek(bookmark - 5000);
                }
            }
        }
    }

    /**
     * Opens the specified file and readies it for playback.
     *
     * @param path The full path of the file to be opened.
     */
    private void open(String path) {
        synchronized (this) {
            if (path == null) {
                return;
            }

            // if mCursor is null, try to associate path with a database cursor
            if (mCursor == null) {

                ContentResolver resolver = getContentResolver();
                Uri uri;
                String where;
                String selectionArgs[];
                if (path.startsWith("content://media/")) {
                    uri = Uri.parse(path);
                    where = null;
                    selectionArgs = null;
                } else {
                    uri = MediaStore.Audio.Media.getContentUriForPath(path);
                    where = MediaStore.Audio.AudioColumns.DATA + "=?";
                    selectionArgs = new String[]{path};
                }

                try {
                    mCursor = resolver.query(uri, CURSOR_COLS, where, selectionArgs, null);
                    if (mCursor != null) {
                        if (mCursor.getCount() == 0) {
                            mCursor.close();
                            mCursor = null;
                            mGenreId = -1;
                            mGenreName = null;
                        } else {
                            if (mCursor.moveToNext()) {
                                IdAndName idAndName = MusicUtils.fetchGenre(this, mCursor.getLong(IDCOLIDX));
                                if (idAndName != null) {
                                    mGenreId = idAndName.id;
                                    mGenreName = idAndName.name;
                                }
                            }
                            ensurePlayListCapacity(1);
                            mPlayListLen = 1;
                            mPlayList[0] = mCursor.getLong(IDCOLIDX);
                            mPlayPos = 0;
                        }
                    }
                } catch (UnsupportedOperationException ex) {
                    Log.w(LOGTAG, "Error", ex);
                }
            }
            mPlayer.setDataSource(path);
            if (!mPlayer.isInitialized()) {
                stop(true);
                if (mOpenFailedCounter++ < 10 && mPlayListLen > 1) {
                    // beware: this ends up being recursive because next() calls open() again.
                    next(false);
                }
                if (!mPlayer.isInitialized() && mOpenFailedCounter != 0) {
                    // need to make sure we only shows this once
                    mOpenFailedCounter = 0;
                    if (!mQuietMode) {
                        Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
                    }
                    Log.d(LOGTAG, "Failed to open file for playback");
                }
            } else {
                mOpenFailedCounter = 0;
            }
        }
    }

    @Override
    public void play() {
        int result = mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(LOGTAG, "Unable to gain audio focus: " + result);
            return;
        }

        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(this.getPackageName(),
                MediaButtonIntentReceiver.class.getName()));
        mAudioManager.registerRemoteControlClient(mRemoteControlClient);

        if (mPlayer.isInitialized()) {
            // if we are at the end of the song, go to the next song first
            long duration = mPlayer.duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > 2000 &&
                    mPlayer.currentPosition() >= duration - 2000) {
                next(true);
            }

            mPlayer.start();
            // make sure we fade in, in case a previous fadein was stopped because
            // of another focus loss
            mMediaplayerHandler.removeMessages(DUCK);
            mMediaplayerHandler.removeMessages(FADEDOWN);
            mMediaplayerHandler.sendEmptyMessage(FADEUP);

            startForeground(PLAYBACKSERVICE_STATUS, buildNotification());

            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }
        }
    }

    private Notification buildNotification() {
        String trackname;
        String artistname;
        if (getAudioId() < 0) { // streaming
            trackname = getString(R.string.streaming);
            artistname = null;
        } else {
            trackname = getTrackName();
            artistname = getArtistName();
            if (artistname == null || artistname.equals(MediaStore.UNKNOWN_STRING)) {
                artistname = getString(R.string.unknown_artist_name);
            }
        }

        Class<?> activityClass = getResources().getBoolean(R.bool.tablet_layout)
                ? MusicBrowserActivity.class
                : MediaPlaybackActivity.class;

        RemoteViews views = new RemoteViews(getPackageName(), R.layout.statusbar);
        views.setTextViewText(R.id.trackname, trackname);
        views.setTextViewText(R.id.artistname, artistname);
        views.setOnClickPendingIntent(R.id.pause, PendingIntent.getService(this, 0,
                new Intent(PAUSE_ACTION).setClass(this, MediaPlaybackService.class),
                0));
        Notification status = new Notification();
        status.contentView = views;
        status.flags |= Notification.FLAG_ONGOING_EVENT;
        status.icon = R.drawable.stat_notify_musicplayer;
        status.contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                0);

        return status;
    }

    private void stop(boolean remove_status_icon) {
        if (mPlayer.isInitialized()) {
            mMediaplayerHandler.removeMessages(FADEDOWN);
            mPlayer.stop();
        }
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            mGenreId = -1;
            mGenreName = null;
        }
        if (remove_status_icon) {
            gotoIdleState();
            mIsSupposedToBePlaying = false;
        } else {
            stopForeground(false);
        }
    }

    @Override
    public void pause() {
        synchronized (this) {
            mMediaplayerHandler.removeMessages(FADEUP);
            mMediaplayerHandler.removeMessages(FADEDOWN);
            if (isPlaying()) {
                mPlayer.pause();
                gotoIdleState();
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
                saveBookmarkIfNeeded();
            }
        }
    }

    @Override
    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

    @Override
    public void prev() {
        synchronized (this) {
            if (mPlayPos > 0) {
                mPlayPos--;
            } else {
                mPlayPos = mPlayListLen - 1;
            }
            saveBookmarkIfNeeded();
            stop(false);
            openCurrent();
            play();
            notifyChange(META_CHANGED);
        }
    }

    @Override
    public void next(boolean force) {
        synchronized (this) {
            if (mPlayListLen <= 0) {
                //Log.d(LOGTAG, "No play queue");
                return;
            }

            if (mPlayPos >= mPlayListLen - 1) {
                // we're at the end of the list
                if (mRepeatMode == REPEAT_NONE && !force) {
                    // all done
                    gotoIdleState();
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                    return;
                } else if (mRepeatMode == REPEAT_ALL || force) {
                    mPlayPos = 0;
                }
            } else {
                mPlayPos++;
            }
            saveBookmarkIfNeeded();
            stop(false);
            openCurrent();
            play();
            notifyChange(META_CHANGED);
        }
    }

    private void gotoIdleState() {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        stopForeground(true);
    }

    private void saveBookmarkIfNeeded() {
        try {
            if (isPodcast()) {
                long pos = position();
                long bookmark = getBookmark();
                long duration = duration();
                if ((pos < bookmark && (pos + 10000) > bookmark) ||
                        (pos > bookmark && (pos - 10000) < bookmark)) {
                    // The existing bookmark is close to the current
                    // position, so don't update it.
                    return;
                }
                if (pos < 15000 || (pos + 10000) > duration) {
                    // if we're near the start or end, clear the bookmark
                    pos = 0;
                }

                // write 'pos' to the bookmark field
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.AudioColumns.BOOKMARK, pos);
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCursor.getLong(IDCOLIDX));
                getContentResolver().update(uri, values, null, null);
            }
        } catch (SQLiteException ex) {
            Log.w(LOGTAG, "Error", ex);
        }
    }

    @Override
    public int removeTracks(int first, int last) {
        int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    private int removeTracksInternal(int first, int last) {
        synchronized (this) {
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
                    stop(true);
                    mPlayPos = -1;
                    if (mCursor != null) {
                        mCursor.close();
                        mCursor = null;
                        mGenreId = -1;
                        mGenreName = null;
                    }
                } else {
                    if (mPlayPos >= mPlayListLen) {
                        mPlayPos = 0;
                    }
                    boolean wasPlaying = isPlaying();
                    stop(false);
                    openCurrent();
                    if (wasPlaying) {
                        play();
                    }
                }
                notifyChange(META_CHANGED);
            }
            return last - first + 1;
        }
    }

    @Override
    public int removeTrack(long id) {
        int numremoved = 0;
        synchronized (this) {
            for (int i = 0; i < mPlayListLen; i++) {
                if (mPlayList[i] == id) {
                    numremoved += removeTracksInternal(i, i);
                    i--;
                }
            }
        }
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    @Override
    public void doShuffle() {
        synchronized (this) {
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
    }

    @Override
    public void uniqueify() {
        synchronized (this) {
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
    }

    @Override
    public void setRepeatMode(int repeatmode) {
        synchronized (this) {
            mRepeatMode = repeatmode;
            saveQueue(false);
        }
    }

    @Override
    public int getRepeatMode() {
        return mRepeatMode;
    }

    @Override
    public long getAudioId() {
        synchronized (this) {
            if (mPlayPos >= 0 && mPlayer.isInitialized()) {
                return mPlayList[mPlayPos];
            }
        }
        return -1;
    }

    @Override
    public int getQueuePosition() {
        synchronized (this) {
            return mPlayPos;
        }
    }

    @Override
    public void setQueuePosition(int pos) {
        synchronized (this) {
            stop(false);
            mPlayPos = pos;
            openCurrent();
            play();
            notifyChange(META_CHANGED);
        }
    }

    @Override
    public String getArtistName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST));
        }
    }

    @Override
    public long getArtistId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST_ID));
        }
    }

    @Override
    public String getAlbumName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM));
        }
    }

    @Override
    public long getAlbumId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID));
        }
    }

    @Override
    public String getGenreName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mGenreName;
        }
    }

    @Override
    public long getGenreId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mGenreId;
        }
    }

    @Override
    public String getMimeType() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE));
        }
    }

    @Override
    public File getFolder() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return new File(mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA))).getParentFile();
        }
    }

    @Override
    public String getTrackName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE));
        }
    }

    private boolean isPodcast() {
        synchronized (this) {
            if (mCursor == null) {
                return false;
            }
            return (mCursor.getInt(PODCASTCOLIDX) > 0);
        }
    }

    private long getBookmark() {
        synchronized (this) {
            if (mCursor == null) {
                return 0;
            }
            return mCursor.getLong(BOOKMARKCOLIDX);
        }
    }

    @Override
    public long duration() {
        if (mPlayer.isInitialized()) {
            return mPlayer.duration();
        }
        return -1;
    }

    @Override
    public long position() {
        if (mPlayer.isInitialized()) {
            return mPlayer.currentPosition();
        }
        return -1;
    }

    @Override
    public void seek(long pos) {
        if (mPlayer.isInitialized()) {
            if (pos < 0) pos = 0;
            if (pos > mPlayer.duration()) pos = mPlayer.duration();
            mPlayer.seek(pos);
        }
    }

    @Override
    public int getAudioSessionId() {
        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

}
