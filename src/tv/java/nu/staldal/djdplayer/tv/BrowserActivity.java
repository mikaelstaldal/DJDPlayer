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

package nu.staldal.djdplayer.tv;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;

public class BrowserActivity extends Activity implements MusicUtils.Defs, ServiceConnection {

    private static final String LOGTAG = "BrowserActivity";

    private MusicUtils.ServiceToken token = null;
    private MediaPlayback service = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(LOGTAG, "onCreate - " + getIntent());

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setContentView(R.layout.tv_browser_activity);

        token = MusicUtils.bindToService(this, this, TvMediaPlaybackService.class);
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((MediaPlaybackService.LocalBinder) binder).getService();

        notifyFragmentConnected(R.id.browser_fragment, service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;

        notifyFragmentDisconnected(R.id.browser_fragment);

        finish();
    }

    @Override
    public void onDestroy() {
        if (token != null) MusicUtils.unbindFromService(token);
        service = null;

        super.onDestroy();
    }

    private void notifyFragmentConnected(int id, MediaPlayback service) {
        Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment != null && fragment.isInLayout()) {
            ((FragmentServiceConnection) fragment).onServiceConnected(service);
        }
    }

    private void notifyFragmentDisconnected(int id) {
        Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment != null && fragment.isInLayout()) {
            ((FragmentServiceConnection) fragment).onServiceDisconnected();
        }
    }

}
