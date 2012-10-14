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
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Window;
import android.widget.ListView;

public class AlbumBrowserActivity extends CategoryBrowserActivity {
    @Override
    public void onCreate(Bundle icicle)
    {
        if (icicle != null) {
            mCurrentId = icicle.getString(getSelectedCategoryId());
        }
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mToken = MusicUtils.bindToService(this, this);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addDataScheme("file");
        registerReceiver(mScanListener, f);

        setContentView(R.layout.media_picker_activity);
        MusicUtils.updateButtonBar(this, getTabId());
        ListView lv = getListView();
        lv.setOnCreateContextMenuListener(this);
        lv.setTextFilterEnabled(true);

        mAdapter = (CategoryListAdapter)getLastNonConfigurationInstance();
        if (mAdapter == null) {
            //Log.i("@@@", "starting query");
            mAdapter = new AlbumListAdapter(
                    getApplication(),
                    this,
                    R.layout.track_list_item,
                    mCursor,
                    new String[] {},
                    new int[] {});
            setListAdapter(mAdapter);
            setTitle(getWorkingCategoryStringId());
            getCursor(mAdapter.getQueryHandler(), null);
        } else {
            mAdapter.setActivity(this);
            setListAdapter(mAdapter);
            mCursor = mAdapter.getCursor();
            if (mCursor != null) {
                init(mCursor);
            } else {
                getCursor(mAdapter.getQueryHandler(), null);
            }
        }
    }

    @Override
    protected String getCategoryId() {
        return "album";
    }

    @Override
    protected String getSelectedCategoryId() {
        return "selectedalbum";
    }

    @Override
    protected int getTabId() {
        return R.id.albumtab;
    }

    @Override
    protected int getWorkingCategoryStringId() {
        return R.string.working_albums;
    }

    @Override
    protected int getTitleStringId() {
        return R.string.albums_title;
    }

    @Override
    protected String fetchCategoryId(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID));
    }

    @Override
    protected String fetchCategoryName(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM));
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
    protected int getDeleteDescNoSdCardStringId() {
        return R.string.delete_album_desc_nosdcard;
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
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, mCurrentName);
            title = mCurrentName;
        }
        // Since we hide the 'search' menu item when both album is
        // unknown, the query and title strings will have at least one of those.
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE);
        title = getString(R.string.mediasearch, title);
        i.putExtra(SearchManager.QUERY, query);

        startActivity(Intent.createChooser(i, title));
    }

    @Override
    protected Cursor getCursor(AsyncQueryHandler async, String filter) {
        String[] cols = new String[] {
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM
        };

        Cursor ret = null;
        Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        if (!TextUtils.isEmpty(filter)) {
            uri = uri.buildUpon().appendQueryParameter("filter", Uri.encode(filter)).build();
        }
        if (async != null) {
            async.startQuery(0, null,
                    uri,
                    cols, null, null, MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
        } else {
            ret = MusicUtils.query(this, uri,
                    cols, null, null, MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
        }
        return ret;
    }

    @Override
    protected long[] getSongList(Context context, long id) {
        final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
        String where = MediaStore.Audio.Media.ALBUM_ID + "=" + id + " AND " +
                MediaStore.Audio.Media.IS_MUSIC + "=1";
        Cursor cursor = MusicUtils.query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                ccols, where, null, MediaStore.Audio.Media.TRACK);

        if (cursor != null) {
            long [] list = MusicUtils.getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return MusicUtils.sEmptyList;
    }
}
