/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.SearchManager;
import android.content.*;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ListView;

public abstract class CategoryBrowserActivity extends BrowserActivity {
    protected final static int SEARCH = CHILD_MENU_BASE;

    protected static int mLastListPosCourse = -1;
    protected static int mLastListPosFine = -1;

    protected long mCurrentId;
    protected String mCurrentName;
    protected boolean mIsUnknown;
    protected CategoryListAdapter mAdapter;
    protected boolean mAdapterSent;
    protected Cursor mCursor;

    BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getListView().invalidateViews();
        }
    };

    BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MusicUtils.setSpinnerState(CategoryBrowserActivity.this);
            mReScanHandler.sendEmptyMessage(0);
        }
    };

    Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getCursor(mAdapter.getQueryHandler(), null);
            }
        }
    };

    protected abstract String getCategoryId();

    protected abstract String getSelectedCategoryId();

    protected abstract Cursor getCursor(AsyncQueryHandler async, String filter);

    protected abstract int getNameColumnIndex(Cursor cursor);

    protected abstract long[] fetchSongList(long id);

    protected abstract long fetchCategoryId(Cursor cursor);

    protected abstract String fetchCategoryName(Cursor cursor);

    protected abstract int fetchNumberOfSongsForCategory(Cursor cursor, long id);

    protected abstract int getTabId();

    protected abstract int getWorkingCategoryStringId();

    protected abstract int getTitleStringId();

    protected abstract int getUnknownStringId();

    protected abstract int getDeleteDescStringId();

    protected abstract int getDeleteDescNoSdCardStringId();

    protected abstract long fetchCurrentlyPlayingCategoryId();

    protected abstract String getEntryContentType();

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
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

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
            mAdapter = new CategoryListAdapter(
                    getApplication(),
                    R.layout.track_list_item,
                    mCursor,
                    new String[] {},
                    new int[] {},
                    this);
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

    public void init(Cursor c) {
        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(c); // also sets mCursor

        if (mCursor == null) {
            MusicUtils.displayDatabaseError(this);
            closeContextMenu();
            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }

        // restore previous position
        if (mLastListPosCourse >= 0) {
            getListView().setSelectionFromTop(mLastListPosCourse, mLastListPosFine);
            mLastListPosCourse = -1;
        }

        MusicUtils.hideDatabaseError(this);
        MusicUtils.updateButtonBar(this, getTabId());
        setTitle();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        mAdapterSent = true;
        return mAdapter;
    }

    @Override
    public void onDestroy() {
        ListView lv = getListView();
        if (lv != null) {
            mLastListPosCourse = lv.getFirstVisiblePosition();
            View cv = lv.getChildAt(0);
            if (cv != null) {
                mLastListPosFine = cv.getTop();
            }
        }
        MusicUtils.unbindFromService(mToken);
        // If we have an adapter and didn't send it off to another activity yet, we should
        // close its cursor, which we do by assigning a null cursor to it. Doing this
        // instead of closing the cursor directly keeps the framework from accessing
        // the closed cursor later.
        if (!mAdapterSent && mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        // Because we pass the adapter to the next activity, we need to make
        // sure it doesn't keep a reference to this activity. We can do this
        // by clearing its DatasetObservers, which setListAdapter(null) does.
        setListAdapter(null);
        mAdapter = null;
        unregisterReceiver(mScanListener);
        super.onDestroy();
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

        MusicUtils.setSpinnerState(this);
    }

    @Override
    public void onPause() {
        unregisterReceiver(mTrackListListener);
        mReScanHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SCAN_DONE:
                if (resultCode == RESULT_CANCELED) {
                    finish();
                } else {
                    getCursor(mAdapter.getQueryHandler(), null);
                }
                break;

            case NEW_PLAYLIST:
                if (resultCode == RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long [] list = fetchSongList(mCurrentId);
                        MusicUtils.addToPlaylist(this, list, Long.parseLong(uri.getLastPathSegment()));
                    }
                }
                break;
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/djd.track");
        intent.putExtra(getCategoryId(), String.valueOf(id));
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, PARTY_SHUFFLE, 0, R.string.party_shuffle); // icon will be set in onPrepareOptionsMenu()
        menu.add(0, SHUFFLE_ALL, 0, R.string.shuffle_all).setIcon(R.drawable.ic_menu_shuffle);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MusicUtils.setPartyShuffleMenuIcon(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Cursor cursor;
        switch (item.getItemId()) {
            case PARTY_SHUFFLE:
                MusicUtils.togglePartyShuffle();
                break;

            case SHUFFLE_ALL:
                cursor = MusicUtils.query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String [] { MediaStore.Audio.Media._ID},
                        MediaStore.Audio.Media.IS_MUSIC + "=1", null,
                        MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
                if (cursor != null) {
                    MusicUtils.shuffleAll(this, cursor);
                    cursor.close();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        menu.add(0, PLAY_SELECTION, 0, R.string.play_all);
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
            menu.add(0, SEARCH, 0, R.string.search_title);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                long [] list = fetchSongList(mCurrentId);
                MusicUtils.playAll(this, list, 0);
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(this, CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long [] list = fetchSongList(mCurrentId);
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(this, list, playlist);
                return true;
            }
            case DELETE_ITEM: {
                long [] list = fetchSongList(mCurrentId);
                String f;
                if (android.os.Environment.isExternalStorageRemovable()) {
                    f = getString(getDeleteDescStringId());
                } else {
                    f = getString(getDeleteDescNoSdCardStringId());
                }
                String desc = String.format(f, mCurrentName);
                Bundle b = new Bundle();
                b.putString("description", desc);
                b.putLongArray("items", list);
                Intent intent = new Intent();
                intent.setClass(this, DeleteItems.class);
                intent.putExtras(b);
                startActivityForResult(intent, -1);
                return true;
            }
            case SEARCH:
                doSearch();
                return true;

        }
        return super.onContextItemSelected(item);
    }
}
