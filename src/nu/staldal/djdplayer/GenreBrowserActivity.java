/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

public class GenreBrowserActivity extends CategoryBrowserActivity {
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
    protected long fetchCategoryId(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID));
    }

    @Override
    protected String fetchCategoryName(Cursor cursor) {
        return ID3Utils.decodeGenre(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Genres.NAME)));
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
    protected void doSearch() {
        Intent i = new Intent();
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        String query = "";
        CharSequence title = "";
        if (!mIsUnknown) {
            query = mCurrentName;
            title = mCurrentName;
        }
        // Since we hide the 'search' menu item when both genre is
        // unknown, the query and title strings will have at least one of those.
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE);
        title = getString(R.string.mediasearch, title);
        i.putExtra(SearchManager.QUERY, query);

        startActivity(Intent.createChooser(i, title));
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
    protected int getIdColumnIndex(Cursor cursor) {
        return cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID);
    }

    @Override
    protected int getNameColumnIndex(Cursor cursor) {
        return cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME);
    }

    @Override
    protected long[] getSongList(Context context, long id) {
        final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
        Cursor cursor = MusicUtils.query(context, MediaStore.Audio.Genres.Members.getContentUri("external", id),
                ccols, null, null, MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            long [] list = MusicUtils.getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return MusicUtils.sEmptyList;
    }
}
