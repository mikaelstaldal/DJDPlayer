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

import android.content.AsyncQueryHandler;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

public class GenreBrowserActivity extends MetadataCategoryBrowserActivity {
    @Override
    protected String getCategoryId() {
        return "genre";
    }

    @Override
    protected String getSelectedCategoryId() {
        return "selectedgenre";
    }

    @Override
    protected int getTabId() {
        return R.id.genretab;
    }

    @Override
    protected int getWorkingCategoryStringId() {
        return R.string.working_genres;
    }

    @Override
    protected int getTitleStringId() {
        return R.string.genres_title;
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
    protected int getDeleteDescNoSdCardStringId() {
        return R.string.delete_genre_desc_nosdcard;
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
    protected Cursor getCursor(AsyncQueryHandler async, String filter) {
        String[] cols = new String[] {
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME,
        };

        Cursor ret = null;
        Uri uri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
        if (!TextUtils.isEmpty(filter)) {
            uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
        }
        if (async != null) {
            async.startQuery(0, null,
                    uri,
                    cols, null, null, MediaStore.Audio.Genres.DEFAULT_SORT_ORDER);
        } else {
            ret = MusicUtils.query(this, uri,
                    cols, null, null, MediaStore.Audio.Genres.DEFAULT_SORT_ORDER);
        }
        return ret;
    }

    @Override
    protected long fetchCategoryId(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID));
    }

    @Override
    protected String fetchCategoryName(Cursor cursor) {
        return ID3Utils.decodeGenre(cursor.getString(getNameColumnIndex(cursor)));
    }

    @Override
    protected int getNameColumnIndex(Cursor cursor) {
        return cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME);
    }

    @Override
    protected int fetchNumberOfSongsForCategory(Cursor cursor, long id) {
        if (id > -1)
            return fetchSongList(id).length; // TODO [mikes] this is quite slow
        else
            return 0;
    }

    @Override
    protected long[] fetchSongList(long id) {
        final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
        Cursor cursor = MusicUtils.query(this, MediaStore.Audio.Genres.Members.getContentUri("external", id),
                ccols, null, null, MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            long [] list = MusicUtils.getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return MusicUtils.sEmptyList;
    }
}
