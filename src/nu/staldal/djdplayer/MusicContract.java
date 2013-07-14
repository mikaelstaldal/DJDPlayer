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

import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;

public final class MusicContract {
    public static final String AUTHORITY = "nu.staldal.djdplayer";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    protected interface FolderColumns {
        public static final String PATH = "path";
        public static final String NAME = "name";
    }

    public static class Folder implements BaseColumns, FolderColumns {
        private Folder() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.folder";
        // public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.djdplayer.folder";

        public static final String FOLDER_PATH = "folder";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, FOLDER_PATH);

        /*
        public static Uri getFolderUri(long folderId) {
            return ContentUris.withAppendedId(CONTENT_URI, folderId);
        }
        */
    }

    protected interface PlaylistColumns {
        public static final String NAME = MediaStore.Audio.Playlists.NAME;
    }

    public static class Playlist implements BaseColumns, PlaylistColumns {
        private Playlist() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.playlist";
        // public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.djdplayer.playlist";

        public static final String PLAYLIST_PATH = "playlist";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, PLAYLIST_PATH);

        /*
        public static Uri getPlaylistUri(long id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
        */
    }

    protected interface GenreColumns {
        public static final String NAME = MediaStore.Audio.Genres.NAME;
    }

    public static class Genre implements BaseColumns, GenreColumns {
        private Genre() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.djdplayer.genre";
        // public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.djdplayer.genre";

        public static final String GENRE_PATH = "genre";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, GENRE_PATH);

        /*
        public static Uri getGenreUri(long id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
        */
    }

}
