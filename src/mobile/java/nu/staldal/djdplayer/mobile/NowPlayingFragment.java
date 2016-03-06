/*
 * Copyright (C) 2012 Mikael Ståldal
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
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.R;

public class NowPlayingFragment extends Fragment implements FragmentServiceConnection {
    @SuppressWarnings("UnusedDeclaration")
    private static final String LOGTAG = "NowPlayingFragment";

    private MediaPlayback service;

    private View nowPlaying;
    private TextView titleView;
    private TextView artistView;
    private ImageButton playPauseButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        nowPlaying = inflater.inflate(R.layout.nowplaying, container, false);

        titleView = (TextView) nowPlaying.findViewById(R.id.title);
        artistView = (TextView) nowPlaying.findViewById(R.id.artist);
        nowPlaying.setOnClickListener(v -> startActivity(new Intent(getActivity(), MediaPlaybackActivity.class)));

        View prevButton = nowPlaying.findViewById(R.id.prev);
        if (prevButton != null) {
            prevButton.setOnClickListener(v -> {
                if (service != null) service.previousOrRestartCurrent();
            });
        }
        nowPlaying.findViewById(R.id.next).setOnClickListener(v -> {
            if (service != null) service.next();
        });
        playPauseButton = (ImageButton) nowPlaying.findViewById(R.id.pause);
        playPauseButton.setOnClickListener(v -> {
            if (service != null) {
                if (service.isPlaying()) {
                    service.pause();
                } else {
                    service.play();
                }
            }
        });

        return nowPlaying;
    }

    @Override
    public void onServiceConnected(MediaPlayback s) {
        service = s;
        updateNowPlaying();
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        getActivity().registerReceiver(mStatusListener, f);
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(mStatusListener);
        super.onPause();
    }

    @Override
    public void onServiceDisconnected() {
        service = null;
    }

    private final BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                updateNowPlaying();
            } else if (intent.getAction().equals(MediaPlaybackService.META_CHANGED)) {
                updateNowPlaying();
            } else if (intent.getAction().equals(MediaPlaybackService.QUEUE_CHANGED)) {
                updateNowPlaying();
            }
        }
    };

    private void updateNowPlaying() {
        if (service != null && service.getAudioId() != -1) {
            nowPlaying.setVisibility(View.VISIBLE);

            titleView.setText(service.getTrackName());
            String artistName = service.getArtistName();
            if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                artistName = getString(R.string.unknown_artist_name);
            }
            artistView.setText(artistName);

            playPauseButton.setImageResource(service.isPlaying()
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play);
        } else {
            nowPlaying.setVisibility(View.GONE);
        }

    }
}
