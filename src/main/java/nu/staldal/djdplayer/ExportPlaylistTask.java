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
package nu.staldal.djdplayer;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import nu.staldal.djdplayer.provider.MusicContract;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class ExportPlaylistTask extends AsyncTask<Object,Void,Object[]> {
    private static final String LOGTAG = "ExportPlaylistTask";

    private final Context context;

    public ExportPlaylistTask(Context context) {
        this.context = context;
    }

    @Override
    protected Object[] doInBackground(Object... params) {
        String playlistName = (String)params[0];
        long playlistId = (Long)params[1];
        boolean shouldShare = (Boolean)params[2];

        String musicDir = PreferenceManager.getDefaultSharedPreferences(context).getString(
                SettingsActivity.MUSIC_FOLDER,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());

        File file = shouldShare
                ? new File(context.getExternalCacheDir(), playlistName+".m3u")
                : new File(musicDir, playlistName+".txt");

        export(playlistId, musicDir.length()+1, file);

        return new Object[] { file, shouldShare };
    }

    private void export(long playlistId, int prefix, File file) {
        Writer writer = null;
        Cursor cursor = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            cursor = context.getContentResolver().query(
                    MusicContract.Playlist.getMembersUri(playlistId),
                    new String[]{MediaStore.Audio.AudioColumns.DATA},
                    null,
                    null,
                    null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (isCancelled()) break;
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA));
                    writer.write(path, prefix, path.length() - prefix);
                    writer.write('\n');
                }
            } else {
                Log.w(LOGTAG, "Unable to get song list");
            }
        } catch (IOException e) {
            Log.w(LOGTAG, "Unable to export playlist", e);
        } finally {
            try {
                if (cursor != null) cursor.close();
            } catch (Exception e) {
                Log.w(LOGTAG, "Unable to close cursor", e);
            }
            try {
                if (writer != null) writer.close();
            } catch (IOException e) {
                Log.w(LOGTAG, "Unable to close exported playlist", e);
            }
        }
    }

    @Override
    protected void onPostExecute(Object[] params) {
        File file = (File)params[0];
        boolean shouldShare = (Boolean)params[1];

        if (shouldShare) {
            String fileName = file.getName();
            share(file, fileName.substring(0, fileName.length()-4));
        } else {
            Toast.makeText(context, context.getResources().getString(R.string.playlist_exported, file.getAbsolutePath()), Toast.LENGTH_LONG).show();
        }
    }

    private void share(File file, CharSequence playlistName) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        intent.putExtra(Intent.EXTRA_SUBJECT, playlistName);
        intent.setType(MusicUtils.AUDIO_X_MPEGURL);

        Intent chooser = Intent.createChooser(intent, context.getString(R.string.share_via));
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }

}
