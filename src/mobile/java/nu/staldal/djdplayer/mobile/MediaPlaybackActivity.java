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

package nu.staldal.djdplayer.mobile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Toast;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.SettingsActivity;

public class MediaPlaybackActivity extends Activity implements ServiceConnection {

    private static final String LOGTAG = "MediaPlaybackActivity";

    private MusicUtils.ServiceToken token = null;
    private MediaPlayback service = null;

    private PlayerHeaderFragment playerHeaderFragment;
    private PlayQueueFragment playQueueFragment;
    private PlayerFooterFragment playerFooterFragment;
    private View playerHeaderDivider;
    private View playerFooterDivider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(LOGTAG, "onCreate - " + getIntent());

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        getActionBar().setHomeButtonEnabled(true);

        setContentView(R.layout.audio_player);

        playerHeaderFragment = (PlayerHeaderFragment)getFragmentManager().findFragmentById(R.id.player_header);
        playQueueFragment = (PlayQueueFragment)getFragmentManager().findFragmentById(R.id.playqueue);
        playerFooterFragment = (PlayerFooterFragment)getFragmentManager().findFragmentById(R.id.player_footer);
        playerHeaderDivider = findViewById(R.id.player_header_divider);
        playerFooterDivider = findViewById(R.id.player_footer_divider);

        token = MusicUtils.bindToService(this, this, MobileMediaPlaybackService.class);
    }

    @Override
    public void onNewIntent(Intent intent) {
        Log.i(LOGTAG, "onNewIntent - " + getIntent());
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        registerReceiver(mStatusListener, new IntentFilter(f));
    }

    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((MediaPlaybackService.LocalBinder)binder).getService();

        playerHeaderFragment.onServiceConnected(service);
        playQueueFragment.onServiceConnected(service);
        playerFooterFragment.onServiceConnected(service);

        invalidateOptionsMenu();
        updateTrackInfo();

        // Assume something is playing when the service says it is,
        // but also if the audio ID is valid but the service is paused.
        if (this.service.getAudioId() >= 0 || this.service.isPlaying()) {
            // something is playing now, we're done
            return;
        }
        // Service is dead or not playing anything. If we got here as part
        // of a "play this file" Intent, exit. Otherwise go to the Music app start screen.
        if (getIntent().getData() == null) {
            Intent intent = new Intent(MediaPlaybackActivity.this, MusicBrowserActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTrackInfo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.player_menu, menu);

        if (MusicUtils.android44OrLater() || !MusicUtils.hasMenuKey(this)) {
            menu.findItem(R.id.shuffle).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, 16, R.string.add_all_to_playlist);
        MusicUtils.makePlaylistMenu(this, sub, R.id.new_playlist, R.id.selected_playlist);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateSoundEffectItem(menu);

        updateRepeatItem(menu);

        updatePlayingItems(menu);

        return true;
    }

    private void updateSoundEffectItem(Menu menu) {
        Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        MenuItem item = menu.findItem(R.id.effect_panel);
        item.setVisible(getPackageManager().resolveActivity(i, 0) != null);
    }

    private void updateRepeatItem(Menu menu) {
        MenuItem item = menu.findItem(R.id.repeat);

        if (service != null) {
            switch (service.getRepeatMode()) {
                case MediaPlayback.REPEAT_ALL:
                    item.setIcon(R.drawable.ic_mp_repeat_all_btn);
                    break;
                case MediaPlayback.REPEAT_CURRENT:
                    item.setIcon(R.drawable.ic_mp_repeat_once_btn);
                    break;
                case MediaPlayback.REPEAT_STOPAFTER:
                    item.setIcon(R.drawable.ic_mp_repeat_stopafter_btn);
                    break;
                default:
                    item.setIcon(R.drawable.ic_mp_repeat_off_btn);
                    break;
            }
        } else {
            item.setIcon(R.drawable.ic_mp_repeat_off_btn);
        }
    }

    private void updatePlayingItems(Menu menu) {
        menu.setGroupVisible(R.id.playing_items, service != null && !service.isPlaying());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                Intent intent = new Intent(this, MusicBrowserActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return true;
            }

            case R.id.zoom_queue:
                if (playQueueFragment.isQueueZoomed()) {
                    playerHeaderFragment.show();
                    playerFooterFragment.show();
                    if (playerHeaderDivider != null) playerHeaderDivider.setVisibility(View.VISIBLE);
                    if (playerFooterDivider != null) playerFooterDivider.setVisibility(View.VISIBLE);
                    playQueueFragment.setQueueZoomed(false);
                    item.setIcon(R.drawable.ic_menu_zoom);
                } else {
                    playerHeaderFragment.hide();
                    playerFooterFragment.hide();
                    if (playerHeaderDivider != null) playerHeaderDivider.setVisibility(View.GONE);
                    if (playerFooterDivider != null) playerFooterDivider.setVisibility(View.GONE);
                    playQueueFragment.setQueueZoomed(true);
                    item.setIcon(R.drawable.ic_menu_unzoom);
                }
                return true;

            case R.id.repeat:
                cycleRepeat();
                return true;

            case R.id.shuffle:
                if (service != null) service.doShuffle();
                return true;

            case R.id.uniqueify:
                if (service != null) service.uniqueify();
                return true;

            case R.id.clear_queue:
                if (service != null) service.removeTracks(0, Integer.MAX_VALUE);
                return true;

            case R.id.new_playlist:
                if (service != null) CreatePlaylist.showMe(this, service.getQueue());
                return true;

            case R.id.selected_playlist:
                if (service != null) {
                    long playlist = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(this, service.getQueue(), playlist);
                }
                return true;

            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.search:
                return onSearchRequested();

            case R.id.effect_panel: {
                Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, service.getAudioSessionId());
                startActivityForResult(intent, 0);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void cycleRepeat() {
        if (service == null) {
            return;
        }
        int mode = service.getRepeatMode();
        if (mode == MediaPlayback.REPEAT_NONE) {
            service.setRepeatMode(MediaPlayback.REPEAT_ALL);
            Toast.makeText(this, R.string.repeat_all_notif, Toast.LENGTH_SHORT).show();
        } else if (mode == MediaPlayback.REPEAT_ALL) {
            service.setRepeatMode(MediaPlayback.REPEAT_CURRENT);
            Toast.makeText(this, R.string.repeat_current_notif, Toast.LENGTH_SHORT).show();
        } else if (mode == MediaPlayback.REPEAT_CURRENT) {
            service.setRepeatMode(MediaPlayback.REPEAT_STOPAFTER);
            Toast.makeText(this, R.string.repeat_stopafter_notif, Toast.LENGTH_SHORT).show();
        } else {
            service.setRepeatMode(MediaPlayback.REPEAT_NONE);
            Toast.makeText(this, R.string.repeat_off_notif, Toast.LENGTH_SHORT).show();
        }
        invalidateOptionsMenu();
    }

    public void onServiceDisconnected(ComponentName name) {
        service = null;

        playerHeaderFragment.onServiceDisconnected();
        playQueueFragment.onServiceDisconnected();
        playerFooterFragment.onServiceDisconnected();

        finish();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(mStatusListener);

        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (token != null) MusicUtils.unbindFromService(token);
        service = null;

        super.onDestroy();
    }

    private final BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTrackInfo();
        }
    };

    private void updateTrackInfo() {
        if (service == null) return;

        if (service.getQueueLength() > 0) {
            setTitle((service.getQueuePosition() + 1) + "/" + service.getQueueLength());
        } else {
            finish();
        }
    }
}
