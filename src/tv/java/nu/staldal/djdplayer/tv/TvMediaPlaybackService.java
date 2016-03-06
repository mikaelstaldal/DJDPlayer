/*
 * Copyright (C) 2015-2016 Mikael Ståldal
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

import android.app.PendingIntent;
import android.content.Intent;
import android.media.session.MediaSession;
import nu.staldal.djdplayer.MediaPlaybackService;

public class TvMediaPlaybackService extends MediaPlaybackService {

    @SuppressWarnings("unused")
    private static final String TAG = TvMediaPlaybackService.class.getSimpleName();

    @Override
    protected void additionalCreate() {
        Intent intent = new Intent(this, PlaybackActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mSession.setSessionActivity(pendingIntent);

        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onStop() {
                pause();
                seek(0);
            }

            @Override
            public void onSkipToPrevious() {
                if (position() < PREV_THRESHOLD_MS) {
                    prev();
                } else {
                    seek(0);
                    play();
                }
            }

            @Override
            public void onSkipToNext() {
                next();
            }
        });
    }
}
