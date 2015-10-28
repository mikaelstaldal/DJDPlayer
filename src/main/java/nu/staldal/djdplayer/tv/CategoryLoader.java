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
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.provider.MusicContract;

import java.util.ArrayList;
import java.util.List;

public class CategoryLoader extends AsyncTaskLoader<List<ListRow>> {

    @SuppressWarnings("unused")
    private static final String TAG = CategoryLoader.class.getSimpleName();

    public CategoryLoader(Context context) {
        super(context);
    }

    @Override
    public List<ListRow> loadInBackground() {
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

        ArrayList<ListRow> data = new ArrayList<>(5);

        data.add(readCursor(R.id.artists_section, getContext().getString(R.string.artists_menu), artistCursor, MusicContract.Artist.NAME, MusicContract.Artist.COUNT));
        data.add(readCursor(R.id.albums_section, getContext().getString(R.string.albums_menu), albumCursor, MusicContract.Album.NAME, MusicContract.Album.COUNT));
        data.add(readCursor(R.id.genres_sections, getContext().getString(R.string.genres_menu), genreCursor, MusicContract.Genre.NAME, MusicContract.Genre._COUNT));
        data.add(readCursor(R.id.folders_section, getContext().getString(R.string.folders_menu), folderCursor, MusicContract.Folder.NAME, MusicContract.Folder._COUNT));
        data.add(readCursor(R.id.playlists_section, getContext().getString(R.string.playlists_menu), playlistCursor, MusicContract.Playlist.NAME, MusicContract.Playlist._COUNT));

        return data;
    }

    private ListRow readCursor(long id, String name, Cursor cursor, String nameColumn, String countColumn) {
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter();

        if (cursor != null) {
            int idColumnIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            int nameColumnIndex = cursor.getColumnIndexOrThrow(nameColumn);
            int countColumnIndex = cursor.getColumnIndexOrThrow(countColumn);
            while (cursor.moveToNext()) {
                listRowAdapter.add(new CategoryItem(
                        cursor.getLong(idColumnIndex),
                        cursor.getString(nameColumnIndex),
                        cursor.getInt(countColumnIndex)));
            }
            cursor.close();
        }

        return new ListRow(new HeaderItem(id, name), listRowAdapter);
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        forceLoad();
    }

}
