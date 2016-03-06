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
package nu.staldal.djdplayer.mobile;

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
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;
import nu.staldal.ui.RepeatingImageButton;

public class PlayerFooterFragment extends Fragment implements FragmentServiceConnection {

    @SuppressWarnings("unused")
    private static final String LOGTAG = "PlayerFooterFragment";

    private MediaPlayback service;

    private View mainView;
    private TextView currentTime;
    private TextView totalTime;
    private ProgressBar progressBar;
    private ImageButton pauseButton;

    private long posOverride = -1;
    private boolean paused = true;
    private long startSeekPos = 0;
    private long lastSeekEventTime = 0;
    private boolean fromTouch = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutId(), container, false);

        mainView = view.findViewById(R.id.player_footer);

        currentTime = (TextView) view.findViewById(R.id.currenttime);
        totalTime = (TextView) view.findViewById(R.id.totaltime);

        progressBar = (ProgressBar) view.findViewById(android.R.id.progress);
        if (progressBar instanceof SeekBar) {
            SeekBar seeker = (SeekBar) progressBar;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        progressBar.setMax(1000);

        RepeatingImageButton prevButton = (RepeatingImageButton) view.findViewById(R.id.prev);
        prevButton.setOnClickListener(v -> {
            if (service == null) return;
            service.previousOrRestartCurrent();
        });
        prevButton.setRepeatListener((v, howlong, repcnt) -> scanBackward(repcnt, howlong), 260);
        pauseButton = (ImageButton) view.findViewById(R.id.pause);
        pauseButton.requestFocus();
        pauseButton.setOnClickListener(v -> doPauseResume());
        RepeatingImageButton nextButton = (RepeatingImageButton) view.findViewById(R.id.next);
        nextButton.setOnClickListener(v -> {
            if (service == null) return;
            service.next();
        });
        nextButton.setRepeatListener((v, howlong, repcnt) -> scanForward(repcnt, howlong), 260);

        return view;
    }

    protected int getLayoutId() {
        return R.layout.player_footer;
    }

    @Override
    public void onServiceConnected(MediaPlayback s) {
        service = s;

        if (service.getAudioId() != -1) {
            setPauseButtonImage();
            totalTime.setText(MusicUtils.formatDuration(getActivity(), service.duration()));
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
        handler.removeMessages(REFRESH);

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
                    totalTime.setText(MusicUtils.formatDuration(getActivity(), service.duration()));
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
            lastSeekEventTime = 0;
            fromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (service == null)) return;
            long now = SystemClock.elapsedRealtime();
            if ((now - lastSeekEventTime) > 250) {
                lastSeekEventTime = now;
                posOverride = service.duration() * progress / 1000;
                service.seek(posOverride);

                // trackball event, allow progress updates
                if (!fromTouch) {
                    refreshNow();
                    posOverride = -1;
                }
            }
        }
        public void onStopTrackingTouch(SeekBar bar) {
            posOverride = -1;
            fromTouch = false;
        }
    };

    private void scanBackward(int repcnt, long delta) {
        if(service == null) return;
        if(repcnt == 0) {
            startSeekPos = service.position();
            lastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = startSeekPos - delta;
            if (newpos < 0) {
                // move to previous track
                service.previous();
                long duration = service.duration();
                startSeekPos += duration;
                newpos += duration;
            }
            if (((delta - lastSeekEventTime) > 250) || repcnt < 0){
                service.seek(newpos);
                lastSeekEventTime = delta;
            }
            if (repcnt >= 0) {
                posOverride = newpos;
            } else {
                posOverride = -1;
            }
            refreshNow();
        }
    }

    private void scanForward(int repcnt, long delta) {
        if(service == null) return;
        if(repcnt == 0) {
            startSeekPos = service.position();
            lastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = startSeekPos + delta;
            long duration = service.duration();
            if (newpos >= duration) {
                // move to next track
                service.next();
                startSeekPos -= duration; // is OK to go negative
                newpos -= duration;
            }
            if (((delta - lastSeekEventTime) > 250) || repcnt < 0){
                service.seek(newpos);
                lastSeekEventTime = delta;
            }
            if (repcnt >= 0) {
                posOverride = newpos;
            } else {
                posOverride = -1;
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
            pauseButton.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            pauseButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private static final int REFRESH = 1;

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = handler.obtainMessage(REFRESH);
            handler.removeMessages(REFRESH);
            handler.sendMessageDelayed(msg, delay);
        }
    }
    
    private long refreshNow() {
        if (service == null) return 500;

        long pos = posOverride < 0 ? service.position() : posOverride;
        long remaining = 1000 - (pos % 1000);
        long duration = service.duration();
        if ((pos >= 0) && (duration > 0)) {
            currentTime.setText(MusicUtils.formatDuration(getActivity(), pos));

            if (service.isPlaying()) {
                currentTime.setVisibility(View.VISIBLE);
            } else {
                // blink the counter
                int vis = currentTime.getVisibility();
                currentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                remaining = 500;
            }

            progressBar.setProgress((int) (1000 * pos / duration));
        } else {
            currentTime.setText("--:--");
            progressBar.setProgress(1000);
        }
        // return the number of milliseconds until the next full second, so
        // the counter can be updated at just the right time
        return remaining;
    }
    
    private final Handler handler = new Handler() {
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
