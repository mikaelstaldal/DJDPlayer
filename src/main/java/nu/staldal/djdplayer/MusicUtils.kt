/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.view.SubMenu
import android.view.ViewConfiguration
import android.widget.Toast
import nu.staldal.djdplayer.provider.ID3Utils
import java.io.CharArrayWriter
import java.io.File
import java.io.PrintWriter
import java.util.Formatter
import java.util.HashMap
import java.util.Locale
import java.util.Random

private const val LOGTAG = "MusicUtils"

object MusicUtils {

    const val AUDIO_X_MPEGURL = "audio/x-mpegurl"

    val sEmptyList = LongArray(0)

    var sService: MediaPlayback? = null
    private val sConnectionMap = HashMap<Context, ServiceBinder>()

    private val isPlaying: Boolean
        get() = if (MusicUtils.sService != null) {
            sService!!.isPlaying
        } else false

    private var sContentValuesCache: Array<ContentValues?>? = null

    /*  Try to use String.format() as little as possible, because it creates a
     *  new Formatter every time you call it, which is very inefficient.
     *  Reusing an existing Formatter more than tripled the speed of
     *  formatDuration().
     *  This Formatter/StringBuilder are also used by makeAlbumSongsLabel()
     */
    private val sFormatBuilder = StringBuilder()
    private val sFormatter = Formatter(sFormatBuilder, Locale.getDefault())
    private val sTimeArgs = arrayOfNulls<Any>(5)

    /**
     * This is now only used for the query screen
     */
    fun makeAlbumsSongsLabel(context: Context, numalbums: Int, numsongs: Int, isUnknown: Boolean): String {
        // There are several formats for the albums/songs information:
        // "1 Song"   - used if there is only 1 song
        // "N Songs" - used for the "unknown artist" item
        // "1 Album"/"N Songs"
        // "N Album"/"M Songs"
        // Depending on locale, these may need to be further subdivided

        val songs_albums = StringBuilder()

        if (numsongs == 1) {
            songs_albums.append(context.resources.getQuantityString(R.plurals.Nsongs, 1))
        } else {
            val r = context.resources
            if (!isUnknown) {
                val f = r.getQuantityText(R.plurals.Nalbums, numalbums).toString()
                sFormatBuilder.setLength(0)
                sFormatter.format(f, numalbums)
                songs_albums.append(sFormatBuilder)
                songs_albums.append(context.getString(R.string.albumsongseparator))
            }
            val f = r.getQuantityText(R.plurals.Nsongs, numsongs).toString()
            sFormatBuilder.setLength(0)
            sFormatter.format(f, numsongs)
            songs_albums.append(sFormatBuilder)
        }
        return songs_albums.toString()
    }

    class ServiceToken internal constructor(internal val mWrappedContext: ContextWrapper)

    fun bindToService(context: Activity, callback: ServiceConnection,
                      serviceClass: Class<out MediaPlaybackService>): ServiceToken? {
        val cw = ContextWrapper(context.parent ?: context)
        cw.startService(Intent(cw, serviceClass))
        val sb = ServiceBinder(callback)
        return if (cw.bindService(Intent().setClass(cw, serviceClass), sb, 0)) {
            sConnectionMap.put(cw, sb)
            ServiceToken(cw)
        } else {
            Log.e(LOGTAG, "Failed to bind to service")
            null
        }
    }

    fun unbindFromService(token: ServiceToken?) {
        if (token == null) {
            Log.e(LOGTAG, "Trying to unbind with null token")
            return
        }
        val cw = token.mWrappedContext
        val sb = sConnectionMap.remove(cw)
        if (sb == null) {
            Log.e(LOGTAG, "Trying to unbind for unknown Context")
            return
        }
        cw.unbindService(sb)
        if (sConnectionMap.isEmpty()) {
            // presumably there is nobody interested in the service at this point,
            // so don't hang on to the ServiceConnection
            sService = null
        }
    }

    private class ServiceBinder internal constructor(internal val mCallback: ServiceConnection?) : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: android.os.IBinder) {
            sService = (service as MediaPlaybackService.LocalBinder).service
            mCallback?.onServiceConnected(className, service)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            mCallback?.onServiceDisconnected(className)
            sService = null
        }
    }

    fun getSongListForCursor(cursor: Cursor?): LongArray =
        cursor?.let { c ->
            val len = c.count
            if (len == 0) {
                return sEmptyList
            }
            val list = LongArray(len)
            c.moveToFirst()
            val columnIndex: Int = try {
                c.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID)
            } catch (ex: IllegalArgumentException) {
                c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID)
            }

            for (i in 0 until len) {
                list[i] = c.getLong(columnIndex)
                c.moveToNext()
            }
            return list
        } ?: sEmptyList

    /**
     * Fills out the given submenu with items for "new playlist" and
     * any existing playlists. When the user selects an item, the
     * application will receive PLAYLIST_SELECTED with the Uri of
     * the selected playlist, NEW_PLAYLIST if a new playlist
     * should be created, and ADD_TO_CURRENT_PLAYLIST if the "current playlist" was
     * selected.
     * @param context The context to use for creating the menu items
     * @param sub The submenu to add the items to.
     */
    fun makePlaylistMenu(context: Context, sub: SubMenu, newPlaylist: Int, playlistSelected: Int) {
        val cols = arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME)
        val resolver = context.contentResolver
        if (resolver == null) {
            Log.w(LOGTAG, "resolver = null")
        } else {
            val whereClause = MediaStore.Audio.Playlists.NAME + " != ''"
            val cur = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    cols, whereClause, null, MediaStore.Audio.Playlists.NAME)
            sub.clear()
            sub.add(1, newPlaylist, 0, R.string.new_playlist)
            if (cur != null && cur.count > 0) {
                cur.moveToFirst()
                while (!cur.isAfterLast) {
                    val intent = Intent()
                    intent.putExtra("playlist", cur.getLong(0))
                    sub.add(1, playlistSelected, 0, cur.getString(1)).intent = intent
                    cur.moveToNext()
                }
            }
            cur?.close()
        }
    }

    fun deleteTracks(context: Context, list: LongArray) {
        val cols = arrayOf(MediaStore.Audio.AudioColumns._ID, MediaStore.Audio.AudioColumns.DATA, MediaStore.Audio.AudioColumns.ALBUM_ID)
        val where = StringBuilder()
        where.append(MediaStore.Audio.AudioColumns._ID + " IN (")
        for (i in list.indices) {
            where.append(list[i])
            if (i < list.size - 1) {
                where.append(",")
            }
        }
        where.append(")")

        query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                where.toString(), null, null)?.use { cursor ->
            // step 1: remove selected tracks from the current playlist
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                // remove from current playlist
                val id = cursor.getLong(0)
                sService!!.removeTrack(id)
                cursor.moveToNext()
            }

            // step 2: remove selected tracks from the database
            context.contentResolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, where.toString(), null)

            // step 3: remove files from card
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val name = cursor.getString(1)
                val f = File(name)
                try {  // File.delete can throw a security exception
                    if (!f.delete()) {
                        // I'm not sure if we'd ever get here (deletion would
                        // have to fail, but no exception thrown)
                        Log.e(LOGTAG, "Failed to delete file " + name)
                    }
                    cursor.moveToNext()
                } catch (ex: SecurityException) {
                    cursor.moveToNext()
                }
            }
        }

        val message = context.resources.getQuantityString(R.plurals.NNNtracksdeleted, list.size, list.size)

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        // We deleted a number of tracks, which could affect any number of things
        // in the media content domain, so update everything.
        context.contentResolver.notifyChange(Uri.parse("content://media"), null)
    }

    fun playSong(context: Context, id: Long) {
        when (PreferenceManager.getDefaultSharedPreferences(context).getString(
                SettingsActivity.CLICK_ON_SONG, SettingsActivity.PLAY_NEXT)) {
            SettingsActivity.PLAY_NOW -> MusicUtils.queueAndPlayImmediately(context, longArrayOf(id))
            SettingsActivity.QUEUE -> MusicUtils.queue(context, longArrayOf(id))
            else -> MusicUtils.queueNextAndPlayIfNotAlreadyPlaying(context, longArrayOf(id))
        }
    }

    fun queueNextAndPlayIfNotAlreadyPlaying(context: Context, songs: LongArray) {
        if (isPlaying) {
            queueNext(context, songs)
        } else {
            queueAndPlayImmediately(context, songs)
        }
    }

    fun queue(context: Context, list: LongArray) {
        sService?.let { service ->
            service.enqueue(list, MediaPlayback.LAST)
            val message = context.resources.getQuantityString(
                    R.plurals.NNNtrackstoplayqueue, list.size, list.size)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun interleave(context: Context, list: LongArray, currentCount: Int, newCount: Int) {
        sService?.let { service ->
            service.interleave(list, currentCount, newCount)
            val message = context.resources.getQuantityString(
                    R.plurals.NNNtrackstoplayqueue, list.size, list.size)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun queueNext(context: Context, songs: LongArray) {
        sService?.let { service ->
            service.enqueue(songs, MediaPlayback.NEXT)
            Toast.makeText(context, R.string.will_play_next, Toast.LENGTH_SHORT).show()
        }
    }

    fun queueAndPlayImmediately(context: Context, songs: LongArray) {
        sService?.enqueue(songs, MediaPlayback.NOW)
    }

    /**
     * @param ids The source array containing all the ids to be added to the playlist
     * @param offset Where in the 'ids' array we start reading
     * @param length How many items to copy during this pass
     * @param base The play order offset to use for this pass
     */
    private fun makeInsertItems(ids: LongArray, offset: Int, length: Int, base: Int) {
        var len = length
        // adjust 'length' if would extend beyond the end of the source array
        if (offset + len > ids.size) {
            len = ids.size - offset
        }
        // allocate the ContentValues array, or reallocate if it is the wrong size
        if (sContentValuesCache == null || sContentValuesCache!!.size != len) {
            sContentValuesCache = arrayOfNulls<ContentValues>(len)
        }
        // fill in the ContentValues array with the right values for this pass
        for (i in 0 until len) {
            if (sContentValuesCache!![i] == null) {
                sContentValuesCache!![i] = ContentValues()
            }

            sContentValuesCache!![i]!!.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, base + offset + i)
            sContentValuesCache!![i]!!.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, ids[offset + i])
        }
    }

    fun addToPlaylist(context: Context, ids: LongArray?, playlistid: Long) {
        if (ids == null) {
            // this shouldn't happen (the menuitems shouldn't be visible
            // unless the selected item represents something playable
            Log.e(LOGTAG, "ListSelection null")
        } else {
            val uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistid)
            val numInserted = addToPlaylist(context, ids, uri)
            Toast.makeText(context, context.resources.getQuantityString(
                    R.plurals.NNNtrackstoplaylist, numInserted, numInserted), Toast.LENGTH_SHORT).show()
        }
    }

    fun addToPlaylist(context: Context, ids: LongArray, uri: Uri): Int {
        val size = ids.size
        val resolver = context.contentResolver
        // need to determine the number of items currently in the playlist,
        // so the play_order field can be maintained.
        val cur = resolver.query(uri, arrayOf("count(*)"), null, null, null)
        if (cur != null) {
            cur.moveToFirst()
            val base = cur.getInt(0)
            cur.close()
            var numInserted = 0
            var i = 0
            while (i < size) {
                makeInsertItems(ids, i, 1000, base)
                numInserted += resolver.bulkInsert(uri, sContentValuesCache)
                i += 1000
            }
            return numInserted
        } else {
            Log.w(LOGTAG, "Unable to lookup playlist: " + uri.toString())
            return -1
        }
    }

    fun query(context: Context, uri: Uri, projection: Array<String>?,
              selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        try {
            val resolver = context.contentResolver ?: return null
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder)
        } catch (ex: UnsupportedOperationException) {
            return null
        }
    }

    fun formatDuration(context: Context, millis: Long): String {
        val secs = millis / 1000
        val durationformat = context.getString(
                if (secs < 3600) R.string.durationformatshort else R.string.durationformatlong)

        // Provide multiple arguments so the format can be changed easily by modifying the xml.
        sFormatBuilder.setLength(0)

        val timeArgs = sTimeArgs
        timeArgs[0] = secs / 3600
        timeArgs[1] = secs / 60
        timeArgs[2] = secs / 60 % 60
        timeArgs[3] = secs
        timeArgs[4] = secs % 60

        return sFormatter.format(durationformat, *timeArgs).toString()
    }

    fun playAll(context: Context, list: LongArray) {
        if (list.size == 0 || sService == null) {
            Log.d(LOGTAG, "attempt to play empty song list")
            // Don't try to play empty playlists. Nothing good will come of it.
            val message = context.getString(R.string.emptyplaylist)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }
        sService!!.load(list, 0)
    }

    fun shuffleArray(array: LongArray) {
        val random = Random()
        for (i in array.indices) {
            val randomPosition = random.nextInt(array.size)
            val temp = array[i]
            array[i] = array[randomPosition]
            array[randomPosition] = temp
        }
    }

    fun setIntPref(context: Context, name: String, value: Int) {
        val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        val ed = prefs.edit()
        ed.putInt(name, value)
        ed.apply()
    }

    fun setStringPref(context: Context, name: String, value: String) {
        val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        val ed = prefs.edit()
        ed.putString(name, value)
        ed.apply()
    }

    fun fetchGenre(context: Context, songId: Long): IdAndName? =
        context.contentResolver.query(
                Uri.parse("content://media/external/audio/media/" + songId.toString() + "/genres"),
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                IdAndName(
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)),
                        ID3Utils.decodeGenre(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)))!!)
            } else {
                null
            }
        }

    /**
     * Cursor should be positioned on the entry to be checked
     * Returns false if the entry matches the naming pattern used for recordings,
     * or if it is marked as not music in the database.
     */
    fun isMusic(cursor: Cursor): Boolean {
        val titleidx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE)
        val albumidx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM)
        val artistidx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST)

        val title = cursor.getString(titleidx)
        val album = cursor.getString(albumidx)
        val artist = cursor.getString(artistidx)
        if (MediaStore.UNKNOWN_STRING == album &&
                MediaStore.UNKNOWN_STRING == artist &&
                title != null &&
                title.startsWith("recording")) {
            // not music
            return false
        }

        val ismusic_idx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.IS_MUSIC)
        var ismusic = true
        if (ismusic_idx >= 0) {
            ismusic = cursor.getInt(ismusic_idx) != 0
        }
        return ismusic
    }

    fun shareVia(audioId: Long, mimeType: String, resources: Resources): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_STREAM,
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId))
        intent.type = mimeType

        return Intent.createChooser(intent, resources.getString(R.string.share_via))
    }

    fun searchForTrack(trackName: String, artistName: String, resources: Resources): Intent {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val query: String
        if (MediaStore.UNKNOWN_STRING == artistName) {
            query = trackName
        } else {
            query = artistName + " " + trackName
            intent.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artistName)
        }
        intent.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "audio/*")
        intent.putExtra(SearchManager.QUERY, query)

        return Intent.createChooser(intent, resources.getString(R.string.mediasearch, trackName))
    }

    fun searchForCategory(categoryName: CharSequence?, contentType: String, resources: Resources): Intent {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        intent.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, contentType)
        intent.putExtra(SearchManager.QUERY, categoryName)

        return Intent.createChooser(intent, resources.getString(R.string.mediasearch, categoryName))
    }

    fun isLong(s: String?): Boolean {
        if (s == null) return false
        return try {
            java.lang.Long.parseLong(s)
            true
        } catch (e: NumberFormatException) {
            false
        }
    }

    fun createPlaylist(context: Context, name: String): Uri {
        val values = ContentValues(1)
        values.put(MediaStore.Audio.Playlists.NAME, name)
        return context.contentResolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values)
    }

    fun renamePlaylist(context: Context, playlistId: Long, name: String?) {
        if (name != null && name.length > 0) {
            if (playlistExists(context, name)) {
                Toast.makeText(context, R.string.playlist_already_exists, Toast.LENGTH_SHORT).show()
            } else {
                val values = ContentValues(1)
                values.put(MediaStore.Audio.Playlists.NAME, name)
                context.contentResolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                        values,
                        MediaStore.Audio.Playlists._ID + "=?",
                        arrayOf(java.lang.Long.valueOf(playlistId)!!.toString()))

                Toast.makeText(context, R.string.playlist_renamed_message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playlistExists(context: Context, name: String): Boolean {
        val cursor = MusicUtils.query(context, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Playlists._ID),
                MediaStore.Audio.Playlists.NAME + "=?",
                arrayOf(name), null)

        return cursor != null && cursor.moveToFirst()
    }

    fun android44OrLater(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
    }

    fun hasMenuKey(context: Context): Boolean {
        return ViewConfiguration.get(context).hasPermanentMenuKey()
    }

    fun reportError(context: Context, text: String, t: Throwable? = null) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        if (t != null) {
            val buffer = CharArrayWriter()
            val pw = PrintWriter(buffer)
            pw.append(text)
            pw.append('\n')
            t.printStackTrace(pw)
            pw.flush()
            intent.putExtra(Intent.EXTRA_TEXT, buffer.toString())
        } else {
            intent.putExtra(Intent.EXTRA_TEXT, text)
        }
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("djdplayer@staldal.nu"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "Error report from DJD Player")

        val chooser = Intent.createChooser(intent, context.getString(R.string.report_error))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
