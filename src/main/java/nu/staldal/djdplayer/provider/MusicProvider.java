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

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.SettingsActivity;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;

public class MusicProvider extends ContentProvider {
    private static final String LOGTAG = "MusicProvider";

    private static final String[] MEDIA_STORE_MEMBER_CURSOR_COLS = new String[] {
        MediaStore.Audio.AudioColumns._ID,
        MediaStore.Audio.AudioColumns.TITLE,
        MediaStore.Audio.AudioColumns.DATA,
        MediaStore.Audio.AudioColumns.ALBUM,
        MediaStore.Audio.AudioColumns.ARTIST,
        MediaStore.Audio.AudioColumns.ARTIST_ID,
        MediaStore.Audio.AudioColumns.DURATION,
        MediaStore.Audio.AudioColumns.MIME_TYPE
    };

    private static final String[] MEDIA_STORE_PLAYLIST_MEMBER_CURSOR_COLS = new String[] {
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
    };

    static final int FOLDER = 1;
    static final int PLAYLIST = 2;
    static final int GENRE = 3;
    static final int ARTIST = 4;
    static final int ALBUM = 5;
    static final int FOLDER_MEMBERS = 6;
    static final int PLAYLIST_MEMBERS = 7;
    static final int GENRE_MEMBERS = 8;
    static final int ARTIST_MEMBERS = 9;
    static final int ALBUM_MEMBERS = 10;
    static final int MUSIC_MEMBERS = 11;

    static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Playlist.PLAYLIST_PATH, PLAYLIST);
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Genre.GENRE_PATH, GENRE);
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Folder.FOLDER_PATH, FOLDER);
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Artist.ARTIST_PATH, ARTIST);
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Album.ALBUM_PATH, ALBUM);

        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Playlist.PLAYLIST_PATH+"/*", PLAYLIST_MEMBERS);
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Genre.GENRE_PATH+"/#", GENRE_MEMBERS);
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Folder.FOLDER_PATH +"/*", FOLDER_MEMBERS);
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Artist.ARTIST_PATH+"/#", ARTIST_MEMBERS);
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Album.ALBUM_PATH+"/#", ALBUM_MEMBERS);

        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.MUSIC_PATH, MUSIC_MEMBERS);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        switch (sURIMatcher.match(uri)) {
            case FOLDER:
                return fetchFolders();

            case PLAYLIST:
                return fetchPlaylists();

            case GENRE:
                return fetchGenres();

            case ARTIST:
                return fetchArtists();

            case ALBUM:
                return fetchAlbums();

            case FOLDER_MEMBERS:
                return fetchFolder(uri.getLastPathSegment());

            case PLAYLIST_MEMBERS:
                return fetchPlaylist(ContentUris.parseId(uri));

            case GENRE_MEMBERS:
                return fetchGenre(ContentUris.parseId(uri));

            case ARTIST_MEMBERS:
                return fetchArtist(ContentUris.parseId(uri));

            case ALBUM_MEMBERS:
                return fetchAlbum(ContentUris.parseId(uri));

            case MUSIC_MEMBERS:
                return fetchMusic();

            default:
                return null;
        }
    }

    private Cursor fetchFolders() {
        File root = fetchRoot(getContext());
        MatrixCursor cursor = new MatrixCursor(new String[] {
                MusicContract.Folder._ID,
                MusicContract.Folder._COUNT,
                MusicContract.Folder.PATH,
                MusicContract.Folder.NAME,
        });
        int[] counter = new int[1];
        processFolder(cursor, counter, root, root);
        return cursor;
    }

    private Cursor fetchFolder(String folder) {
        return getContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MEDIA_STORE_MEMBER_CURSOR_COLS,
                MediaStore.Audio.AudioColumns.TITLE + " != ''"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " LIKE ?"
                        + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1",
                new String[] { folder + "%" },
                MediaStore.Audio.AudioColumns.DATA);
    }

    private Cursor fetchPlaylist(long id) {
        if (id == MusicContract.Playlist.ALL_SONGS) {
            return fetchMusic();
        } else if (id == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
            // do a query for all songs added in the last X weeks
            Context context = getContext();
            int weeks = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).getInt(SettingsActivity.NUMWEEKS, 2);
            int seconds = weeks * (3600 * 24 * 7);
            return getContext().getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    MEDIA_STORE_MEMBER_CURSOR_COLS,
                    MediaStore.Audio.AudioColumns.TITLE + " != ''"
                            + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                            + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                            + " AND " + MediaStore.Audio.AudioColumns.DATE_ADDED + ">" + (System.currentTimeMillis() / 1000 - seconds)
                            + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1",
                    null,
                    MediaStore.Audio.Media.DEFAULT_SORT_ORDER
            );
        } else {
            return getContext().getContentResolver().query(
                    MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                    MEDIA_STORE_PLAYLIST_MEMBER_CURSOR_COLS,
                    null,
                    null,
                    MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
        }
    }

    private Cursor fetchGenre(long id) {
        return getContext().getContentResolver().query(
                MediaStore.Audio.Genres.Members.getContentUri("external", id),
                MEDIA_STORE_MEMBER_CURSOR_COLS,
                MediaStore.Audio.AudioColumns.TITLE + " != ''"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                        + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1",
                null,
                MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER);
    }

    private Cursor fetchArtist(long id) {
        return getContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MEDIA_STORE_MEMBER_CURSOR_COLS,
                MediaStore.Audio.AudioColumns.TITLE + " != ''"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                        + " AND " + MediaStore.Audio.AudioColumns.ARTIST_ID + "=" + id
                        + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1",
                null,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
    }

    private Cursor fetchAlbum(long id) {
        return getContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MEDIA_STORE_MEMBER_CURSOR_COLS,
                MediaStore.Audio.AudioColumns.TITLE + " != ''"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                        + " AND " + MediaStore.Audio.AudioColumns.ALBUM_ID + "=" + id
                        + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1",
                null,
                MediaStore.Audio.AudioColumns.TRACK + ", " + MediaStore.Audio.AudioColumns.TITLE_KEY);
    }

    private Cursor fetchMusic() {
        return getContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MEDIA_STORE_MEMBER_CURSOR_COLS,
                MediaStore.Audio.AudioColumns.TITLE + " != ''"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " IS NOT NULL"
                        + " AND " + MediaStore.Audio.AudioColumns.DATA + " != ''"
                        + " AND " + MediaStore.Audio.AudioColumns.IS_MUSIC + "=1",
                null,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
    }

    private static File fetchRoot(Context context) {
        return new File(PreferenceManager.getDefaultSharedPreferences(context).getString(SettingsActivity.MUSIC_FOLDER,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath()));
    }

    private void processFolder(MatrixCursor cursor, int[] counter, File start, File root) {
        File[] subFolders = start.listFiles(DIRECTORY_FILTER);
        if (subFolders == null) {
            Log.w(LOGTAG, "Music folder not found: " + start.getAbsolutePath());
            return;
        }
        if (subFolders.length == 0 && !start.equals(root)) addToCursor(cursor, counter, start, root);
        for (File folder : subFolders) {
            processFolder(cursor, counter, folder, root);
        }
    }

    private void addToCursor(MatrixCursor cursor, int[] counter, File folder, File root) {
        counter[0]++;
        String path = folder.getAbsolutePath();
        cursor.addRow(new Object[]{counter[0], fetchFolderCount(path), path, path.substring(root.getAbsolutePath().length() + 1)});
    }

    private int fetchFolderCount(String path) {
        Cursor cursor = getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.AudioColumns._ID},
                MediaStore.Audio.AudioColumns.IS_MUSIC + "=1 AND " + MediaStore.Audio.AudioColumns.DATA + " LIKE ?",
                new String[]{path + "%"}, null);

        int count = 0;
        if (cursor != null) {
            count = cursor.getCount();
            cursor.close();
        }
        return count;
    }

    static final FileFilter DIRECTORY_FILTER = new DirectoryFilter();

    static class DirectoryFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isDirectory();
        }
    }


    private Cursor fetchPlaylists() {
        Cursor cursor = getContext().getContentResolver().query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[] {
                        MediaStore.Audio.Playlists._ID,
                        MediaStore.Audio.Playlists.NAME
                },
                null, null,
                MediaStore.Audio.Playlists.NAME);

        if (cursor == null) return null;

        MergeCursor mergeCursor = new MergeCursor(new Cursor[]{buildAutoPlaylistsCursor(), cursor});

        int idColumn = mergeCursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID);
        int[] counts = new int[mergeCursor.getCount()];
        int i = 0;
        while (mergeCursor.moveToNext()) {
            long id = mergeCursor.getLong(idColumn);
            counts[i++] = getCursorCount(fetchPlaylist(id));
        }
        mergeCursor.moveToPosition(-1);

        Cursor finalCursor = new CursorWithCountColumn(mergeCursor, counts);
        finalCursor.setNotificationUri(getContext().getContentResolver(), MusicContract.Playlist.CONTENT_URI);
        return finalCursor;
    }

    private Cursor buildAutoPlaylistsCursor() {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME
        }, 2);

        cursor.addRow(Arrays.<Object>asList(MusicContract.Playlist.ALL_SONGS, getContext().getString(R.string.all_songs)));
        cursor.addRow(Arrays.<Object>asList(MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST, getContext().getString(R.string.recentlyadded)));

        return cursor;
    }

    private Cursor fetchGenres() {
        Cursor cursor = getContext().getContentResolver().query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                new String[] {
                        MediaStore.Audio.Genres._ID,
                        MediaStore.Audio.Genres.NAME
                },
                MediaStore.Audio.Genres.NAME + " != ''",
                null,
                MediaStore.Audio.Genres.DEFAULT_SORT_ORDER);

        if (cursor == null) return null;
        cursor.setNotificationUri(getContext().getContentResolver(), MusicContract.Genre.CONTENT_URI);

        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID);
        int[] counts = new int[cursor.getCount()];
        int i = 0;
        while (cursor.moveToNext()) {
            long id = cursor.getLong(idColumn);
            counts[i++] = (id >= 0)
                    ? getCursorCount(fetchGenre(id))
                    : 0;
        }
        cursor.moveToPosition(-1);

        return new CursorWithCountColumn(cursor, counts);
    }

    private Cursor fetchArtists() {
        return getContext().getContentResolver().query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                new String[] {
                        MediaStore.Audio.Artists._ID,
                        MediaStore.Audio.Artists.ARTIST,
                        MediaStore.Audio.Artists.NUMBER_OF_TRACKS
                },
                MediaStore.Audio.Artists.ARTIST + " != ''",
                null,
                MediaStore.Audio.Artists.DEFAULT_SORT_ORDER);
    }

    private Cursor fetchAlbums() {
        return getContext().getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[] {
                        MediaStore.Audio.Albums._ID,
                        MediaStore.Audio.Albums.ALBUM,
                        MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                },
                MediaStore.Audio.Albums.ALBUM + " != ''",
                null,
                MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
    }


    private int getCursorCount(Cursor cursor) {
        if (cursor == null) return 0;
        int count = cursor.getCount();
        cursor.close();
        return count;
    }


    @Override
    public String getType(Uri uri) {
        switch (sURIMatcher.match(uri)) {
            case FOLDER:
                return MusicContract.Folder.CONTENT_TYPE;

            case PLAYLIST:
                return MusicContract.Playlist.CONTENT_TYPE;

            case GENRE:
                return MusicContract.Genre.CONTENT_TYPE;

            case ARTIST:
                return MusicContract.Artist.CONTENT_TYPE;

            case ALBUM:
                return MusicContract.Album.CONTENT_TYPE;

            case FOLDER_MEMBERS:
            case PLAYLIST_MEMBERS:
            case GENRE_MEMBERS:
            case ARTIST_MEMBERS:
            case ALBUM_MEMBERS:
            case MUSIC_MEMBERS:
                return MusicContract.CONTENT_TYPE;

            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    public static String calcTitle(Context context, Uri uri) {
        switch (MusicProvider.sURIMatcher.match(uri)) {
            case MusicProvider.FOLDER_MEMBERS:
                File root = fetchRoot(context);
                return uri.getLastPathSegment().substring(root.getAbsolutePath().length() + 1);

            case MusicProvider.PLAYLIST_MEMBERS: {
                if (ContentUris.parseId(uri) == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
                    return context.getString(R.string.recentlyadded_title);
                } else if (ContentUris.parseId(uri) == MusicContract.Playlist.ALL_SONGS) {
                    return context.getString(R.string.all_songs_title);
                } else {
                    String[] cols = new String[] {
                            MediaStore.Audio.Playlists.NAME
                    };
                    Cursor cursor = context.getContentResolver().query(
                            ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                            cols, null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.getCount() != 0) {
                                cursor.moveToFirst();
                                return cursor.getString(0);
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                    return context.getString(R.string.unknown_playlist_name);
                }
            }
            case MusicProvider.GENRE_MEMBERS: {
                String fancyName = null;
                String[] cols = new String[] {
                        MediaStore.Audio.Genres.NAME
                };
                Cursor cursor = context.getContentResolver().query(
                        ContentUris.withAppendedId(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                        cols, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.getCount() != 0) {
                            cursor.moveToFirst();
                            fancyName = cursor.getString(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }
                if (fancyName == null || fancyName.equals(MediaStore.UNKNOWN_STRING)) {
                    return context.getString(R.string.unknown_genre_name);
                } else {
                    return ID3Utils.decodeGenre(fancyName);
                }
            }
            case MusicProvider.ARTIST_MEMBERS: {
                String fancyName = null;
                String[] cols = new String[] {
                        MediaStore.Audio.Artists.ARTIST
                };
                Cursor cursor = context.getContentResolver().query(
                        ContentUris.withAppendedId(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                        cols, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.getCount() != 0) {
                            cursor.moveToFirst();
                            fancyName = cursor.getString(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }
                if (fancyName == null || fancyName.equals(MediaStore.UNKNOWN_STRING)) {
                    return context.getString(R.string.unknown_artist_name);
                } else {
                    return fancyName;
                }
            }
            case MusicProvider.ALBUM_MEMBERS: {
                String fancyName = null;
                String[] cols = new String[] {
                        MediaStore.Audio.Albums.ALBUM
                };
                Cursor cursor = context.getContentResolver().query(
                        ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)),
                        cols, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.getCount() != 0) {
                            cursor.moveToFirst();
                            fancyName = cursor.getString(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }
                if (fancyName == null || fancyName.equals(MediaStore.UNKNOWN_STRING)) {
                    return context.getString(R.string.unknown_album_name);
                } else {
                    return fancyName;
                }
            }

            case MusicProvider.MUSIC_MEMBERS:
                return context.getString(R.string.tracks_title);

            default:
                return context.getString(R.string.tracks_title);
        }
    }


}
