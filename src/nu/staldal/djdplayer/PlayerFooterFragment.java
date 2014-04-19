/*
 * Copyright (C) 2014 Mikael Ståldal
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

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import nu.staldal.ui.RepeatingImageButton;

public class PlayerFooterFragment extends Fragment implements FragmentServiceConnection {

    @SuppressWarnings("unused")
    private static final String LOGTAG = "PlayerFooterFragment";

    private MediaPlaybackService service;

    private View mainView;
    private ImageButton mPauseButton;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private ProgressBar mProgress;
    private long mPosOverride = -1;
    private boolean paused;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private boolean mFromTouch = false;
    private long mDuration;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutId(), container, false);

        mainView = view.findViewById(R.id.player_footer);

        mCurrentTime = (TextView) view.findViewById(R.id.currenttime);
        mTotalTime = (TextView) view.findViewById(R.id.totaltime);
        mProgress = (ProgressBar) view.findViewById(android.R.id.progress);
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);

        RepeatingImageButton mPrevButton = (RepeatingImageButton) view.findViewById(R.id.prev);
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (service == null) return;
                if (service.position() < 2000) {
                    service.prev();
                } else {
                    service.seek(0);
                    service.play();
                }
            }
        });
        mPrevButton.setRepeatListener(new RepeatingImageButton.RepeatListener() {
            public void onRepeat(View v, long howlong, int repcnt) {
                scanBackward(repcnt, howlong);
            }
        }, 260);
        mPauseButton = (ImageButton) view.findViewById(R.id.pause);
        mPauseButton.requestFocus();
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doPauseResume();
            }
        });
        RepeatingImageButton mNextButton = (RepeatingImageButton) view.findViewById(R.id.next);
        mNextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (service == null) return;
                service.next(true);
            }
        });
        mNextButton.setRepeatListener(new RepeatingImageButton.RepeatListener() {
            public void onRepeat(View v, long howlong, int repcnt) {
                scanForward(repcnt, howlong);
            }
        }, 260);

        return view;
    }

    protected int getLayoutId() {
        return R.layout.player_footer;
    }

    @Override
    public void onServiceConnected(MediaPlaybackService s) {
        service = s;

        if (service.getAudioId() != -1) {
            setPauseButtonImage();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        paused = false;
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MediaPlaybackService.META_CHANGED);
        filter.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        getActivity().registerReceiver(mStatusListener, filter);

        setPauseButtonImage();        
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(mStatusListener);

        super.onPause();
    }

    @Override
    public void onStop() {
        paused = true;
        mHandler.removeMessages(REFRESH);

        super.onStop();
    }    

    @Override
    public void onServiceDisconnected() {
        service = null;
    }

    private final BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (service == null) return;
            switch (intent.getAction()) {
                case MediaPlaybackService.META_CHANGED:
                    mDuration = service.duration();
                    mTotalTime.setText(MusicUtils.formatDuration(getActivity(), mDuration));
                    setPauseButtonImage();
                    queueNextRefresh(1);
                    break;
                case MediaPlaybackService.PLAYSTATE_CHANGED:
                    setPauseButtonImage();
                    break;
            }
        }
    };

    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (service == null)) return;
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;
                service.seek(mPosOverride);

                // trackball event, allow progress updates
                if (!mFromTouch) {
                    refreshNow();
                    mPosOverride = -1;
                }
            }
        }
        public void onStopTrackingTouch(SeekBar bar) {
            mPosOverride = -1;
            mFromTouch = false;
        }
    };

    private void scanBackward(int repcnt, long delta) {
        if(service == null) return;
        if(repcnt == 0) {
            mStartSeekPos = service.position();
            mLastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos - delta;
            if (newpos < 0) {
                // move to previous track
                service.prev();
                long duration = service.duration();
                mStartSeekPos += duration;
                newpos += duration;
            }
            if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                service.seek(newpos);
                mLastSeekEventTime = delta;
            }
            if (repcnt >= 0) {
                mPosOverride = newpos;
            } else {
                mPosOverride = -1;
            }
            refreshNow();
        }
    }

    private void scanForward(int repcnt, long delta) {
        if(service == null) return;
        if(repcnt == 0) {
            mStartSeekPos = service.position();
            mLastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos + delta;
            long duration = service.duration();
            if (newpos >= duration) {
                // move to next track
                service.next(true);
                mStartSeekPos -= duration; // is OK to go negative
                newpos -= duration;
            }
            if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                service.seek(newpos);
                mLastSeekEventTime = delta;
            }
            if (repcnt >= 0) {
                mPosOverride = newpos;
            } else {
                mPosOverride = -1;
            }
            refreshNow();
        }
    }

    private void doPauseResume() {
        if (service != null) {
            if (service.isPlaying()) {
                service.pause();
            } else {
                service.play();
            }
            refreshNow();
            setPauseButtonImage();
        }
    }

    private void setPauseButtonImage() {
        if (service != null && service.isPlaying()) {
            mPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            mPauseButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private static final int REFRESH = 1;

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }
    
    private long refreshNow() {
        if(service == null)
            return 500;
        long pos = mPosOverride < 0 ? service.position() : mPosOverride;
        long remaining = 1000 - (pos % 1000);
        if ((pos >= 0) && (mDuration > 0)) {
            mCurrentTime.setText(MusicUtils.formatDuration(getActivity(), pos));

            if (service.isPlaying()) {
                mCurrentTime.setVisibility(View.VISIBLE);
            } else {
                // blink the counter
                int vis = mCurrentTime.getVisibility();
                mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                remaining = 500;
            }

            mProgress.setProgress((int) (1000 * pos / mDuration));
        } else {
            mCurrentTime.setText("--:--");
            mProgress.setProgress(1000);
        }
        // return the number of milliseconds until the next full second, so
        // the counter can be updated at just the right time
        return remaining;
    }
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;

                default:
                    break;
            }
        }
    };
    
    public void show() {
        mainView.setVisibility(View.VISIBLE);
    }

    public void hide() {
        mainView.setVisibility(View.GONE);
    }
}
