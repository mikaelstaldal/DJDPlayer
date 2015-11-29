/*
 * Copyright (C) 2012-2014 Mikael Ståldal
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
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import nu.staldal.djdplayer.MusicUtils;

public class RescanActivity extends Activity {
    private static final String LOGTAG = "RescanActivity";

    @Override
    protected void onStart() {
        super.onStart();
        if (MusicUtils.android44OrLater()) {
            Log.w(LOGTAG, "Cannot rescan music on Android 4.4 or later");
        } else {
            Log.i(LOGTAG, "Rescanning music");
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(Environment.getExternalStorageDirectory())));
        }
        finish();
    }
}
