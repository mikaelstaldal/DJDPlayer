/*
 * Copyright (C) 2007 The Android Open Source Project
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
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.*;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;
import java.util.Vector;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class MediaPlaybackService extends Service {
    /** used to specify whether enqueue() should start playing
     * the new list of files right away, next or once all the currently
     * queued files have been played
     */
    public static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;
    public static final int PLAYBACKSERVICE_STATUS = 1;
    
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;

    public static final String PLAYSTATE_CHANGED = "nu.staldal.djdplayer.playstatechanged";
    public static final String META_CHANGED = "nu.staldal.djdplayer.metachanged";
    public static final String QUEUE_CHANGED = "nu.staldal.djdplayer.queuechanged";

    public static final String SERVICECMD = "nu.staldal.djdplayer.musicservicecommand";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";

    public static final String TOGGLEPAUSE_ACTION = "nu.staldal.djdplayer.musicservicecommand.togglepause";
    public static final String PAUSE_ACTION = "nu.staldal.djdplayer.musicservicecommand.pause";
    public static final String PREVIOUS_ACTION = "nu.staldal.djdplayer.musicservicecommand.previous";
    public static final String NEXT_ACTION = "nu.staldal.djdplayer.musicservicecommand.next";

    private static final int TRACK_ENDED = 1;
    private static final int RELEASE_WAKELOCK = 2;
    private static final int SERVER_DIED = 3;
    private static final int FOCUSCHANGE = 4;
    private static final int FADEDOWN = 5;
    private static final int FADEUP = 6;
    private static final int MAX_HISTORY_SIZE = 100;

    private final IBinder mBinder = new MediaPlaybackServiceBinder();

    public class MediaPlaybackServiceBinder extends Binder {
      public MediaPlaybackService getService() {
          return MediaPlaybackService.this;
      }
    }

    private MultiPlayer mPlayer;
    private String mFileToPlay;
    private int mRepeatMode = REPEAT_NONE;
    private int mMediaMountedCount = 0;
    private long [] mPlayList = null;
    private int mPlayListLen = 0;
    private Vector<Integer> mHistory = new Vector<Integer>(MAX_HISTORY_SIZE);
    private Cursor mCursor;
    private long mGenreId = -1;
    private String mGenreName = null;
    private long mNextTrackId = -1;
    private String mNextTrackName = null;
    private long mNextArtistId = -1;
    private String mNextArtistName = null;
    private long mNextGenreId = -1;
    private String mNextGenreName = null;
    private long mNextAlbumId = -1;
    private String mNextAlbumName = null;
    private int mPlayPos = -1;
    private static final String LOGTAG = "MediaPlaybackService";
    private int mOpenFailedCounter = 0;
    String[] mCursorCols = new String[] {
            "audio._id AS _id",             // index must match IDCOLIDX below
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.IS_PODCAST, // index must match PODCASTCOLIDX below
            MediaStore.Audio.Media.BOOKMARK    // index must match BOOKMARKCOLIDX below
    };
    private final static int IDCOLIDX = 0;
    private final static int PODCASTCOLIDX = 8;
    private final static int BOOKMARKCOLIDX = 9;
    private BroadcastReceiver mUnmountReceiver = null;
    private WakeLock mWakeLock;
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQuietMode = false;
    private AudioManager mAudioManager;
    private boolean mQueueIsSaveable = true;
    // used to track what type of audio focus loss caused the playback to pause
    private boolean mPausedByTransientLossOfFocus = false;

    private SharedPreferences mPreferences;
    // We use this to distinguish between different cards when saving/restoring playlists.
    // This will have to change if we want to support multiple simultaneous cards.
    private int mCardId;
    
    private MediaAppWidgetProvider mAppWidgetProvider = MediaAppWidgetProvider.getInstance();
    
    // interval after which we stop the service when idle
    private static final int IDLE_DELAY = 60000;
    
    private Handler mMediaplayerHandler = new Handler() {
        float mCurrentVolume = 1.0f;
        @Override
        public void handleMessage(Message msg) {
            MusicUtils.debugLog("mMediaplayerHandler.handleMessage " + msg.what);
            switch (msg.what) {
                case FADEDOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    mPlayer.setVolume(mCurrentVolume);
                    break;
                case FADEUP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEUP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                    }
                    mPlayer.setVolume(mCurrentVolume);
                    break;
                case SERVER_DIED:
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
                case TRACK_ENDED:
                    if (mRepeatMode == REPEAT_CURRENT) {
                        seek(0);
                        play();
                    } else {
                        next(false);
                    }
                    break;
                case RELEASE_WAKELOCK:
                    mWakeLock.release();
                    break;

                case FOCUSCHANGE:
                    // This code is here so we can better synchronize it with the code that
                    // handles fade-in
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS");
                            if(isPlaying()) {
                                mPausedByTransientLossOfFocus = false;
                            }
                            pause();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            mMediaplayerHandler.removeMessages(FADEUP);
                            mMediaplayerHandler.sendEmptyMessage(FADEDOWN);
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");
                            if(isPlaying()) {
                                mPausedByTransientLossOfFocus = true;
                            }
                            pause();
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                            if(!isPlaying() && mPausedByTransientLossOfFocus) {
                                mPausedByTransientLossOfFocus = false;
                                mCurrentVolume = 0f;
                                mPlayer.setVolume(mCurrentVolume);
                                play(); // also queues a fade-in
                            } else {
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

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            MusicUtils.debugLog("mIntentReceiver.onReceive " + action + " / " + cmd);
            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                next(true);
            } else if (CMDPREVIOUS.equals(cmd) || PREVIOUS_ACTION.equals(action)) {
                prev();
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else if (CMDSTOP.equals(cmd)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                seek(0);
            } else if (MediaAppWidgetProvider.CMDAPPWIDGETUPDATE.equals(cmd)) {
                // Someone asked us to refresh a set of specific widgets, probably
                // because they were just added.
                int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetProvider.performUpdate(MediaPlaybackService.this, appWidgetIds);
            }
        }
    };

    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            mMediaplayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };

    public MediaPlaybackService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName()));
        
        mPreferences = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        mCardId = MusicUtils.getCardId(this);
        
        registerExternalStorageListener();

        // Needs to be done in this thread, since otherwise ApplicationContext.getPowerManager() crashes.
        mPlayer = new MultiPlayer();
        mPlayer.setHandler(mMediaplayerHandler);

        reloadQueue();
        
        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(SERVICECMD);
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        commandFilter.addAction(PREVIOUS_ACTION);
        registerReceiver(mIntentReceiver, commandFilter);
        
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);

        // If the service was idle, but got killed before it stopped itself, the
        // system will relaunch it. Make sure it gets stopped again in that case.
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
    }

    @Override
    public void onDestroy() {
        // Check that we're not being destroyed while something is still playing.
        if (isPlaying()) {
            Log.e(LOGTAG, "Service being destroyed while still playing.");
        }
        // release all MediaPlayer resources, including the native player and wakelocks
        Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(i);
        mPlayer.release();
        mPlayer = null;

        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        
        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mMediaplayerHandler.removeCallbacksAndMessages(null);

        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            mGenreId = -1;
            mGenreName = null;
            clearNextSong();
        }

        unregisterReceiver(mIntentReceiver);
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }
        mWakeLock.release();
        super.onDestroy();
    }

    private void clearNextSong() {
        mNextTrackId = -1;
        mNextTrackName = null;
        mNextArtistId = -1;
        mNextArtistName = null;
        mNextGenreId = -1;
        mNextGenreName = null;
        mNextAlbumId = -1;
        mNextAlbumName = null;
    }

    private final char hexdigits [] = new char [] {
            '0', '1', '2', '3',
            '4', '5', '6', '7',
            '8', '9', 'a', 'b',
            'c', 'd', 'e', 'f'
    };

    private void saveQueue(boolean full) {
        if (!mQueueIsSaveable) {
            return;
        }

        Editor ed = mPreferences.edit();
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
                if (n < 0) {
                    continue;
                } else if (n == 0) {
                    q.append("0;");
                } else {
                    while (n != 0) {
                        int digit = (int)(n & 0xf);
                        n >>>= 4;
                        q.append(hexdigits[digit]);
                    }
                    q.append(";");
                }
            }
            //Log.i("@@@@ service", "created queue string in " + (System.currentTimeMillis() - start) + " ms");
            ed.putString("queue", q.toString());
            ed.putInt("cardid", mCardId);
        }
        ed.putInt("curpos", mPlayPos);
        if (mPlayer.isInitialized()) {
            ed.putLong("seekpos", mPlayer.position());
        }
        ed.putInt("repeatmode", mRepeatMode);
        SharedPreferencesCompat.apply(ed);

        //Log.i("@@@@ service", "saved state in " + (System.currentTimeMillis() - start) + " ms");
    }

    private void reloadQueue() {
        String q = null;
        
        boolean newstyle = false;
        int id = mCardId;
        if (mPreferences.contains("cardid")) {
            newstyle = true;
            id = mPreferences.getInt("cardid", ~mCardId);
        }
        if (id == mCardId) {
            // Only restore the saved playlist if the card is still
            // the same one as when the playlist was saved
            q = mPreferences.getString("queue", "");
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

            int pos = mPreferences.getInt("curpos", 0);
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
                        new String [] {"_id"}, "_id=" + mPlayList[mPlayPos] , null, null);
            if (crsr == null || crsr.getCount() == 0) {
                // wait a bit and try again
                SystemClock.sleep(3000);
                crsr = getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mCursorCols, "_id=" + mPlayList[mPlayPos] , null, null);
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
            
            long seekpos = mPreferences.getLong("seekpos", 0);
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);
            Log.d(LOGTAG, "restored queue, currently at position "
                    + position() + "/" + duration()
                    + " (requested " + seekpos + ")");
            
            int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mServiceStartId = startId;
        mDelayedStopHandler.removeCallbacksAndMessages(null);

        if (intent != null) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            MusicUtils.debugLog("onStartCommand " + action + " / " + cmd);

            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                next(true);
            } else if (CMDPREVIOUS.equals(cmd) || PREVIOUS_ACTION.equals(action)) {
                if (position() < 2000) {
                    prev();
                } else {
                    seek(0);
                    play();
                }
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else if (CMDSTOP.equals(cmd)) {
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
        if (mPlayListLen > 0  || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
            Message msg = mDelayedStopHandler.obtainMessage();
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
            return true;
        }
        
        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId);
        return true;
    }
    
    private Handler mDelayedStopHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Check again to make sure nothing is playing right now
            if (isPlaying() || mPausedByTransientLossOfFocus || mServiceInUse
                    || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
                return;
            }
            // save the queue again, because it might have changed
            // since the user exited the music app (because of
            // the play-position changed)
            saveQueue(true);
            stopSelf(mServiceStartId);
        }
    };

    /**
     * Called when we receive a ACTION_MEDIA_EJECT notification.
     *
     * @param storagePath path to mount point for the removed media
     */
    public void closeExternalStorageFiles(String storagePath) {
        // stop playback and clean up if the SD card is going to be unmounted.
        stop(true);
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
    }

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        saveQueue(true);
                        mQueueIsSaveable = false;
                        closeExternalStorageFiles(intent.getData().getPath());
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        mMediaMountedCount++;
                        mCardId = MusicUtils.getCardId(MediaPlaybackService.this);
                        reloadQueue();
                        mQueueIsSaveable = true;
                        notifyChange(QUEUE_CHANGED);
                        notifyChange(META_CHANGED);
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
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
        i.putExtra("album",getAlbumName());
        i.putExtra("track", getTrackName());
        i.putExtra("playing", isPlaying());
        sendStickyBroadcast(i);
        
        if (what.equals(QUEUE_CHANGED)) {
            saveQueue(true);
        } else {
            saveQueue(false);
        }
        
        // Share this notification directly with our widgets
        mAppWidgetProvider.notifyChange(this, what);
    }

    private void ensurePlayListCapacity(int size) {
        if (mPlayList == null || size > mPlayList.length) {
            // reallocate at 2x requested size so we don't
            // need to grow and copy the array for every
            // insert
            long [] newlist = new long[size * 2];
            int len = mPlayList != null ? mPlayList.length : mPlayListLen;
            for (int i = 0; i < len; i++) {
                newlist[i] = mPlayList[i];
            }
            mPlayList = newlist;
        }
        // FIXME: shrink the array when the needed size is much smaller
        // than the allocated size
    }
    
    // insert the list of songs at the specified position in the playlist
    private void addToPlayList(long [] list, int position) {
        int addlen = list.length;
        if (position < 0) { // overwrite
            mPlayListLen = 0;
            position = 0;
        }
        ensurePlayListCapacity(mPlayListLen + addlen);
        if (position > mPlayListLen) {
            position = mPlayListLen;
        }
        
        // move part of list after insertion point
        int tailsize = mPlayListLen - position;
        for (int i = tailsize ; i > 0 ; i--) {
            mPlayList[position + i] = mPlayList[position + i - addlen]; 
        }
        
        // copy list into playlist
        for (int i = 0; i < addlen; i++) {
            mPlayList[position + i] = list[i];
        }
        mPlayListLen += addlen;
        if (mPlayListLen == 0) {
            mCursor.close();
            mCursor = null;
            mGenreId = -1;
            mGenreName = null;
            clearNextSong();
            notifyChange(META_CHANGED);
        } else {
            fetchNextSong();
        }
        notifyChange(QUEUE_CHANGED);
    }
    
    /**
     * Appends a list of tracks to the current playlist.
     * If nothing is playing currently, playback will be started at
     * the first track.
     * If the action is NOW, playback will switch to the first of
     * the new tracks immediately.
     * @param list The list of tracks to append.
     * @param action NOW, NEXT or LAST
     */
    public void enqueue(long [] list, int action) {
        synchronized(this) {
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

    /**
     * Replaces the current playlist with a new list,
     * and prepares for starting playback at the specified
     * position in the list
     * @param list The new list of tracks.
     */
    public void open(long [] list, int position) {
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
            int oldpos = mPlayPos;
            if (position >= 0) {
                mPlayPos = position;
            } else {
                mPlayPos = 0;
            }
            mHistory.clear();

            saveBookmarkIfNeeded();
            openCurrent();
            if (oldId != getAudioId()) {
                notifyChange(META_CHANGED);
            }
        }
    }
    
    /**
     * Moves the item at index1 to index2.
     * @param index1
     * @param index2
     */
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
                for (int i = index1; i < index2; i++) {
                    mPlayList[i] = mPlayList[i+1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                        mPlayPos--;
                }
            } else if (index2 < index1) {
                long tmp = mPlayList[index1];
                for (int i = index1; i > index2; i--) {
                    mPlayList[i] = mPlayList[i-1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                        mPlayPos++;
                }
            }
            fetchNextSong();
            notifyChange(QUEUE_CHANGED);
        }
    }

    /**
     * Returns the current play list
     * @return An array of integers containing the IDs of the tracks in the play list
     */
    public long [] getQueue() {
        synchronized (this) {
            int len = mPlayListLen;
            long [] list = new long[len];
            for (int i = 0; i < len; i++) {
                list[i] = mPlayList[i];
            }
            return list;
        }
    }

    private void openCurrent() {
        synchronized (this) {
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
                mGenreId = -1;
                mGenreName = null;
                clearNextSong();
            }

            if (mPlayListLen == 0) {
                return;
            }
            stop(false);

            String id = String.valueOf(mPlayList[mPlayPos]);
            
            mCursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mCursorCols, "_id=" + id , null, null);
            if (mCursor != null) {
                if (mCursor.moveToFirst()) {
                    IdAndName idAndName = MusicUtils.fetchGenre(this, mCursor.getLong(IDCOLIDX));
                    if (idAndName != null) {
                        mGenreId = idAndName.id;
                        mGenreName = idAndName.name;
                    }
                }
                fetchNextSong();
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

    private void fetchNextSong() {
        if (mPlayPos < mPlayListLen - 1) { // not at end of playlist
            long nextSongId = mPlayList[mPlayPos+1];
            Cursor c = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mCursorCols, "_id=" + String.valueOf(nextSongId) , null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    mNextTrackId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                    mNextTrackName = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                    mNextArtistId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID));
                    mNextArtistName = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                    mNextAlbumId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
                    mNextAlbumName = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
                }
                c.close();
            }

            IdAndName idAndName = MusicUtils.fetchGenre(this, nextSongId);
            if (idAndName != null) {
                mNextGenreId = idAndName.id;
                mNextGenreName = idAndName.name;
            }
        }
    }

    /**
     * Opens the specified file and readies it for playback.
     *
     * @param path The full path of the file to be opened.
     */
    public void open(String path) {
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
                   where = MediaStore.Audio.Media.DATA + "=?";
                   selectionArgs = new String[] { path };
                }
                
                try {
                    mCursor = resolver.query(uri, mCursorCols, where, selectionArgs, null);
                    if  (mCursor != null) {
                        if (mCursor.getCount() == 0) {
                            mCursor.close();
                            mCursor = null;
                            mGenreId = -1;
                            mGenreName = null;
                            clearNextSong();
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
                }
            }
            mFileToPlay = path;
            mPlayer.setDataSource(mFileToPlay);
            if (! mPlayer.isInitialized()) {
                stop(true);
                if (mOpenFailedCounter++ < 10 &&  mPlayListLen > 1) {
                    // beware: this ends up being recursive because next() calls open() again.
                    next(false);
                }
                if (! mPlayer.isInitialized() && mOpenFailedCounter != 0) {
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

    /**
     * Starts playback of a previously opened file.
     */
    public void play() {
        mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(this.getPackageName(),
                MediaButtonIntentReceiver.class.getName()));

        if (mPlayer.isInitialized()) {
            // if we are at the end of the song, go to the next song first
            long duration = mPlayer.duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > 2000 &&
                mPlayer.position() >= duration - 2000) {
                next(true);
            }

            mPlayer.start();
            // make sure we fade in, in case a previous fadein was stopped because
            // of another focus loss
            mMediaplayerHandler.removeMessages(FADEDOWN);
            mMediaplayerHandler.sendEmptyMessage(FADEUP);

            RemoteViews views = new RemoteViews(getPackageName(), R.layout.statusbar);
            views.setImageViewResource(R.id.icon, R.drawable.stat_notify_musicplayer);
            if (getAudioId() < 0) {
                // streaming
                views.setTextViewText(R.id.trackname, getPath());
                views.setTextViewText(R.id.artistgenre, null);
            } else {
                String artist = getArtistName();
                views.setTextViewText(R.id.trackname, getTrackName());
                if (artist == null || artist.equals(MediaStore.UNKNOWN_STRING)) {
                    artist = getString(R.string.unknown_artist_name);
                }
                String genre = getGenreName();
                if (genre == null || genre.equals(MediaStore.UNKNOWN_STRING)) {
                    genre = getString(R.string.unknown_genre_name);
                }
                
                views.setTextViewText(R.id.artistgenre,
                        getString(R.string.notification_artist_genre, artist, genre)
                        );
            }
            
            Notification status = new Notification();
            status.contentView = views;
            status.flags |= Notification.FLAG_ONGOING_EVENT;
            status.icon = R.drawable.stat_notify_musicplayer;
            status.contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent("nu.staldal.djdplayer.PLAYBACK_VIEWER")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);
            startForeground(PLAYBACKSERVICE_STATUS, status);
            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }

        }
    }
    
    private void stop(boolean remove_status_icon) {
        if (mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        mFileToPlay = null;
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            mGenreId = -1;
            mGenreName = null;
            clearNextSong();
        }
        if (remove_status_icon) {
            gotoIdleState();
        } else {
            stopForeground(false);
        }
        if (remove_status_icon) {
            mIsSupposedToBePlaying = false;
        }
    }

    /**
     * Stops playback.
     */
    public void stop() {
        stop(true);
    }

    /**
     * Pauses playback (call play() to resume)
     */
    public void pause() {
        synchronized(this) {
            mMediaplayerHandler.removeMessages(FADEUP);
            if (isPlaying()) {
                mPlayer.pause();
                gotoIdleState();
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
                saveBookmarkIfNeeded();
            }
        }
    }

    /** Returns whether something is currently playing
     *
     * @return true if something is playing (or will be playing shortly, in case
     * we're currently transitioning between tracks), false if not.
     */
    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

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

    public void next(boolean force) {
        synchronized (this) {
            if (mPlayListLen <= 0) {
                Log.d(LOGTAG, "No play queue");
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
                values.put(MediaStore.Audio.Media.BOOKMARK, pos);
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCursor.getLong(IDCOLIDX));
                getContentResolver().update(uri, values, null, null);
            }
        } catch (SQLiteException ex) {
        }
    }

    // check that the specified idx is not in the history (but only look at at
    // most lookbacksize entries in the history)
    private boolean wasRecentlyUsed(int idx, int lookbacksize) {

        // early exit to prevent infinite loops in case idx == mPlayPos
        if (lookbacksize == 0) {
            return false;
        }

        int histsize = mHistory.size();
        if (histsize < lookbacksize) {
            Log.d(LOGTAG, "lookback too big");
            lookbacksize = histsize;
        }
        int maxidx = histsize - 1;
        for (int i = 0; i < lookbacksize; i++) {
            long entry = mHistory.get(maxidx - i);
            if (entry == idx) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the range of tracks specified from the play list. If a file within the range is
     * the file currently being played, playback will move to the next file after the
     * range. 
     * @param first The first file to be removed
     * @param last The last file to be removed
     * @return the number of tracks deleted
     */
    public int removeTracks(int first, int last) {
        int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            fetchNextSong();
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
                        clearNextSong();
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
    
    /**
     * Removes all instances of the track with the given id
     * from the playlist.
     * @param id The id to be removed
     * @return how many instances of the track were removed
     */
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
            fetchNextSong();
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }
    
    public void doShuffle() {
        synchronized(this) {
            Random random = new Random();
            for (int i=0; i < mPlayListLen; i++) {
                if (i != mPlayPos) {
                    int randomPosition = random.nextInt(mPlayListLen);
                    while (randomPosition == mPlayPos) randomPosition = random.nextInt(mPlayListLen);
                    long temp = mPlayList[i];
                    mPlayList[i] = mPlayList[randomPosition];
                    mPlayList[randomPosition] = temp;
                }
            }
            fetchNextSong();
            notifyChange(QUEUE_CHANGED);
        }
    }
    public void setRepeatMode(int repeatmode) {
        synchronized(this) {
            mRepeatMode = repeatmode;
            saveQueue(false);
        }
    }
    public int getRepeatMode() {
        return mRepeatMode;
    }

    public int getMediaMountedCount() {
        return mMediaMountedCount;
    }

    /**
     * Returns the path of the currently playing file, or null if
     * no file is currently playing.
     */
    public String getPath() {
        return mFileToPlay;
    }
    
    /**
     * Returns the rowid of the currently playing file, or -1 if
     * no file is currently playing.
     */
    public long getAudioId() {
        synchronized (this) {
            if (mPlayPos >= 0 && mPlayer.isInitialized()) {
                return mPlayList[mPlayPos];
            }
        }
        return -1;
    }
    
    /**
     * Returns the position in the queue 
     * @return the position in the queue
     */
    public int getQueuePosition() {
        synchronized(this) {
            return mPlayPos;
        }
    }
    
    /**
     * Starts playing the track at the given position in the queue.
     * @param pos The position in the queue of the track that will be played.
     */
    public void setQueuePosition(int pos) {
        synchronized(this) {
            stop(false);
            mPlayPos = pos;
            openCurrent();
            play();
            notifyChange(META_CHANGED);
        }
    }

    public String getArtistName() {
        synchronized(this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
        }
    }
    
    public long getArtistId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID));
        }
    }

    public String getAlbumName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
        }
    }

    public long getAlbumId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
        }
    }

    public String getGenreName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mGenreName;
        }
    }

    public long getNextArtistId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mNextArtistId;
        }
    }

    public String getNextArtistName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mNextArtistName;
        }
    }

    public long getNextGenreId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mNextGenreId;
        }
    }

    public String getNextGenreName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mNextGenreName;
        }
    }

    public long getNextAlbumId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mNextAlbumId;
        }
    }

    public String getNextAlbumName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mNextAlbumName;
        }
    }

    public long getNextTrackId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mNextTrackId;
        }
    }

    public String getNextTrackName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mNextTrackName;
        }
    }

    public long getGenreId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mGenreId;
        }
    }

    public File getFolder() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return new File(mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))).getParentFile();
        }
    }

    public String getTrackName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
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
    
    /**
     * Returns the duration of the file in milliseconds.
     * Currently this method returns -1 for the duration of MIDI files.
     */
    public long duration() {
        if (mPlayer.isInitialized()) {
            return mPlayer.duration();
        }
        return -1;
    }

    /**
     * Returns the current playback position in milliseconds
     */
    public long position() {
        if (mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }

    /**
     * Seeks to the position specified.
     *
     * @param pos The position to seek to, in milliseconds
     */
    public long seek(long pos) {
        if (mPlayer.isInitialized()) {
            if (pos < 0) pos = 0;
            if (pos > mPlayer.duration()) pos = mPlayer.duration();
            return mPlayer.seek(pos);
        }
        return -1;
    }

    /**
     * Sets the audio session ID.
     *
     * @param sessionId: the audio session ID.
     */
    public void setAudioSessionId(int sessionId) {
        synchronized (this) {
            mPlayer.setAudioSessionId(sessionId);
        }
    }

    /**
     * Returns the audio session ID.
     */
    public int getAudioSessionId() {
        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

    /**
     * Provides a unified interface for dealing with midi files and
     * other media files.
     */
    private class MultiPlayer {
        private MediaPlayer mMediaPlayer = new MediaPlayer();
        private Handler mHandler;
        private boolean mIsInitialized = false;

        public MultiPlayer() {
            mMediaPlayer.setWakeMode(MediaPlaybackService.this, PowerManager.PARTIAL_WAKE_LOCK);
        }

        public void setDataSource(String path) {
            try {
                mMediaPlayer.reset();
                mMediaPlayer.setOnPreparedListener(null);
                if (path.startsWith("content://")) {
                    mMediaPlayer.setDataSource(MediaPlaybackService.this, Uri.parse(path));
                } else {
                    mMediaPlayer.setDataSource(path);
                }
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.prepare();
            } catch (IOException ex) {
                // TODO: notify the user why the file couldn't be opened
                mIsInitialized = false;
                return;
            } catch (IllegalArgumentException ex) {
                // TODO: notify the user why the file couldn't be opened
                mIsInitialized = false;
                return;
            }
            mMediaPlayer.setOnCompletionListener(listener);
            mMediaPlayer.setOnErrorListener(errorListener);
            Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
            sendBroadcast(i);
            mIsInitialized = true;
        }
        
        public boolean isInitialized() {
            return mIsInitialized;
        }

        public void start() {
            MusicUtils.debugLog(new Exception("MultiPlayer.start called"));
            mMediaPlayer.start();
        }

        public void stop() {
            mMediaPlayer.reset();
            mIsInitialized = false;
        }

        /**
         * You CANNOT use this player anymore after calling release()
         */
        public void release() {
            stop();
            mMediaPlayer.release();
        }
        
        public void pause() {
            mMediaPlayer.pause();
        }
        
        public void setHandler(Handler handler) {
            mHandler = handler;
        }

        MediaPlayer.OnCompletionListener listener = new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                // Acquire a temporary wakelock, since when we return from
                // this callback the MediaPlayer will release its wakelock
                // and allow the device to go to sleep.
                // This temporary wakelock is released when the RELEASE_WAKELOCK
                // message is processed, but just in case, put a timeout on it.
                mWakeLock.acquire(30000);
                mHandler.sendEmptyMessage(TRACK_ENDED);
                mHandler.sendEmptyMessage(RELEASE_WAKELOCK);
            }
        };

        MediaPlayer.OnErrorListener errorListener = new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                switch (what) {
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    mIsInitialized = false;
                    mMediaPlayer.release();
                    // Creating a new MediaPlayer and settings its wakemode does not
                    // require the media service, so it's OK to do this now, while the
                    // service is still being restarted
                    mMediaPlayer = new MediaPlayer(); 
                    mMediaPlayer.setWakeMode(MediaPlaybackService.this, PowerManager.PARTIAL_WAKE_LOCK);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(SERVER_DIED), 2000);
                    return true;
                default:
                    Log.d("MultiPlayer", "Error: " + what + "," + extra);
                    break;
                }
                return false;
           }
        };

        public long duration() {
            return mMediaPlayer.getDuration();
        }

        public long position() {
            return mMediaPlayer.getCurrentPosition();
        }

        public long seek(long whereto) {
            mMediaPlayer.seekTo((int) whereto);
            return whereto;
        }

        public void setVolume(float vol) {
            mMediaPlayer.setVolume(vol, vol);
        }

        public void setAudioSessionId(int sessionId) {
            mMediaPlayer.setAudioSessionId(sessionId);
        }

        public int getAudioSessionId() {
            return mMediaPlayer.getAudioSessionId();
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("" + mPlayListLen + " items in queue, currently at index " + mPlayPos);
        writer.println("Currently loaded:");
        writer.println(getArtistName());
        writer.println(getAlbumName());
        writer.println(getTrackName());
        writer.println(getPath());
        writer.println("playing: " + mIsSupposedToBePlaying);
        writer.println("actual: " + mPlayer.mMediaPlayer.isPlaying());
        MusicUtils.debugDump(writer);
    }
}
