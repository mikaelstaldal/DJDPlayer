/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public abstract class MetadataCategoryBrowserActivity extends CategoryBrowserActivity<MetadataCategoryListAdapter> {
    protected final static int SEARCH_FOR = CHILD_MENU_BASE;

    protected long mCurrentId;
    protected String mCurrentName;
    protected boolean mIsUnknown;

    final BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getListView().invalidateViews();
        }
    };

    protected abstract String getCategoryId();

    protected abstract Cursor getCursor(AsyncQueryHandler async, String filter);

    protected abstract String getSelectedCategoryId();

    protected abstract int getNameColumnIndex(Cursor cursor);

    protected abstract long[] fetchSongList(long id);

    protected abstract long fetchCategoryId(Cursor cursor);

    protected abstract String fetchCategoryName(Cursor cursor);

    protected abstract int fetchNumberOfSongsForCategory(Cursor cursor, long id);

    protected abstract int getWorkingCategoryStringId();

    protected abstract int getTitleStringId();

    protected abstract int getUnknownStringId();

    protected abstract int getDeleteDescStringId();

    protected abstract long fetchCurrentlyPlayingCategoryId();

    protected abstract String getEntryContentType();

    protected abstract boolean shuffleSongs();

    /**
     * Do nothing by default, can be overridden by subclasses.
     * @param i  intent
     */
    protected void addExtraSearchData(Intent i) { }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mCurrentId = icicle.getLong(getSelectedCategoryId());
        }

        mAdapter = (MetadataCategoryListAdapter)getLastNonConfigurationInstance();
        if (mAdapter == null) {
            //Log.i("@@@", "starting query");
            mAdapter = new MetadataCategoryListAdapter(
                    getApplication(),
                    R.layout.track_list_item,
                    mCursor,
                    new String[] {},
                    new int[] {},
                    this);
            setListAdapter(mAdapter);
            if (!withTabs) setTitle(getWorkingCategoryStringId());
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
        mToken = MusicUtils.bindToService(this, this);
    }

    protected void doSearch() {
        Intent i = new Intent();
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        String query = "";
        CharSequence title = "";
        if (!mIsUnknown) {
            query = mCurrentName;
            addExtraSearchData(i);
            title = mCurrentName;
        }
        // Since we hide the 'search' menu item when category is
        // unknown, the query and title strings will have at least one of those.
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, getEntryContentType());
        title = getString(R.string.mediasearch, title);
        i.putExtra(SearchManager.QUERY, query);

        startActivity(Intent.createChooser(i, title));
    }

    @Override
    protected void setTitle() {
        CharSequence fancyName = "";
        if (mCursor != null && mCursor.getCount() > 0) {
            mCursor.moveToFirst();
            fancyName = fetchCategoryName(mCursor);
            if (fancyName == null || fancyName.equals(MediaStore.UNKNOWN_STRING))
                fancyName = getText(R.string.unknown_artist_name);
        }

        if (fancyName != null)
            setTitle(fancyName);
        else
            setTitle(getTitleStringId());
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putLong(getSelectedCategoryId(), mCurrentId);
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        registerReceiver(mTrackListListener, f);
        mTrackListListener.onReceive(null, null);
    }

    @Override
    public void onPause() {
        unregisterReceiver(mTrackListListener);
        super.onPause();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/vnd.djdplayer.audio");
        intent.putExtra(getCategoryId(), String.valueOf(id));
        startActivity(intent);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;

        menu.add(0, PLAY_ALL, 0, R.string.play_all);
        menu.add(0, QUEUE_ALL, 0, R.string.queue_all);
        SubMenu interleave = menu.addSubMenu(0, INTERLEAVE_ALL, 0, R.string.interleave_all);
        for (int i = 1; i<=5; i++) {
            for (int j = 1; j<=5; j++) {
                interleave.add(2, INTERLEAVE_ALL+10*i+j, 0, getResources().getString(R.string.interleaveNNN, i, j));
            }
        }

        SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_all_to_playlist);
        MusicUtils.makePlaylistMenu(this, sub);

        menu.add(0, DELETE_ITEM, 0, R.string.delete_all);

        AdapterView.AdapterContextMenuInfo mi = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        mCursor.moveToPosition(mi.position);
        mCurrentId = fetchCategoryId(mCursor);
        mCurrentName = fetchCategoryName(mCursor);
        mIsUnknown = mCurrentName == null ||
                mCurrentName.equals(MediaStore.UNKNOWN_STRING);
        if (mIsUnknown) {
            menu.setHeaderTitle(getString(getUnknownStringId()));
        } else {
            menu.setHeaderTitle(mCurrentName);
            menu.add(0, SEARCH_FOR, 0, R.string.search_for);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_ALL: {
                long[] songs = fetchSongList(mCurrentId);
                if (shuffleSongs()) MusicUtils.shuffleArray(songs);
                MusicUtils.playAll(this, songs);
                return true;
            }

            case QUEUE_ALL: {
                long[] songs = fetchSongList(mCurrentId);
                if (shuffleSongs()) MusicUtils.shuffleArray(songs);
                MusicUtils.queue(this, songs);
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(this, CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                long[] songs = fetchSongList(mCurrentId);
                if (shuffleSongs()) MusicUtils.shuffleArray(songs);
                MusicUtils.addToPlaylist(this, songs, playlist);
                return true;
            }

            case DELETE_ITEM: {
                final long[] songs = fetchSongList(mCurrentId);
                String f = getString(getDeleteDescStringId());
                String desc = String.format(f, mCurrentName);
                new AlertDialog.Builder(this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_songs_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setPositiveButton(R.string.delete_confirm_button_text, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MusicUtils.deleteTracks(MetadataCategoryBrowserActivity.this, songs);
                            }
                        }).show();
                return true;
            }

            case SEARCH_FOR:
                doSearch();
                return true;

            default:
                if (item.getItemId() > INTERLEAVE_ALL) {
                    int currentCount = (item.getItemId() - INTERLEAVE_ALL) / 10;
                    int newCount = (item.getItemId() - INTERLEAVE_ALL) % 10;
                    long[] songs = fetchSongList(mCurrentId);
                    if (shuffleSongs()) MusicUtils.shuffleArray(songs);
                    MusicUtils.interleave(this, songs, currentCount, newCount);
                    return true;
                }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case NEW_PLAYLIST:
                if (resultCode == RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long [] songs = fetchSongList(mCurrentId);
                        if (shuffleSongs()) MusicUtils.shuffleArray(songs);
                        MusicUtils.addToPlaylist(this, songs, Long.parseLong(uri.getLastPathSegment()));
                    }
                }
                break;
        }
    }

    @Override
    protected void reloadData() {
        if (mAdapter != null) {
            getCursor(mAdapter.getQueryHandler(), null);
        }
    }
}
