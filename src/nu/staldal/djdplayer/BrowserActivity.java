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
package nu.staldal.djdplayer;

import android.app.ListActivity;
import android.content.*;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

public abstract class BrowserActivity extends ListActivity
        implements View.OnCreateContextMenuListener, MusicUtils.Defs, ServiceConnection {

    protected MusicUtils.ServiceToken mToken;

    private View nowPlayingView;
    private TextView titleView;
    private TextView artistView;
    private ImageButton playButton;

    public void onServiceConnected(ComponentName name, IBinder service) {
        nowPlayingView = findViewById(R.id.nowplaying);
        titleView = (TextView) nowPlayingView.findViewById(R.id.title);
        artistView = (TextView) nowPlayingView.findViewById(R.id.artist);
        playButton = (ImageButton) nowPlayingView.findViewById(R.id.control_play);
        ImageButton nextButton = (ImageButton) nowPlayingView.findViewById(R.id.control_next);

        nowPlayingView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Context c = v.getContext();
                c.startActivity(new Intent(c, MediaPlaybackActivity.class));
            }
        });

        playButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (MusicUtils.isPlaying()) {
                    MusicUtils.pause();
                } else {
                    MusicUtils.play();
                }
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MusicUtils.next();
            }
        });

        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        registerReceiver(mStatusListener, new IntentFilter(f));

        updateNowPlaying();
    }

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                if (MusicUtils.isPlaying()) {
                    playButton.setImageResource(R.drawable.music_pause);
                } else {
                    playButton.setImageResource(R.drawable.music_play);
                }
            }
        }
    };

    public void onServiceDisconnected(ComponentName name) {
        unregisterReceiver(mStatusListener);
        finish();
    }

    protected final void updateNowPlaying() {
        try {
            if (MusicUtils.sService != null && MusicUtils.sService.getAudioId() != -1) {
                titleView.setText(MusicUtils.sService.getTrackName());
                String artistName = MusicUtils.sService.getArtistName();
                if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                    artistName = getString(R.string.unknown_artist_name);
                }
                artistView.setText(artistName);
                nowPlayingView.setVisibility(View.VISIBLE);
            }
        } catch (RemoteException ex) {
            nowPlayingView.setVisibility(View.GONE);
        }
    }

}
