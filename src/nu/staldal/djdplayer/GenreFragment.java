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
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;

public class GenreFragment extends MetadataCategoryFragment {

    public static final String CATEGORY_ID = "genre";

    @Override
    protected String getCategoryId() {
        return CATEGORY_ID;
    }

    @Override
    protected String getSelectedCategoryId() {
        return "selectedgenre";
    }

    @Override
    protected int getUnknownStringId() {
        return R.string.unknown_genre_name;
    }

    @Override
    protected int getDeleteDescStringId() {
        return R.string.delete_genre_desc;
    }

    @Override
    protected long fetchCurrentlyPlayingCategoryId() {
        return MusicUtils.getCurrentGenreId();
    }

    @Override
    protected String getEntryContentType() {
        return MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] cols = new String[] {
                MusicContract.Genre._ID,
                MusicContract.Genre.NAME,
                MusicContract.Genre._COUNT,
        };

        return new CursorLoader(getActivity(), MusicContract.Genre.CONTENT_URI, cols, null, null,
                MediaStore.Audio.Genres.DEFAULT_SORT_ORDER);
    }

    @Override
    protected String fetchCategoryName(Cursor cursor) {
        return ID3Utils.decodeGenre(cursor.getString(getNameColumnIndex(cursor)));
    }

    @Override
    protected int getNameColumnIndex(Cursor cursor) {
        return cursor.getColumnIndexOrThrow(MusicContract.Genre.NAME);
    }

    @Override
    protected String getNumberOfSongsColumnName() {
        return MusicContract.Genre._COUNT;
    }

    @Override
    protected long[] fetchSongList(long id) {
        final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
        Cursor cursor = MusicUtils.query(getActivity(),
                MediaStore.Audio.Genres.Members.getContentUri("external", id),
                ccols,
                MediaStore.Audio.Media.DATA + " IS NOT NULL AND " + MediaStore.Audio.Media.DATA + " != ''",
                null,
                MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            long [] list = MusicUtils.getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return MusicUtils.sEmptyList;
    }

    @Override
    protected boolean shuffleSongs() {
        return true;
    }
}
