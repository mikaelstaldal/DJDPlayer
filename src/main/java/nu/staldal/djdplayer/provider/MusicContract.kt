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

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

object MusicContract {
    val AUTHORITY = "nu.staldal.djdplayer"
    val AUTHORITY_URI = Uri.parse("content://$AUTHORITY")

    val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.audio"

    internal val MUSIC_PATH = "music"

    val CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, MUSIC_PATH)

    object FolderColumns {
        val PATH = "path"
        val NAME = "name"
    }

    object Folder {
        val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.folder"

        internal val FOLDER_PATH = "folder"

        val CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, FOLDER_PATH)

        fun getMembersUri(folder: String) = CONTENT_URI.buildUpon().appendPath(folder).build()
    }

    object PlaylistColumns {
        val NAME = MediaStore.Audio.Playlists.NAME
    }

    object Playlist {
        val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.playlist"

        internal val PLAYLIST_PATH = "playlist"

        val CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, PLAYLIST_PATH)

        fun getMembersUri(id: Long) = ContentUris.withAppendedId(CONTENT_URI, id)

        val ALL_SONGS: Long = -1
        val RECENTLY_ADDED_PLAYLIST: Long = -2
    }

    object GenreColumns {
        val NAME = MediaStore.Audio.Genres.NAME
    }

    object Genre {
        val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.genre"

        internal val GENRE_PATH = "genre"

        val CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, GENRE_PATH)

        fun getMembersUri(id: Long) = ContentUris.withAppendedId(CONTENT_URI, id)
    }

    object ArtistColumns {
        val NAME = MediaStore.Audio.Artists.ARTIST
        val COUNT = MediaStore.Audio.Artists.NUMBER_OF_TRACKS
    }

    object Artist {
        val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.artist"

        internal val ARTIST_PATH = "artist"

        val CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, ARTIST_PATH)

        fun getMembersUri(id: Long) = ContentUris.withAppendedId(CONTENT_URI, id)
    }

    object AlbumColumns {
        val NAME = MediaStore.Audio.Albums.ALBUM
        val COUNT = MediaStore.Audio.Albums.NUMBER_OF_SONGS
    }

    object Album {
        val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.album"

        internal val ALBUM_PATH = "album"

        val CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, ALBUM_PATH)

        fun getMembersUri(id: Long) = ContentUris.withAppendedId(CONTENT_URI, id)
    }

}
