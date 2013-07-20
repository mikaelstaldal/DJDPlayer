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

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.*;

public class ExportPlaylistTask extends AsyncTask<Object,Void,Void> {
    private static final String LOGTAG = "ExportPlaylistTask";

    private final Context context;

    public ExportPlaylistTask(Context context) {
        this.context = context;
    }

    @Override
    protected Void doInBackground(Object... params) {
        String playlistName = (String)params[0];
        long[] songs = (long[])params[1];

        String dir = PreferenceManager.getDefaultSharedPreferences(context).getString(
                SettingsActivity.MUSIC_FOLDER,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
        int prefix = dir.length()+1;
        File file = new File(dir, playlistName+".txt");
        Writer writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            for (long song : songs) {
                if (isCancelled()) break;
                Cursor cursor = context.getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String[] { MediaStore.Audio.Media.DATA },
                        MediaStore.Audio.Media._ID + "=" + song,
                        null, null);
                cursor.moveToFirst();
                String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                cursor.close();
                writer.write(path, prefix, path.length() - prefix);
                writer.write('\n');
            }
        } catch (IOException e) {
            Log.w(LOGTAG, "Unable to export playlist", e);
        } finally {
            try {
                if (writer != null) writer.close();
            } catch (IOException e) {
                Log.w(LOGTAG, "Unable to close exported playlist", e);
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        Toast.makeText(context, R.string.playlist_exported, Toast.LENGTH_SHORT).show();
    }
}
