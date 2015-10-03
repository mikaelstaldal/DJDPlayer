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
package nu.staldal.djdplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class ImportPlaylistTask extends AsyncTask<Uri,Void,CharSequence> {
    private static final String LOGTAG = "ImportPlaylistTask";

    private final Context context;

    public ImportPlaylistTask(Context context) {
        this.context = context;
    }

    @Override
    protected CharSequence doInBackground(Uri... params) {
        Uri playlistUri = params[0];
        String playlistName = playlistUri.getLastPathSegment();
        if (playlistName.endsWith(".m3u"))
            playlistName = playlistName.substring(0, playlistName.length()-4);
        else if (playlistName.endsWith(".m3u8"))
            playlistName = playlistName.substring(0, playlistName.length()-5);
        return importPlaylist(playlistName, playlistUri);
    }

    private CharSequence importPlaylist(String name, Uri playlistToImport) {
        Log.i(LOGTAG, "Importing playlist: " + name);

        if (MusicUtils.playlistExists(context, name)) {
            return context.getString(R.string.playlist_already_exists);
        }

        ArrayList<Long> songIds = new ArrayList<>();
        String musicDir = PreferenceManager.getDefaultSharedPreferences(context).getString(
                SettingsActivity.MUSIC_FOLDER,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());

        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(playlistToImport);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            while (true) {
                String line = br.readLine();
                if (line == null) break;
                parseLine(musicDir, songIds, line);
            }
        } catch (IOException e) {
            Log.w(LOGTAG, "Unable to read playlist: " + playlistToImport.toString(), e);
            MusicUtils.reportError(context, "Unable to read playlist: " + playlistToImport.toString(), e);
            return context.getString(R.string.unable_to_import_playlist);
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException e) {
                Log.w(LOGTAG, "Unable to close playlist: " + playlistToImport.toString(), e);
                MusicUtils.reportError(context, "Unable to close playlist: " + playlistToImport.toString(), e);
            }
        }

        Uri createdPlaylist = MusicUtils.createPlaylist(context, name);
        long[] ids = new long[songIds.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = songIds.get(i);
        MusicUtils.addToPlaylist(context, ids, createdPlaylist);

        if (ContentResolver.SCHEME_FILE.equals(playlistToImport.getScheme())) {
            boolean successful = new File(playlistToImport.getPath()).delete();
            if (!successful) {
                Log.w(LOGTAG, "Unable to delete playlist file: " + playlistToImport.toString());
                MusicUtils.reportError(context, "Unable to delete playlist file: " + playlistToImport.toString());
            }
        }

        return context.getString(R.string.playlist_imported);
    }

    private void parseLine(String musicDir, ArrayList<Long> songIds, String line) {
        if (!line.isEmpty() && line.charAt(0) != '#') {
            String songPath = (line.charAt(0) == '/') ? line : musicDir + '/' + line;
            Cursor cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.getContentUriForPath(songPath),
                    new String[] { MediaStore.Audio.AudioColumns._ID },
                    MediaStore.Audio.AudioColumns.DATA + "=?",
                    new String[] { songPath },
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                songIds.add(id);
            }
            if (cursor != null) cursor.close();
        }
    }

    @Override
    protected void onPostExecute(CharSequence message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

}
