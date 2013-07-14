/*
 * Copyright (C) 2012-2013 Mikael Ståldal
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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;

public class MusicProvider extends ContentProvider {
    private static final String LOGTAG = "MusicProvider";

    private static final long RECENTLY_ADDED_PLAYLIST = -1;
    private static final long ALL_SONGS_PLAYLIST = -2;

    private static final int FOLDER = 1;
    private static final int PLAYLIST = 2;
    // private static final int PLAYLIST_ID = 3;
    // private static final int FOLDER_ID = 4;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Folder.FOLDER_PATH, FOLDER);
        sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Playlist.PLAYLIST_PATH, PLAYLIST);
        // sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Playlist.PLAYLIST_PATH+"/#", PLAYLIST_ID);
        // sURIMatcher.addURI(MusicContract.AUTHORITY, MusicContract.Folder.FOLDER_PATH+"/#", FOLDER_ID);
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

            default:
                return null;
        }
    }


    private Cursor fetchFolders() {
        File root = fetchRoot();
        MatrixCursor cursor = new MatrixCursor(new String[]{
                MusicContract.Folder._ID,
                MusicContract.Folder._COUNT,
                MusicContract.Folder.PATH,
                MusicContract.Folder.NAME,
        });
        int[] counter = new int[1];
        processFolder(cursor, counter, root, root);
        return cursor;
    }

    private File fetchRoot() {
        return new File(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(SettingsActivity.MUSIC_FOLDER,
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
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.IS_MUSIC + "=1 AND " + MediaStore.Audio.Media.DATA + " LIKE ?",
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

        cursor.setNotificationUri(getContext().getContentResolver(), MusicContract.Playlist.CONTENT_URI);

        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID);
        int[] counts = new int[cursor.getCount()];
        int i = 0;
        while (cursor.moveToNext()) {
            counts[i++] = fetchPlaylistCount(cursor.getLong(idColumn));
        }
        cursor.moveToPosition(-1);

        return new CursorWithCountColumn(cursor, counts);
    }


    private int fetchPlaylistCount(long id) {
        if (id < 0) return 0;
        Cursor cursor = fetchSongListCursor(id);
        if (cursor == null) return 0;
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    private Cursor fetchSongListCursor(long playlistId) {
        if (playlistId == MusicProvider.RECENTLY_ADDED_PLAYLIST) {
            // do a query for all songs added in the last X weeks
            int numweeks = MusicUtils.getIntPref(getContext(), "numweeks", 2);
            int X = numweeks * (3600 * 24 * 7);
            String where = MediaStore.MediaColumns.DATE_ADDED + ">" + (System.currentTimeMillis() / 1000 - X);
            return getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID}, where, null, null);
        } else if (playlistId == MusicProvider.ALL_SONGS_PLAYLIST) {
            return getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID}, MediaStore.Audio.Media.IS_MUSIC + "=1",
                    null, null);
        } else {
            return getContext().getContentResolver().query(MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                    new String[]{MediaStore.Audio.Playlists.Members.AUDIO_ID}, null, null, null);
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (sURIMatcher.match(uri)) {
            case FOLDER:
                return MusicContract.Folder.CONTENT_TYPE;

            case PLAYLIST:
                return MusicContract.Playlist.CONTENT_TYPE;
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
}
