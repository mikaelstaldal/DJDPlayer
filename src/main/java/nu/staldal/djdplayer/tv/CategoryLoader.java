/*
 * Copyright (C) 2015 Mikael Ståldal
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
package nu.staldal.djdplayer.tv;

import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.util.Log;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.provider.MusicContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CategoryLoader extends AsyncTaskLoader<Map<String, List<CategoryItem>>> {

    private static final String TAG = CategoryLoader.class.getSimpleName();

    public CategoryLoader(Context context) {
        super(context);
    }

    @Override
    public Map<String, List<CategoryItem>> loadInBackground() {
        Log.i(TAG, "loadInBackground");

        ContentResolver contentResolver = getContext().getContentResolver();

        // TODO fetch in parallel

        Cursor artistCursor = contentResolver.query(MusicContract.Artist.CONTENT_URI, new String[]{
                MusicContract.Artist._ID,
                MusicContract.Artist.NAME,
                MusicContract.Artist.COUNT,
        }, null, null, null);

        Cursor albumCursor = contentResolver.query(MusicContract.Album.CONTENT_URI, new String[]{
                MusicContract.Album._ID,
                MusicContract.Album.NAME,
                MusicContract.Album.COUNT,
        }, null, null, null);

        Cursor genreCursor = contentResolver.query(MusicContract.Genre.CONTENT_URI, new String[]{
                MusicContract.Genre._ID,
                MusicContract.Genre.NAME,
                MusicContract.Genre._COUNT,
        }, null, null, null);

        Cursor folderCursor = contentResolver.query(MusicContract.Folder.CONTENT_URI, new String[]{
                MusicContract.Folder._ID,
                MusicContract.Folder.NAME,
                MusicContract.Folder._COUNT,
                MusicContract.Folder.PATH,
        }, null, null, null);

        Cursor playlistCursor = contentResolver.query(MusicContract.Playlist.CONTENT_URI, new String[]{
                        MusicContract.Playlist._ID,
                        MusicContract.Playlist.NAME,
                        MusicContract.Playlist._COUNT
                },
                MusicContract.Playlist.NAME + " != ''", null,
                MusicContract.Playlist.NAME);

        Map<String, List<CategoryItem>> data = new LinkedHashMap<>(5);

        data.put(getContext().getString(R.string.artists_menu), readCursor(artistCursor, MusicContract.Artist.NAME, MusicContract.Artist.COUNT));
        data.put(getContext().getString(R.string.albums_menu), readCursor(albumCursor, MusicContract.Album.NAME, MusicContract.Album.COUNT));
        data.put(getContext().getString(R.string.genres_menu), readCursor(genreCursor, MusicContract.Genre.NAME, MusicContract.Genre._COUNT));
        data.put(getContext().getString(R.string.folders_menu), readCursor(folderCursor, MusicContract.Folder.NAME, MusicContract.Folder._COUNT));
        data.put(getContext().getString(R.string.playlists_menu), readCursor(playlistCursor, MusicContract.Playlist.NAME, MusicContract.Playlist._COUNT));

        return data;
    }

    private List<CategoryItem> readCursor(Cursor cursor, String nameColumn, String countColumn) {
        List<CategoryItem> list = new ArrayList<>();
        if (cursor != null) {
            int idColumnIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            int nameColumnIndex = cursor.getColumnIndexOrThrow(nameColumn);
            int countColumnIndex = cursor.getColumnIndexOrThrow(countColumn);
            while (cursor.moveToNext()) {
                list.add(new CategoryItem(
                        cursor.getLong(idColumnIndex),
                        cursor.getString(nameColumnIndex),
                        cursor.getInt(countColumnIndex)));
            }
            cursor.close();
        }
        return list;
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        forceLoad();
    }

}
