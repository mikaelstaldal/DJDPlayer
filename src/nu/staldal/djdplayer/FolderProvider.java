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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileFilter;

public class FolderProvider extends ContentProvider {
    private static final int FOLDER = 1;
    // private static final int FOLDER_ID = 2;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sURIMatcher.addURI(FolderContract.AUTHORITY, FolderContract.Folder.FOLDER_PATH, FOLDER);
        // sURIMatcher.addURI(FolderContract.AUTHORITY, FolderContract.Folder.FOLDER_PATH+"/#", FOLDER_ID);
    }

    private File root;

    @Override
    public boolean onCreate() {
        root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        switch (sURIMatcher.match(uri)) {
        case FOLDER:
            MatrixCursor cursor = new MatrixCursor(new String[] {
                    FolderContract.Folder._ID,
                    FolderContract.Folder._COUNT,
                    FolderContract.Folder.PATH,
                    FolderContract.Folder.NAME,
            });
            int[] counter = new int[1];
            processFolder(cursor, counter, root);
            return cursor;
        default:
            return null;
        }
    }

    private void processFolder(MatrixCursor cursor, int[] counter, File start) {
        File[] subFolders = start.listFiles(DIRECTORY_FILTER);
        if (subFolders.length == 0 && !start.equals(root)) addToCursor(cursor, counter, start);
        for (File folder : subFolders) {
            processFolder(cursor, counter, folder);
        }
    }

    private void addToCursor(MatrixCursor cursor, int[] counter, File folder) {
        counter[0]++;
        String path = folder.getAbsolutePath();
        cursor.addRow(new Object[]{ counter[0], fetchCount(path), path, fetchName(path)});
    }

    private int fetchCount(String path) {
        Cursor cursor = getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Media._ID },
                MediaStore.Audio.Media.IS_MUSIC + "=1 AND " + MediaStore.Audio.Media.DATA + " LIKE \'" + path + "%\'",
                null, null);

        int count = 0;
        if (cursor != null) {
            count = cursor.getCount();
            cursor.close();
        }
        return count;
    }

    private String fetchName(String path) {
        return path.substring(root.getAbsolutePath().length() + 1);
    }

    @Override
    public String getType(Uri uri) {
        switch (sURIMatcher.match(uri)) {
        case FOLDER:
            return FolderContract.Folder.CONTENT_TYPE;
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

    static final FileFilter DIRECTORY_FILTER = new DirectoryFilter();

    static class DirectoryFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isDirectory();
        }
    }
}
