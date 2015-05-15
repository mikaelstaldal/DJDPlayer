/*
 * Copyright (C) 2013 Mikael Ståldal
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
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.widget.Toast;

import java.util.Random;

public class ShufflePlaylistTask extends AsyncTask<Object,Void,Void> {
    @SuppressWarnings("unused")
    private static final String LOGTAG = "ShufflePlaylistTask";

    private final Context context;

    public ShufflePlaylistTask(Context context) {
        this.context = context;
    }

    @Override
    protected Void doInBackground(Object... params) {
        Random random = new Random();
        long playlistId = (Long)params[0];
        long[] songs = (long[])params[1];
        for (int i=0; i < songs.length; i++) {
            int randomPosition = random.nextInt(songs.length);
            MediaStore.Audio.Playlists.Members.moveItem(context.getContentResolver(), playlistId, i, randomPosition);
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        Toast.makeText(context, R.string.playlist_shuffled, Toast.LENGTH_SHORT).show();
    }
}
