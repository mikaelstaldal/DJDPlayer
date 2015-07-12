/*
 * Copyright (C) 2015 Mikael Ståldal
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

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;

class MyMediaPlayer {
    private static final String LOGTAG = "MyMediaPlayer";

    public static final int TRACK_ENDED = 1;
    public static final int RELEASE_WAKELOCK = 2;
    public static final int SERVER_DIED = 3;

    private final Context mContext;
    private final Handler mHandler;
    private final PowerManager.WakeLock mWakeLock;

    private MediaPlayer mMediaPlayer;
    private boolean mIsInitialized;

    public MyMediaPlayer(Context context, Handler handler) {
        this.mContext = context;
        this.mHandler = handler;

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);

        mIsInitialized = false;
    }

    private final MediaPlayer.OnCompletionListener listener = new MediaPlayer.OnCompletionListener() {
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

    private final MediaPlayer.OnErrorListener errorListener = new MediaPlayer.OnErrorListener() {
        public boolean onError(MediaPlayer mp, int what, int extra) {
            switch (what) {
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    Log.i(LOGTAG, "MediaPlayer died, restarting");
                    mIsInitialized = false;
                    mMediaPlayer.release();
                    // Creating a new MediaPlayer and settings its wake mode does not
                    // require the media service, so it's OK to do this now, while the
                    // service is still being restarted
                    mMediaPlayer = new MediaPlayer();
                    mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(SERVER_DIED), 2000);
                    return true;

                default:
                    Log.w(LOGTAG, "Error: " + what + "," + extra);
                    return false;
            }
        }
    };

    /**
     * @return true if successful, false if failed
     */
    public boolean prepare(String path) {
        try {
            mMediaPlayer.reset();
            mMediaPlayer.setOnPreparedListener(null);
            if (path.startsWith("content://")) {
                mMediaPlayer.setDataSource(mContext, Uri.parse(path));
            } else {
                mMediaPlayer.setDataSource(path);
            }
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.prepare();
        } catch (IOException | IllegalArgumentException e) {
            Log.w(LOGTAG, "Couldn't open audio file: " + path, e);
            mIsInitialized = false;
            return false;
        }
        mMediaPlayer.setOnCompletionListener(listener);
        mMediaPlayer.setOnErrorListener(errorListener);

        Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mContext.getPackageName());
        mContext.sendBroadcast(i);

        Log.d(LOGTAG, "Prepared song: " + path);

        mIsInitialized = true;
        return true;
    }

    public boolean isInitialized() {
        return mIsInitialized;
    }

    public boolean isPlaying() {
        return mMediaPlayer.isPlaying();
    }

    public void start() {
        mMediaPlayer.start();
    }

    public void pause() {
        mMediaPlayer.pause();
    }

    public void stop() {
        mMediaPlayer.reset();
        mIsInitialized = false;
    }

    public long duration() {
        return mMediaPlayer.getDuration();
    }

    public long currentPosition() {
        return mMediaPlayer.getCurrentPosition();
    }

    public void seek(long whereto) {
        mMediaPlayer.seekTo((int)whereto);
    }

    public void setVolume(float vol) {
        mMediaPlayer.setVolume(vol, vol);
    }

    public int getAudioSessionId() {
        return mMediaPlayer.getAudioSessionId();
    }

    public void releaseWakeLock() {
        mWakeLock.release();
    }

    /**
     * You CANNOT use this player anymore after calling release()
     */
    public void release() {
        stop();

        Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mContext.getPackageName());
        mContext.sendBroadcast(i);

        mMediaPlayer.release();
        mWakeLock.release();
    }

}
