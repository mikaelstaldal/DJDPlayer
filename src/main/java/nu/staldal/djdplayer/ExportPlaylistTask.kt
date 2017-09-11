/*
 * Copyright (C) 2012-2017 Mikael Ståldal
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

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import nu.staldal.djdplayer.provider.MusicContract

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer

private const val LOGTAG = "ExportPlaylistTask"

class ExportPlaylistTask(private val context: Context) : AsyncTask<Any, Void, Array<Any>>() {

    override fun doInBackground(vararg params: Any): Array<Any> {
        val playlistName = params[0] as String
        val playlistId = params[1] as Long
        val shouldShare = params[2] as Boolean

        val musicDir = PreferenceManager.getDefaultSharedPreferences(context).getString(
                SettingsActivity.MUSIC_FOLDER,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath)

        val file = if (shouldShare)
            File(context.externalCacheDir, playlistName + ".m3u")
        else
            File(musicDir, playlistName + ".txt")

        export(playlistId, musicDir.length + 1, file)

        return arrayOf(file, shouldShare)
    }

    private fun export(playlistId: Long, prefix: Int, file: File) {
        var writer: Writer? = null
        var cursor: Cursor? = null
        try {
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file), "UTF-8"))
            cursor = context.contentResolver.query(
                    MusicContract.Playlist.getMembersUri(playlistId),
                    arrayOf(MediaStore.Audio.AudioColumns.DATA), null, null, null)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (isCancelled) break
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA))
                    writer.write(path, prefix, path.length - prefix)
                    writer.write('\n'.toInt())
                }
            } else {
                Log.w(LOGTAG, "Unable to get song list")
            }
        } catch (e: IOException) {
            Log.w(LOGTAG, "Unable to export playlist", e)
        } finally {
            try {
                if (cursor != null) cursor.close()
            } catch (e: Exception) {
                Log.w(LOGTAG, "Unable to close cursor", e)
            }

            try {
                if (writer != null) writer.close()
            } catch (e: IOException) {
                Log.w(LOGTAG, "Unable to close exported playlist", e)
            }

        }
    }

    override fun onPostExecute(params: Array<Any>) {
        val file = params[0] as File
        val shouldShare = params[1] as Boolean

        if (shouldShare) {
            val fileName = file.name
            share(file, fileName.substring(0, fileName.length - 4))
        } else {
            Toast.makeText(context, context.resources.getString(R.string.playlist_exported, file.absolutePath), Toast.LENGTH_LONG).show()
        }
    }

    private fun share(file: File, playlistName: CharSequence) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        intent.putExtra(Intent.EXTRA_SUBJECT, playlistName)
        intent.type = MusicUtils.AUDIO_X_MPEGURL

        val chooser = Intent.createChooser(intent, context.getString(R.string.share_via))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

}
