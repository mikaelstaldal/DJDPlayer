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
package nu.staldal.djdplayer.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MergeCursor
import android.net.Uri
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.BaseColumns
import android.provider.MediaStore
import android.util.Log
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.SettingsActivity
import java.io.File
import java.io.FileFilter
import java.util.Arrays

private const val LOGTAG = "MusicProvider"

class MusicProvider : ContentProvider() {

    private val MEDIA_STORE_MEMBER_CURSOR_COLS = arrayOf(
            MediaStore.Audio.AudioColumns._ID,
            MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.DATA,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.ARTIST_ID,
            MediaStore.Audio.AudioColumns.DURATION,
            MediaStore.Audio.AudioColumns.MIME_TYPE
    )

    private val MEDIA_STORE_PLAYLIST_MEMBER_CURSOR_COLS = arrayOf(
            MediaStore.Audio.Playlists.Members._ID,
            MediaStore.Audio.Playlists.Members.TITLE,
            MediaStore.Audio.Playlists.Members.DATA,
            MediaStore.Audio.Playlists.Members.ALBUM,
            MediaStore.Audio.Playlists.Members.ARTIST,
            MediaStore.Audio.Playlists.Members.ARTIST_ID,
            MediaStore.Audio.Playlists.Members.DURATION,
            MediaStore.Audio.Playlists.Members.MIME_TYPE,
            MediaStore.Audio.Playlists.Members.PLAY_ORDER,
            MediaStore.Audio.Playlists.Members.AUDIO_ID,
            MediaStore.Audio.Playlists.Members.IS_MUSIC
    )

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
        when (sURIMatcher.match(uri)) {
            FOLDER -> fetchFolders()

            PLAYLIST -> fetchPlaylists()

            GENRE -> fetchGenres()

            ARTIST -> fetchArtists()

            ALBUM -> fetchAlbums()

            FOLDER_MEMBERS -> fetchFolder(uri.lastPathSegment)

            PLAYLIST_MEMBERS -> fetchPlaylist(ContentUris.parseId(uri))

            GENRE_MEMBERS -> fetchGenre(ContentUris.parseId(uri))

            ARTIST_MEMBERS -> fetchArtist(ContentUris.parseId(uri))

            ALBUM_MEMBERS -> fetchAlbum(ContentUris.parseId(uri))

            MUSIC_MEMBERS -> fetchMusic()

            else -> null
        }

    private fun fetchFolders(): Cursor {
        val root = fetchRoot(context)
        val cursor = MatrixCursor(arrayOf<String>(BaseColumns._ID, BaseColumns._COUNT,
                MusicContract.FolderColumns.PATH, MusicContract.FolderColumns.NAME))
        val counter = IntArray(1)
        processFolder(cursor, counter, root, root)
        return cursor
    }

    private fun fetchFolder(folder: String): Cursor? =
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MEDIA_STORE_MEMBER_CURSOR_COLS,
            MediaStore.Audio.AudioColumns.TITLE + " != ''"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " LIKE ?"
                    + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1",
            arrayOf(folder + "%"),
            MediaStore.Audio.AudioColumns.DATA)

    private fun fetchPlaylist(id: Long): Cursor? =
        when (id) {
            MusicContract.Playlist.ALL_SONGS -> fetchMusic()
            MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST -> {
                // do a query for all songs added in the last X weeks
                val context = context
                val weeks = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
                        .getInt(SettingsActivity.NUMWEEKS, 2)
                val seconds = weeks * (3600 * 24 * 7)
                getContext().contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        MEDIA_STORE_MEMBER_CURSOR_COLS,
                        MediaStore.Audio.AudioColumns.TITLE + " != ''"
                                + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                                + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                                + " AND " + MediaStore.Audio.AudioColumns.DATE_ADDED + ">"
                                + (System.currentTimeMillis() / 1000 - seconds)
                                + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1", null,
                        MediaStore.Audio.Media.DEFAULT_SORT_ORDER
                )
            }
            else -> context.contentResolver.query(
                    MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                    MEDIA_STORE_PLAYLIST_MEMBER_CURSOR_COLS,
                    null,
                    null,
                    MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER)
        }

    private fun fetchGenre(id: Long): Cursor? =
        context.contentResolver.query(
            MediaStore.Audio.Genres.Members.getContentUri("external", id),
            MEDIA_STORE_MEMBER_CURSOR_COLS,
            MediaStore.Audio.AudioColumns.TITLE + " != ''"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                    + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1", null,
            MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER)

    private fun fetchArtist(id: Long): Cursor? =
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MEDIA_STORE_MEMBER_CURSOR_COLS,
            MediaStore.Audio.AudioColumns.TITLE + " != ''"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                    + " AND " + MediaStore.Audio.AudioColumns.ARTIST_ID + "=" + id
                    + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1", null,
            MediaStore.Audio.Media.DEFAULT_SORT_ORDER)

    private fun fetchAlbum(id: Long): Cursor? =
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MEDIA_STORE_MEMBER_CURSOR_COLS,
            MediaStore.Audio.AudioColumns.TITLE + " != ''"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                    + " AND " + MediaStore.Audio.AudioColumns.ALBUM_ID + "=" + id
                    + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1", null,
            MediaStore.Audio.AudioColumns.TRACK + ", " + MediaStore.Audio.AudioColumns.TITLE_KEY)

    private fun fetchMusic(): Cursor? =
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MEDIA_STORE_MEMBER_CURSOR_COLS,
            MediaStore.Audio.AudioColumns.TITLE + " != ''"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                    + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                    + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1", null,
            MediaStore.Audio.Media.DEFAULT_SORT_ORDER)

    private fun processFolder(cursor: MatrixCursor, counter: IntArray, start: File, root: File) {
        val subFolders = start.listFiles(DIRECTORY_FILTER)
        if (subFolders == null) {
            Log.w(LOGTAG, "Music folder not found: " + start.absolutePath)
            return
        }
        if (subFolders.isEmpty() && start != root) addToCursor(cursor, counter, start, root)
        for (folder in subFolders) {
            processFolder(cursor, counter, folder, root)
        }
    }

    private fun addToCursor(cursor: MatrixCursor, counter: IntArray, folder: File, root: File) {
        counter[0]++
        val path = folder.absolutePath
        cursor.addRow(arrayOf(counter[0], fetchFolderCount(path), path, path.substring(root.absolutePath.length + 1)))
    }

    private fun fetchFolderCount(path: String): Int =
        context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.AudioColumns._ID),
                MediaStore.Audio.AudioColumns.IS_MUSIC + "=1 AND " + MediaStore.Audio.AudioColumns.DATA + " LIKE ?",
                arrayOf(path + "%"),
                null)?.use { it.count } ?: 0

    internal class DirectoryFilter : FileFilter {
        override fun accept(file: File): Boolean {
            return file.isDirectory
        }
    }

    private fun fetchPlaylists(): Cursor? {
        val cursor = context.contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME),
                null,
                null,
                MediaStore.Audio.Playlists.NAME) ?: return null

        val mergeCursor = MergeCursor(arrayOf(buildAutoPlaylistsCursor(), cursor))

        val idColumn = mergeCursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
        val counts = IntArray(mergeCursor.count)
        var i = 0
        while (mergeCursor.moveToNext()) {
            val id = mergeCursor.getLong(idColumn)
            counts[i++] = getCursorCount(fetchPlaylist(id))
        }
        mergeCursor.moveToPosition(-1)

        val finalCursor = CursorWithCountColumn(mergeCursor, counts)
        finalCursor.setNotificationUri(context.contentResolver, MusicContract.Playlist.CONTENT_URI)
        return finalCursor
    }

    private fun buildAutoPlaylistsCursor(): Cursor {
        val cursor = MatrixCursor(arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME), 2)

        cursor.addRow(Arrays.asList<Any>(MusicContract.Playlist.ALL_SONGS, context.getString(R.string.all_songs)))
        cursor.addRow(Arrays.asList<Any>(MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST, context.getString(R.string.recentlyadded)))

        return cursor
    }

    private fun fetchGenres(): Cursor? {
        val cursor = context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                MediaStore.Audio.Genres.NAME + " != ''", null,
                MediaStore.Audio.Genres.DEFAULT_SORT_ORDER) ?: return null

        cursor.setNotificationUri(context.contentResolver, MusicContract.Genre.CONTENT_URI)

        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
        val counts = IntArray(cursor.count)
        var i = 0
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            counts[i++] = if (id >= 0)
                getCursorCount(fetchGenre(id))
            else
                0
        }
        cursor.moveToPosition(-1)

        return CursorWithCountColumn(cursor, counts)
    }

    private fun fetchArtists(): Cursor? =
        context.contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST, MediaStore.Audio.Artists.NUMBER_OF_TRACKS),
            MediaStore.Audio.Artists.ARTIST + " != ''", null,
            MediaStore.Audio.Artists.DEFAULT_SORT_ORDER)

    private fun fetchAlbums(): Cursor? =
        context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.NUMBER_OF_SONGS),
            MediaStore.Audio.Albums.ALBUM + " != ''", null,
            MediaStore.Audio.Albums.DEFAULT_SORT_ORDER)

    private fun getCursorCount(cursor: Cursor?): Int = cursor?.use { it.count } ?: 0

    override fun getType(uri: Uri): String? =
        when (sURIMatcher.match(uri)) {
            FOLDER -> MusicContract.Folder.CONTENT_TYPE

            PLAYLIST -> MusicContract.Playlist.CONTENT_TYPE

            GENRE -> MusicContract.Genre.CONTENT_TYPE

            ARTIST -> MusicContract.Artist.CONTENT_TYPE

            ALBUM -> MusicContract.Album.CONTENT_TYPE

            FOLDER_MEMBERS, PLAYLIST_MEMBERS, GENRE_MEMBERS, ARTIST_MEMBERS, ALBUM_MEMBERS, MUSIC_MEMBERS ->
                MusicContract.CONTENT_TYPE

            else -> null
        }

    override fun insert(uri: Uri, values: ContentValues): Uri? = null

    override fun delete(uri: Uri, selection: String, selectionArgs: Array<String>): Int = 0

    override fun update(uri: Uri, values: ContentValues, selection: String, selectionArgs: Array<String>): Int = 0

    companion object {
        internal const val FOLDER = 1
        internal const val PLAYLIST = 2
        internal const val GENRE = 3
        internal const val ARTIST = 4
        internal const val ALBUM = 5
        internal const val FOLDER_MEMBERS = 6
        internal const val PLAYLIST_MEMBERS = 7
        internal const val GENRE_MEMBERS = 8
        internal const val ARTIST_MEMBERS = 9
        internal const val ALBUM_MEMBERS = 10
        internal const val MUSIC_MEMBERS = 11

        internal val sURIMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Playlist.PLAYLIST_PATH, PLAYLIST)
            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Genre.GENRE_PATH, GENRE)
            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Folder.FOLDER_PATH, FOLDER)
            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Artist.ARTIST_PATH, ARTIST)
            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Album.ALBUM_PATH, ALBUM)

            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Playlist.PLAYLIST_PATH + "/*", PLAYLIST_MEMBERS)
            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Genre.GENRE_PATH + "/#", GENRE_MEMBERS)
            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Folder.FOLDER_PATH + "/*", FOLDER_MEMBERS)
            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Artist.ARTIST_PATH + "/#", ARTIST_MEMBERS)
            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Album.ALBUM_PATH + "/#", ALBUM_MEMBERS)

            sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.MUSIC_PATH, MUSIC_MEMBERS)
        }

        private fun fetchRoot(context: Context): File =
            File(PreferenceManager.getDefaultSharedPreferences(context).getString(SettingsActivity.MUSIC_FOLDER,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath))

        internal val DIRECTORY_FILTER: FileFilter = DirectoryFilter()

        fun calcTitle(context: Context, uri: Uri): String? {
            when (MusicProvider.sURIMatcher.match(uri)) {
                MusicProvider.FOLDER_MEMBERS -> {
                    val root = fetchRoot(context)
                    return uri.lastPathSegment.substring(root.absolutePath.length + 1)
                }

                MusicProvider.PLAYLIST_MEMBERS -> {
                    return when {
                        ContentUris.parseId(uri) == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST ->
                            context.getString(R.string.recentlyadded_title)
                        ContentUris.parseId(uri) == MusicContract.Playlist.ALL_SONGS ->
                            context.getString(R.string.all_songs_title)
                        else -> {
                            context.contentResolver.query(
                                    ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                                    arrayOf(MediaStore.Audio.Playlists.NAME),
                                    null,
                                    null,
                                    null)?.use {
                                if (it.count != 0) {
                                    it.moveToFirst()
                                    return it.getString(0)
                                }
                            }
                            context.getString(R.string.unknown_playlist_name)
                        }
                    }
                }
                MusicProvider.GENRE_MEMBERS -> {
                    var fancyName: String? = null
                    context.contentResolver.query(
                            ContentUris.withAppendedId(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                            arrayOf(MediaStore.Audio.Genres.NAME),
                            null,
                            null,
                            null)?.use {
                        if (it.count != 0) {
                            it.moveToFirst()
                            fancyName = it.getString(0)
                        }
                    }
                    return if (fancyName == null || fancyName == MediaStore.UNKNOWN_STRING) {
                        context.getString(R.string.unknown_genre_name)
                    } else {
                        ID3Utils.decodeGenre(fancyName)
                    }
                }
                MusicProvider.ARTIST_MEMBERS -> {
                    var fancyName: String? = null
                    context.contentResolver.query(
                            ContentUris.withAppendedId(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                            arrayOf(MediaStore.Audio.Artists.ARTIST),
                            null,
                            null,
                            null)?.use {
                        if (it.count != 0) {
                            it.moveToFirst()
                            fancyName = it.getString(0)
                        }
                    }
                    return if (fancyName == null || fancyName == MediaStore.UNKNOWN_STRING) {
                        context.getString(R.string.unknown_artist_name)
                    } else {
                        fancyName
                    }
                }
                MusicProvider.ALBUM_MEMBERS -> {
                    var fancyName: String? = null
                    context.contentResolver.query(
                            ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                            arrayOf(MediaStore.Audio.Albums.ALBUM),
                            null,
                            null,
                            null)?.use {
                        if (it.count != 0) {
                            it.moveToFirst()
                            fancyName = it.getString(0)
                        }
                    }
                    return if (fancyName == null || fancyName == MediaStore.UNKNOWN_STRING) {
                        context.getString(R.string.unknown_album_name)
                    } else {
                        fancyName
                    }
                }

                MusicProvider.MUSIC_MEMBERS -> return context.getString(R.string.tracks_title)

                else -> return context.getString(R.string.tracks_title)
            }
        }
    }

}
