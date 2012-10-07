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

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.*;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;
import nu.staldal.djdplayer.MusicUtils.ServiceToken;

public class GenreBrowserActivity extends ListActivity
    implements View.OnCreateContextMenuListener, MusicUtils.Defs, ServiceConnection
{
    private String mCurrentGenreId;
    private String mCurrentGenreName;
    boolean mIsUnknownGenre;
    private GenreListAdapter mAdapter;
    private boolean mAdapterSent;
    private final static int SEARCH = CHILD_MENU_BASE;
    private static int mLastListPosCourse = -1;
    private static int mLastListPosFine = -1;
    private ServiceToken mToken;

    public GenreBrowserActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        if (icicle != null) {
            mCurrentGenreId = icicle.getString("selectedgenre");
            mArtistId = icicle.getString("artist");
        } else {
            mArtistId = getIntent().getStringExtra("artist");
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
        MusicUtils.updateButtonBar(this, R.id.genretab);
        ListView lv = getListView();
        lv.setOnCreateContextMenuListener(this);
        lv.setTextFilterEnabled(true);

        mAdapter = (GenreListAdapter) getLastNonConfigurationInstance();
        if (mAdapter == null) {
            //Log.i("@@@", "starting query");
            mAdapter = new GenreListAdapter(
                    getApplication(),
                    this,
                    R.layout.track_list_item,
                    mGenreCursor,
                    new String[] {},
                    new int[] {});
            setListAdapter(mAdapter);
            setTitle(R.string.working_genres);
            getGenreCursor(mAdapter.getQueryHandler(), null);
        } else {
            mAdapter.setActivity(this);
            setListAdapter(mAdapter);
            mGenreCursor = mAdapter.getCursor();
            if (mGenreCursor != null) {
                init(mGenreCursor);
            } else {
                getGenreCursor(mAdapter.getQueryHandler(), null);
            }
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        mAdapterSent = true;
        return mAdapter;
    }
    
    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putString("selectedgenre", mCurrentGenreId);
        outcicle.putString("artist", mArtistId);
        super.onSaveInstanceState(outcicle);
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
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        registerReceiver(mTrackListListener, f);
        mTrackListListener.onReceive(null, null);

        MusicUtils.setSpinnerState(this);
    }

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getListView().invalidateViews();
            MusicUtils.updateNowPlaying(GenreBrowserActivity.this);
        }
    };
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MusicUtils.setSpinnerState(GenreBrowserActivity.this);
            mReScanHandler.sendEmptyMessage(0);
        }
    };
    
    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getGenreCursor(mAdapter.getQueryHandler(), null);
            }
        }
    };

    @Override
    public void onPause() {
        unregisterReceiver(mTrackListListener);
        mReScanHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    public void init(Cursor c) {

        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(c); // also sets mGenreCursor

        if (mGenreCursor == null) {
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
        MusicUtils.updateButtonBar(this, R.id.genretab);
        setTitle();
    }

    private void setTitle() {
        CharSequence fancyName = "";
        if (mGenreCursor != null && mGenreCursor.getCount() > 0) {
            mGenreCursor.moveToFirst();
            fancyName = mGenreCursor.getString(
                    mGenreCursor.getColumnIndex(MediaStore.Audio.Genres.NAME));
            if (fancyName == null || fancyName.equals(MediaStore.UNKNOWN_STRING))
                fancyName = getText(R.string.unknown_artist_name);
        }

        if (mArtistId != null && fancyName != null)
            setTitle(fancyName);
        else
            setTitle(R.string.genres_title);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        menu.add(0, PLAY_SELECTION, 0, R.string.play_selection);
        SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(this, sub);
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);

        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;
        mGenreCursor.moveToPosition(mi.position);
        mCurrentGenreId = mGenreCursor.getString(mGenreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID));
        mCurrentGenreName = mGenreCursor.getString(mGenreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME));
        mIsUnknownGenre = mCurrentGenreName == null ||
                mCurrentGenreName.equals(MediaStore.UNKNOWN_STRING);
        if (mIsUnknownGenre) {
            menu.setHeaderTitle(getString(R.string.unknown_genre_name));
        } else {
            menu.setHeaderTitle(mCurrentGenreName);
            menu.add(0, SEARCH, 0, R.string.search_title);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                // play the selected genre
                long [] list = MusicUtils.getSongListForGenre(this, Long.parseLong(mCurrentGenreId));
                MusicUtils.playAll(this, list, 0);
                return true;
            }

            case QUEUE: {
                long [] list = MusicUtils.getSongListForGenre(this, Long.parseLong(mCurrentGenreId));
                MusicUtils.addToCurrentPlaylist(this, list);
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(this, CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long [] list = MusicUtils.getSongListForGenre(this, Long.parseLong(mCurrentGenreId));
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(this, list, playlist);
                return true;
            }
            case DELETE_ITEM: {
                long [] list = MusicUtils.getSongListForGenre(this, Long.parseLong(mCurrentGenreId));
                String f;
                if (android.os.Environment.isExternalStorageRemovable()) {
                    f = getString(R.string.delete_genre_desc);
                } else {
                    f = getString(R.string.delete_genre_desc_nosdcard);
                }
                String desc = String.format(f, mCurrentGenreName);
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

    void doSearch() {
        CharSequence title = null;
        String query = "";
        
        Intent i = new Intent();
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        title = "";
        if (!mIsUnknownGenre) {
            query = mCurrentGenreName;
            title = mCurrentGenreName;
        }
        // Since we hide the 'search' menu item when both genre and artist are
        // unknown, the query and title strings will have at least one of those.
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE);
        title = getString(R.string.mediasearch, title);
        i.putExtra(SearchManager.QUERY, query);

        startActivity(Intent.createChooser(i, title));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SCAN_DONE:
                if (resultCode == RESULT_CANCELED) {
                    finish();
                } else {
                    getGenreCursor(mAdapter.getQueryHandler(), null);
                }
                break;

            case NEW_PLAYLIST:
                if (resultCode == RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long [] list = MusicUtils.getSongListForGenre(this, Long.parseLong(mCurrentGenreId));
                        MusicUtils.addToPlaylist(this, list, Long.parseLong(uri.getLastPathSegment()));
                    }
                }
                break;
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/djd.track");
        intent.putExtra("genre", Long.valueOf(id).toString());
        intent.putExtra("artist", mArtistId);
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
        Intent intent;
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

    private Cursor getGenreCursor(AsyncQueryHandler async, String filter) {
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
    
    static class GenreListAdapter extends SimpleCursorAdapter implements SectionIndexer {
        
        private final Drawable mNowPlayingOverlay;
        private final BitmapDrawable mDefaultGenreIcon;
        private int mGenreIdx;
        private final Resources mResources;
        private final String mUnknownGenre;
        private AlphabetIndexer mIndexer;
        private GenreBrowserActivity mActivity;
        private AsyncQueryHandler mQueryHandler;
        private String mConstraint = null;
        private boolean mConstraintIsValid = false;
        
        static class ViewHolder {
            TextView line1;
            TextView line2;
            ImageView play_indicator;
            ImageView icon;
        }

        class QueryHandler extends AsyncQueryHandler {
            QueryHandler(ContentResolver res) {
                super(res);
            }
            
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                //Log.i("@@@", "query complete");
                mActivity.init(cursor);
            }
        }

        GenreListAdapter(Context context, GenreBrowserActivity currentactivity,
                int layout, Cursor cursor, String[] from, int[] to) {
            super(context, layout, cursor, from, to);

            mActivity = currentactivity;
            mQueryHandler = new QueryHandler(context.getContentResolver());
            
            mUnknownGenre = context.getString(R.string.unknown_genre_name);

            Resources r = context.getResources();
            mNowPlayingOverlay = r.getDrawable(R.drawable.indicator_ic_mp_playing_list);

            Bitmap b = BitmapFactory.decodeResource(r, R.drawable.albumart_mp_unknown_list);
            mDefaultGenreIcon = new BitmapDrawable(context.getResources(), b);
            // no filter or dither, it's a lot faster and we can't tell the difference
            mDefaultGenreIcon.setFilterBitmap(false);
            mDefaultGenreIcon.setDither(false);
            getColumnIndices(cursor);
            mResources = context.getResources();
        }

        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
                mGenreIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME);

                if (mIndexer != null) {
                    mIndexer.setCursor(cursor);
                } else {
                    mIndexer = new MusicAlphabetIndexer(cursor, mGenreIdx, mResources.getString(
                            R.string.fast_scroll_alphabet));
                }
            }
        }
        
        public void setActivity(GenreBrowserActivity newactivity) {
            mActivity = newactivity;
        }
        
        public AsyncQueryHandler getQueryHandler() {
            return mQueryHandler;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
           View v = super.newView(context, cursor, parent);
           ViewHolder vh = new ViewHolder();
           vh.line1 = (TextView) v.findViewById(R.id.line1);
           vh.line2 = (TextView) v.findViewById(R.id.line2);
           vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
           vh.icon = (ImageView) v.findViewById(R.id.icon);
           vh.icon.setBackgroundDrawable(mDefaultGenreIcon);
           vh.icon.setPadding(0, 0, 1, 0);
           v.setTag(vh);
           return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            
            ViewHolder vh = (ViewHolder) view.getTag();

            String name = cursor.getString(mGenreIdx);
            String displayname = name;
            boolean unknown = name == null || name.equals(MediaStore.UNKNOWN_STRING); 
            if (unknown) {
                displayname = mUnknownGenre;
            }
            vh.line1.setText(displayname);

            /* TODO check for currently playing genre
            long currentgenreid = MusicUtils.getCurrentGenreId();
            ImageView iv = vh.play_indicator;
            if (currentgenreid == aid) {
                iv.setImageDrawable(mNowPlayingOverlay);
            } else {
                iv.setImageDrawable(null);
            }
            */
        }
        
        @Override
        public void changeCursor(Cursor cursor) {
            if (mActivity.isFinishing() && cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor != mActivity.mGenreCursor) {
                mActivity.mGenreCursor = cursor;
                getColumnIndices(cursor);
                super.changeCursor(cursor);
            }
        }
        
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            String s = constraint.toString();
            if (mConstraintIsValid && (
                    (s == null && mConstraint == null) ||
                    (s != null && s.equals(mConstraint)))) {
                return getCursor();
            }
            Cursor c = mActivity.getGenreCursor(null, s);
            mConstraint = s;
            mConstraintIsValid = true;
            return c;
        }
        
        public Object[] getSections() {
            return mIndexer.getSections();
        }
        
        public int getPositionForSection(int section) {
            return mIndexer.getPositionForSection(section);
        }
        
        public int getSectionForPosition(int position) {
            return 0;
        }
    }

    private Cursor mGenreCursor;
    private String mArtistId;

    public void onServiceConnected(ComponentName name, IBinder service) {
        MusicUtils.updateNowPlaying(this);
    }

    public void onServiceDisconnected(ComponentName name) {
        finish();
    }
}

