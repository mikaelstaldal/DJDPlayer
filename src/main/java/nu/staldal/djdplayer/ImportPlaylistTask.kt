/*
 * Copyright (C) 2015-2017 Mikael Ståldal
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
package nu.staldal.djdplayer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList

private const val LOGTAG = "ImportPlaylistTask"

class ImportPlaylistTask(private val context: Context) : AsyncTask<Uri, Void, CharSequence>() {

    override fun doInBackground(vararg params: Uri): CharSequence {
        val playlistUri = params[0]
        var playlistName = playlistUri.lastPathSegment
        if (playlistName.endsWith(".m3u"))
            playlistName = playlistName.substring(0, playlistName.length - 4)
        else if (playlistName.endsWith(".m3u8"))
            playlistName = playlistName.substring(0, playlistName.length - 5)
        return importPlaylist(playlistName, playlistUri)
    }

    private fun importPlaylist(name: String, playlistToImport: Uri): CharSequence {
        Log.i(LOGTAG, "Importing playlist: " + name)

        if (MusicUtils.playlistExists(context, name)) {
            return context.getString(R.string.playlist_already_exists)
        }

        val songIds = ArrayList<Long>()
        val musicDir = PreferenceManager.getDefaultSharedPreferences(context).getString(
                SettingsActivity.MUSIC_FOLDER,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath)

        context.contentResolver.openInputStream(playlistToImport).use { inputStream ->
            try {
                val br = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                while (true) {
                    val line = br.readLine() ?: break
                    parseLine(musicDir, songIds, line)
                }
            } catch (e: IOException) {
                Log.w(LOGTAG, "Unable to read playlist: " + playlistToImport.toString(), e)
                MusicUtils.reportError(context, "Unable to read playlist: " + playlistToImport.toString(), e)
                return context.getString(R.string.unable_to_import_playlist)
            }
        }

        val createdPlaylist = MusicUtils.createPlaylist(context, name)
        val ids = LongArray(songIds.size)
        for (i in ids.indices) ids[i] = songIds[i]
        MusicUtils.addToPlaylist(context, ids, createdPlaylist)

        if (ContentResolver.SCHEME_FILE == playlistToImport.scheme) {
            val successful = File(playlistToImport.path).delete()
            if (!successful) {
                Log.w(LOGTAG, "Unable to delete playlist file: " + playlistToImport.toString())
                MusicUtils.reportError(context, "Unable to delete playlist file: " + playlistToImport.toString())
            }
        }

        return context.getString(R.string.playlist_imported)
    }

    private fun parseLine(musicDir: String, songIds: ArrayList<Long>, line: String) {
        if (!line.isEmpty() && line[0] != '#') {
            val songPath = if (line[0] == '/') line else musicDir + '/' + line
            context.contentResolver.query(
                    MediaStore.Audio.Media.getContentUriForPath(songPath),
                    arrayOf(MediaStore.Audio.AudioColumns._ID),
                    MediaStore.Audio.AudioColumns.DATA + "=?",
                    arrayOf(songPath), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    songIds.add(id)
                }
            }
        }
    }

    override fun onPostExecute(message: CharSequence) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

}
