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
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

public class ArtistBrowserActivity extends MetadataCategoryBrowserActivity {
    @Override
    protected String getCategoryId() {
        return "artist";
    }

    @Override
    protected String getSelectedCategoryId() {
        return "selectedartist";
    }

    @Override
    protected int getTabId() {
        return R.id.artisttab;
    }

    @Override
    protected int getWorkingCategoryStringId() {
        return R.string.working_artists;
    }

    @Override
    protected int getTitleStringId() {
        return R.string.artists_title;
    }

    @Override
    protected int getUnknownStringId() {
        return R.string.unknown_artist_name;
    }

    @Override
    protected int getDeleteDescStringId() {
        return R.string.delete_artist_desc;
    }

    @Override
    protected int getDeleteDescNoSdCardStringId() {
        return R.string.delete_artist_desc_nosdcard;
    }

    @Override
    protected long fetchCurrentlyPlayingCategoryId() {
        return MusicUtils.getCurrentArtistId();
    }

    @Override
    protected String getEntryContentType() {
        return MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE;
    }

    @Override
    protected void addExtraSearchData(Intent i) {
        i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, mCurrentName);
    }

    @Override
    protected Cursor getCursor(AsyncQueryHandler async, String filter) {
        String[] cols = new String[] {
                MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
        };

        Cursor ret = null;
        Uri uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
        if (!TextUtils.isEmpty(filter)) {
            uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
        }
        if (async != null) {
            async.startQuery(0, null,
                    uri,
                    cols, null, null, MediaStore.Audio.Artists.DEFAULT_SORT_ORDER);
        } else {
            ret = MusicUtils.query(this, uri,
                    cols, null, null, MediaStore.Audio.Artists.DEFAULT_SORT_ORDER);
        }
        return ret;
    }

    @Override
    protected long fetchCategoryId(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID));
    }

    @Override
    protected String fetchCategoryName(Cursor cursor) {
        return cursor.getString(getNameColumnIndex(cursor));
    }

    @Override
    protected int getNameColumnIndex(Cursor cursor) {
        return cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST);
    }

    @Override
    protected int fetchNumberOfSongsForCategory(Cursor cursor, long id) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS));
    }

    @Override
    protected long[] fetchSongList(long id) {
        final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
        String where = MediaStore.Audio.Media.ARTIST_ID + "=" + id + " AND " +
                MediaStore.Audio.Media.IS_MUSIC + "=1";
        Cursor cursor = MusicUtils.query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                ccols, where, null, MediaStore.Audio.Media.TRACK);

        if (cursor != null) {
            long [] list = MusicUtils.getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return MusicUtils.sEmptyList;
    }
}
