/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2013 Mikael Ståldal
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
import android.app.LoaderManager;
import android.content.*;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.io.IOException;

/**
 * Activity allowing the user to select a music track on the device, and
 * return it to its caller.  The music picker user interface is fairly
 * extensive, providing information about each track like the music
 * application (title, author, duration), as well as the ability to
 * previous tracks and sort them in different orders.
 * 
 * <p>This class also illustrates how you can load data from a content
 * provider asynchronously, providing a good UI while doing so, perform
 * indexing of the content for use inside of a FastScrollView, and
 * perform filtering of the data as the user presses keys.
 */
public class MusicPicker extends ListActivity
        implements View.OnClickListener, MediaPlayer.OnCompletionListener, LoaderManager.LoaderCallbacks<Cursor>, MusicUtils.Defs {
    private static final String LOGTAG = "MusicPicker";

    /** Holds the previous state of the list, to restore after the async
     * query has completed. */
    static final String LIST_STATE_KEY = "liststate";
    /** Remember whether the list last had focus for restoring its state. */
    static final String FOCUS_KEY = "focused";
    /** Remember the last ordering mode for restoring state. */
    static final String SORT_MODE_KEY = "sortMode";

    /** Menu item to sort the music list by track title. */
    static final int TRACK_MENU = Menu.FIRST;
    /** Menu item to sort the music list by artist name. */
    static final int ARTIST_MENU = Menu.FIRST+1;
    
    /** These are the columns in the music cursor that we are interested in. */
    static final String[] CURSOR_COLS = new String[] {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.TITLE_KEY,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK
    };
    
    /** Uri to the directory of all music being displayed. */
    Uri mBaseUri;
    
    /** This is the adapter used to display all of the tracks. */
    TrackListAdapter mAdapter;

    /** Used to keep track of the last scroll state of the list. */
    Parcelable mListState = null;
    /** Used to keep track of whether the list last had focus. */
    boolean mListHasFocus;
    
    /** The actual sort order the user has selected. */
    int mSortMode = -1;
    /** SQL order by string describing the currently selected sort order. */
    String mSortOrder;

    /** View holding the okay button. */
    View mOkayButton;
    /** View holding the cancel button. */
    View mCancelButton;
    
    /** Which track row ID the user has last selected. */
    long mSelectedId = -1;
    /** Completel Uri that the user has last selected. */
    Uri mSelectedUri;
    
    /** If >= 0, we are currently playing a track for preview, and this is its
     * row ID. */
    long mPlayingId = -1;
    
    /** This is used for playing previews of the music files. */
    MediaPlayer mMediaPlayer;
    
    /**
     * A special implementation of SimpleCursorAdapter that knows how to bind
     * our cursor data to our list item structure, and takes care of other
     * advanced features such as indexing and filtering.
     */
    class TrackListAdapter extends SimpleCursorAdapter implements SectionIndexer {
        private final String mUnknownArtist;

        private int mIdIdx;
        private int mTitleIdx;
        private int mArtistIdx;
        private int mDurationIdx;

        private int mIndexerSortMode;
        private AlphabetIndexer mIndexer;
        
        class ViewHolder {
            TextView line1;
            TextView line2;
            TextView duration;
            RadioButton radio;
            ImageView play_indicator;
        }
        
        TrackListAdapter(Context context, int layout, String[] from, int[] to) {
            super(context, layout, null, from, to, 0);
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = super.newView(context, cursor, parent);
            ViewHolder vh = new ViewHolder();
            vh.line1 = (TextView) v.findViewById(R.id.line1);
            vh.line2 = (TextView) v.findViewById(R.id.line2);
            vh.duration = (TextView) v.findViewById(R.id.duration);
            vh.radio = (RadioButton) v.findViewById(R.id.radio);
            vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder vh = (ViewHolder) view.getTag();
            
            vh.line1.setText(cursor.getString(mTitleIdx));
            
            int secs = cursor.getInt(mDurationIdx);
            if (secs == 0) {
                vh.duration.setText("");
            } else {
                vh.duration.setText(MusicUtils.formatDuration(context, secs));
            }
            
            String name = cursor.getString(mArtistIdx);
            if (name == null || name.equals("<unknown>")) {
                name = mUnknownArtist;
            }
            vh.line2.setText(name);

            // Update the checkbox of the item, based on which the user last
            // selected.  Note that doing it this way means we must have the
            // list view update all of its items when the selected item
            // changes.
            final long id = cursor.getLong(mIdIdx);
            vh.radio.setChecked(id == mSelectedId);

            // Likewise, display the "now playing" icon if this item is
            // currently being previewed for the user.
            ImageView iv = vh.play_indicator;
            if (id == mPlayingId) {
                iv.setImageResource(R.drawable.indicator_ic_mp_playing_list);
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.GONE);
            }
        }
        
        /**
         * This method is called whenever we receive a new cursor due to
         * an async query, and must take care of plugging the new one in
         * to the adapter.
         */
        @Override
        public Cursor swapCursor(Cursor cursor) {
            Cursor res = super.swapCursor(cursor);
            
            if (cursor != null) {
                // Retrieve indices of the various columns we are interested in.
                mIdIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                mTitleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                mArtistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                mDurationIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);

                // If the sort mode has changed, or we haven't yet created an
                // indexer one, then create a new one that is indexing the
                // appropriate column based on the sort mode.
                if (mIndexerSortMode != mSortMode || mIndexer == null) {
                    mIndexerSortMode = mSortMode;
                    int idx = mTitleIdx;
                    switch (mIndexerSortMode) {
                        case ARTIST_MENU:
                            idx = mArtistIdx;
                            break;
                    }
                    mIndexer = new MusicAlphabetIndexer(cursor, idx,
                            getResources().getString(R.string.fast_scroll_alphabet));
                    
                // If we have a valid indexer, but the cursor has changed since
                // its last use, then point it to the current cursor.
                } else {
                    mIndexer.setCursor(cursor);
                }
            }
            
            return res;
        }
        
        public int getPositionForSection(int section) {
            Cursor cursor = getCursor();
            if (cursor == null) {
                // No cursor, the section doesn't exist so just return 0
                return 0;
            }
            
            return mIndexer.getPositionForSection(section);
        }

        public int getSectionForPosition(int position) {
            return 0;
        }

        public Object[] getSections() {
            if (mIndexer != null) {
                return mIndexer.getSections();
            }
            return null;
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        int sortMode = TRACK_MENU;
        if (icicle == null) {
            mSelectedUri = getIntent().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI);
        } else {
            mSelectedUri = icicle.getParcelable(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI);
            // Retrieve list state. This will be applied after the
            // QueryHandler has run
            mListState = icicle.getParcelable(LIST_STATE_KEY);
            mListHasFocus = icicle.getBoolean(FOCUS_KEY);
            sortMode = icicle.getInt(SORT_MODE_KEY, sortMode);
        }
        mBaseUri = getIntent().getData();
        if (mBaseUri == null) {
            Log.w(LOGTAG, "No data URI given to PICK action");
            finish();
            return;
        }

        setContentView(R.layout.music_picker);

        mSortOrder = MediaStore.Audio.Media.TITLE_KEY;

        final ListView listView = getListView();

        listView.setItemsCanFocus(false);
        
        mAdapter = new TrackListAdapter(this,
                R.layout.music_picker_item, new String[] {},
                new int[] {});

        setListAdapter(mAdapter);
        
        listView.setTextFilterEnabled(true);
        
        // We manually save/restore the listview state
        listView.setSaveEnabled(false);
        
        mOkayButton = findViewById(R.id.okayButton);
        mOkayButton.setOnClickListener(this);
        mCancelButton = findViewById(R.id.cancelButton);
        mCancelButton.setOnClickListener(this);
        
        // If there is a currently selected Uri, then try to determine who it is.
        if (mSelectedUri != null) {
            Uri.Builder builder = mSelectedUri.buildUpon();
            String path = mSelectedUri.getEncodedPath();
            int idx = path.lastIndexOf('/');
            if (idx >= 0) {
                path = path.substring(0, idx);
            }
            builder.encodedPath(path);
            Uri baseSelectedUri = builder.build();
            if (baseSelectedUri.equals(mBaseUri)) {
                // If the base Uri of the selected Uri is the same as our
                // content's base Uri, then use the selection!
                mSelectedId = ContentUris.parseId(mSelectedUri);
            }
        }
        
        setSortMode(sortMode);
        
        getLoaderManager().initLoader(0, null, this);        
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in. (The framework will take care of closing the old cursor once we return.)
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no longer using it.
        mAdapter.swapCursor(null);
    }    
    
    @Override 
    public boolean onOptionsItemSelected(MenuItem item) {
        if (setSortMode(item.getItemId())) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override 
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, TRACK_MENU, Menu.NONE, R.string.sort_by_track);
        menu.add(Menu.NONE, ARTIST_MENU, Menu.NONE, R.string.sort_by_artist);
        return true;
    }

    @Override 
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        // Save list state in the bundle so we can restore it after the
        // QueryHandler has run
        icicle.putParcelable(LIST_STATE_KEY, getListView().onSaveInstanceState());
        icicle.putBoolean(FOCUS_KEY, getListView().hasFocus());
        icicle.putInt(SORT_MODE_KEY, mSortMode);
    }
    
    @Override 
    public void onPause() {
        super.onPause();
        stopMediaPlayer();
    }
        
    /**
     * Changes the current sort order, building the appropriate query string for the selected order.
     */
    boolean setSortMode(int sortMode) {
        if (sortMode != mSortMode) {
            switch (sortMode) {
                case TRACK_MENU:
                    mSortMode = sortMode;
                    mSortOrder = MediaStore.Audio.Media.TITLE_KEY;
                    getLoaderManager().restartLoader(0, null, this);
                    return true;
                case ARTIST_MENU:
                    mSortMode = sortMode;
                    mSortOrder = MediaStore.Audio.Media.ARTIST_KEY + " ASC, "
                            + MediaStore.Audio.Media.TRACK + " ASC, "
                            + MediaStore.Audio.Media.TITLE_KEY + " ASC";
                    getLoaderManager().restartLoader(0, null, this);
                    return true;
            }
            
        }
        return false;
    }
    
    
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // We want to show all audio files, even recordings
        return new CursorLoader(this, mBaseUri, CURSOR_COLS, MediaStore.Audio.Media.TITLE + " != ''", null, mSortOrder);
    }
        
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        mAdapter.getCursor().moveToPosition(position);
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        long newId = mAdapter.getCursor().getLong(mAdapter.getCursor().getColumnIndex(MediaStore.Audio.Media._ID));
        mSelectedUri = ContentUris.withAppendedId(uri, newId);

        mSelectedId = newId;
        if (newId != mPlayingId || mMediaPlayer == null) {
            stopMediaPlayer();
            mMediaPlayer = new MediaPlayer();
            try {
                mMediaPlayer.setDataSource(this, mSelectedUri);
                mMediaPlayer.setOnCompletionListener(this);
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
                mMediaPlayer.prepare();
                mMediaPlayer.start();
                mPlayingId = newId;
                getListView().invalidateViews();
            } catch (IOException e) {
                Log.w("MusicPicker", "Unable to play track", e);
            }
        } else {
            stopMediaPlayer();
            getListView().invalidateViews();
        }

        mOkayButton.setEnabled(true);
    }

    public void onCompletion(MediaPlayer mp) {
        if (mMediaPlayer == mp) {
            mp.stop();
            mp.release();
            mMediaPlayer = null;
            mPlayingId = -1;
            getListView().invalidateViews();
        }
    }
    
    void stopMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mPlayingId = -1;
        }
    }
    
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.okayButton:
                if (mSelectedId >= 0) {
                    setResult(RESULT_OK, new Intent().setData(mSelectedUri));
                    finish();
                }
                break;

            case R.id.cancelButton:
                finish();
                break;
        }
    }
}
