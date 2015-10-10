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
package nu.staldal.djdplayer.provider;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;

public final class MusicContract {
    public static final String AUTHORITY = "nu.staldal.djdplayer";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.audio";

    static final String MUSIC_PATH = "music";

    public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, MUSIC_PATH);

    protected interface FolderColumns {
        String PATH = "path";
        String NAME = "name";
    }

    public static class Folder implements BaseColumns, FolderColumns {
        private Folder() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.folder";

        static final String FOLDER_PATH = "folder";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, FOLDER_PATH);

        public static Uri getMembersUri(String folder) {
            return CONTENT_URI.buildUpon().appendPath(folder).build();
        }
    }

    protected interface PlaylistColumns {
        String NAME = MediaStore.Audio.Playlists.NAME;
    }

    public static class Playlist implements BaseColumns, PlaylistColumns {
        private Playlist() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.playlist";

        static final String PLAYLIST_PATH = "playlist";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, PLAYLIST_PATH);

        public static Uri getMembersUri(long id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }

        public static final long ALL_SONGS = -1;
        public static final long RECENTLY_ADDED_PLAYLIST = -2;
    }

    protected interface GenreColumns {
        String NAME = MediaStore.Audio.Genres.NAME;
    }

    public static class Genre implements BaseColumns, GenreColumns {
        private Genre() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.genre";

        static final String GENRE_PATH = "genre";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, GENRE_PATH);

        public static Uri getMembersUri(long id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
    }

    protected interface ArtistColumns {
        String NAME = MediaStore.Audio.Artists.ARTIST;
        String COUNT = MediaStore.Audio.Artists.NUMBER_OF_TRACKS;
    }

    public static class Artist implements BaseColumns, ArtistColumns {
        private Artist() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.artist";

        static final String ARTIST_PATH = "artist";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, ARTIST_PATH);

        public static Uri getMembersUri(long id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
    }

    protected interface AlbumColumns {
        String NAME = MediaStore.Audio.Albums.ALBUM;
        String COUNT = MediaStore.Audio.Albums.NUMBER_OF_SONGS;
    }

    public static class Album implements BaseColumns, AlbumColumns {
        private Album() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.album";

        static final String ALBUM_PATH = "album";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, ALBUM_PATH);

        public static Uri getMembersUri(long id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
    }

}
