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

import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

public class AlbumFragment extends MetadataCategoryFragment {

    public static final String CATEGORY_ID = "album";

    @Override
    protected String getCategoryId() {
        return CATEGORY_ID;
    }

    @Override
    protected String getSelectedCategoryId() {
        return "selectedalbum";
    }

    @Override
    protected int getUnknownStringId() {
        return R.string.unknown_album_name;
    }

    @Override
    protected int getDeleteDescStringId() {
        return R.string.delete_album_desc;
    }

    @Override
    protected long fetchCurrentlyPlayingCategoryId() {
        return MusicUtils.getCurrentAlbumId();
    }

    @Override
    protected String getEntryContentType() {
        return MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE;
    }

    @Override
    protected void addExtraSearchData(Intent i) {
        i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, mCurrentName);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] cols = new String[] {
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS,
        };

        Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        return new CursorLoader(getActivity(), uri, cols, null, null, MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
    }

    @Override
    protected String fetchCategoryName(Cursor cursor) {
        return cursor.getString(getNameColumnIndex(cursor));
    }

    @Override
    protected int getNameColumnIndex(Cursor cursor) {
        return cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM);
    }

    @Override
    protected String getNumberOfSongsColumnName() {
        return MediaStore.Audio.Albums.NUMBER_OF_SONGS;
    }

    @Override
    protected long[] fetchSongList(long id) {
        final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
        String where = MediaStore.Audio.Media.ALBUM_ID + "=" + id + " AND " +
                MediaStore.Audio.Media.IS_MUSIC + "=1 AND " +
                MediaStore.Audio.Media.DATA + " IS NOT NULL AND " + MediaStore.Audio.Media.DATA + " != ''";
        Cursor cursor = MusicUtils.query(getActivity(), MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                ccols, where, null, MediaStore.Audio.Media.TRACK + ", " + MediaStore.Audio.Media.TITLE_KEY);

        if (cursor != null) {
            long [] list = MusicUtils.getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return MusicUtils.sEmptyList;
    }

    @Override
    protected boolean shuffleSongs() {
        return false;
    }
}
