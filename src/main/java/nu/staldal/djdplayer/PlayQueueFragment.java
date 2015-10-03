/*
 * Copyright (C) 2013-2014 Mikael Ståldal
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
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import nu.staldal.ui.TouchInterceptor;

import java.util.Arrays;

public class PlayQueueFragment extends ListFragment
        implements FragmentServiceConnection, AbsListView.OnScrollListener, MusicUtils.Defs {
    private static final String LOGTAG = "PlayQueueFragment";

    final static String[] mCols = new String[] {
            MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.DURATION,
            MediaStore.Audio.AudioColumns._ID,
            MediaStore.Audio.AudioColumns._ID,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.MIME_TYPE
    };

    private MediaPlayback service;

    PlayQueueCursor playQueueCursor;
    SimpleCursorAdapter listAdapter;
    boolean deletedOneRow;

    private boolean queueZoomed;
    private boolean listScrolled;

    int mSelectedPosition = -1;
    long mSelectedId = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mSelectedPosition = savedInstanceState.getInt("selectedposition", -1);
            mSelectedId = savedInstanceState.getLong("selectedtrack", -1);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        TouchInterceptor listView = new TouchInterceptor(getActivity(), null);
        listView.setId(android.R.id.list);
        listView.setFastScrollEnabled(true);
        listView.setDropListener(new TouchInterceptor.DropListener() {
                public void drop(int from, int to) {
                playQueueCursor.moveItem(from, to);
                listAdapter.notifyDataSetChanged();
                getListView().invalidateViews();
                deletedOneRow = true;
                }
            });
        listView.setRemoveListener(new TouchInterceptor.RemoveListener() {
                public void remove(int which) {
                    removePlaylistItem(which);
                }
            });
        listView.setDivider(null);
        listView.setSelector(R.drawable.list_selector_background);

        registerForContextMenu(listView);

        listScrolled = false;
        listView.setOnScrollListener(this);

        return listView;
    }

    @Override
    public void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MediaPlaybackService.META_CHANGED);
        filter.addAction(MediaPlaybackService.QUEUE_CHANGED);
        getActivity().registerReceiver(mNowPlayingListener, filter);
    }

    @Override
    public void onServiceConnected(MediaPlayback s) {
        service = s;
        playQueueCursor = new PlayQueueCursor();
        listAdapter = new SimpleCursorAdapterWithContextMenu(
                getActivity(),
                R.layout.edit_track_list_item,
                playQueueCursor,
                mCols,
                new int[] { R.id.line1, R.id.line2, R.id.duration, R.id.play_indicator, R.id.crossfade_indicator },
                0);
        listAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            final String unknownArtist = PlayQueueFragment.this.getActivity().getString(R.string.unknown_artist_name);

            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                switch (view.getId()) {
                    case R.id.line2:
                        String name = cursor.getString(columnIndex);
                        if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                            ((TextView) view).setText(unknownArtist);
                        } else {
                            ((TextView) view).setText(name);
                        }

                        return true;

                    case R.id.duration:
                        int secs = cursor.getInt(columnIndex);
                        if (secs == 0) {
                            ((TextView) view).setText("");
                        } else {
                            ((TextView) view).setText(MusicUtils.formatDuration(PlayQueueFragment.this.getActivity(), secs));
                        }
                        return true;

                    case R.id.play_indicator:
                        if (service != null) {
                            int cursorPosition = cursor.getPosition();
                            if (cursorPosition == service.getQueuePosition()) {
                                view.setVisibility(View.VISIBLE);
                            } else {
                                view.setVisibility(View.INVISIBLE);
                            }
                            return true;
                        }

                    case R.id.crossfade_indicator:
                        if (service != null) {
                            int cursorPosition = cursor.getPosition();
                            if (cursorPosition == service.getCrossfadeQueuePosition()) {
                                view.setVisibility(View.VISIBLE);
                            } else {
                                view.setVisibility(View.INVISIBLE);
                            }
                            return true;
                        }

                    default:
                        return false;
                }

            }
        });
        setListAdapter(listAdapter);
    }

    public void setQueueZoomed(boolean queueZoomed) {
        this.queueZoomed = queueZoomed;
        if (!queueZoomed) listScrolled = false;
    }

    public boolean isQueueZoomed() {
        return queueZoomed;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (service != null) {
            String clickOnSong = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(
                    SettingsActivity.CLICK_ON_SONG, SettingsActivity.PLAY_NEXT);
            if (clickOnSong.equals(SettingsActivity.PLAY_NOW)) {
                service.setQueuePosition(position);
            } else {
                if (!service.isPlaying()) {
                    service.setQueuePosition(position);
                }
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(mNowPlayingListener);
        listScrolled = false;
    }

    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putInt("selectedposition", mSelectedPosition);
        outcicle.putLong("selectedtrack", mSelectedId);

        super.onSaveInstanceState(outcicle);
    }

    private boolean removePlaylistItem(int which) {
        View v = getListView().getChildAt(which - getListView().getFirstVisiblePosition());
        if (v == null) {
            Log.i(LOGTAG, "No view when removing playlist item " + which);
            return false;
        }
        if (service != null && which != service.getQueuePosition()) {
            deletedOneRow = true;
        }
        v.setVisibility(View.GONE);
        getListView().invalidateViews();
        boolean ret = playQueueCursor.removeItem(which);
        listAdapter.notifyDataSetChanged();
        v.setVisibility(View.VISIBLE);
        getListView().invalidateViews();
        return ret;
    }

    private final BroadcastReceiver mNowPlayingListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // The service could disappear while the broadcast was in flight,
            // so check to see if it's still valid
            if (service == null) {
                return;
            }

            if (intent.getAction().equals(MediaPlaybackService.QUEUE_CHANGED)) {
                if (deletedOneRow) {
                    // This is the notification for a single row that was
                    // deleted previously, which is already reflected in the UI.
                    deletedOneRow = false;
                    return;
                }
                playQueueCursor.requery();
                listAdapter.notifyDataSetChanged();
            }

            getListView().invalidateViews();
            if (!listScrolled && !queueZoomed) getListView().setSelection(service.getQueuePosition() + 1);
        }
    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;
        AdapterView.AdapterContextMenuInfo mi = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        mSelectedPosition = mi.position;
        playQueueCursor.moveToPosition(mSelectedPosition);
        mSelectedId = playQueueCursor.getLong(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID));

        menu.add(0, PLAY_NOW2, 0, R.string.play_now);

        SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub, NEW_PLAYLIST3, PLAYLIST_SELECTED3);

        menu.add(0, DELETE_ITEM3, 0, R.string.delete_item);

        menu.add(0, TRACK_INFO3, 0, R.string.info);

        menu.add(0, SHARE_VIA3, 0, R.string.share_via);

        // only add the 'search' menu if the selected item is music
        if (MusicUtils.isMusic(playQueueCursor)) {
            menu.add(0, SEARCH_FOR, 0, R.string.search_for);
        }

        menu.setHeaderTitle(
                playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_NOW2: {
                if (service != null) service.setQueuePosition(mSelectedPosition);
                return true;
            }

            case NEW_PLAYLIST3: {
                CreatePlaylist.showMe(getActivity(), new long[] { mSelectedId });
                return true;
            }

            case PLAYLIST_SELECTED3: {
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), new long[] { mSelectedId }, playlist);
                return true;
            }

            case DELETE_ITEM3: {
                final long [] list = new long[1];
                list[0] = (int) mSelectedId;
                String f = getString(R.string.delete_song_desc);
                String desc = String.format(f,
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)));

                new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_song_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setPositiveButton(R.string.delete_confirm_button_text, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MusicUtils.deleteTracks(PlayQueueFragment.this.getActivity(), list);
                            }
                        }).show();
                return true;
            }

            case TRACK_INFO3:
                TrackInfoFragment.showMe(getActivity(),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mSelectedId));

                return true;

            case SHARE_VIA3:
                startActivity(MusicUtils.shareVia(
                        mSelectedId,
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE)),
                        getResources()));
                return true;

            case SEARCH_FOR:
                startActivity(MusicUtils.searchForTrack(
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)),
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)),
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM)),
                        getResources()));
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == SCROLL_STATE_TOUCH_SCROLL || scrollState == SCROLL_STATE_FLING)
            listScrolled = true;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) { }

    @Override
    public void onServiceDisconnected() {
        service = null;
    }

    private class PlayQueueCursor extends AbstractCursor {
        private Cursor mCurrentPlaylistCursor;     // updated in onMove
        private int mSize;                         // size of the queue
        private long[] playQueue;
        private long[] mCursorIdxs;
        private int mCurPos;

        public PlayQueueCursor() {
            init();
        }

        private void init() {
            if (mCurrentPlaylistCursor != null) {
                mCurrentPlaylistCursor.close();
                mCurrentPlaylistCursor = null;
            }
            playQueue = service.getQueue();
            mSize = playQueue.length;
            if (mSize == 0) {
                return;
            }

            mCurrentPlaylistCursor = PlayQueueFragment.this.getActivity().getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mCols, buildPlayQueueWhereClause(playQueue), null, MediaStore.Audio.AudioColumns._ID);

            if (mCurrentPlaylistCursor == null) {
                mSize = 0;
                return;
            }

            int size = mCurrentPlaylistCursor.getCount();
            mCursorIdxs = new long[size];
            mCurrentPlaylistCursor.moveToFirst();
            int colidx = mCurrentPlaylistCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID);
            for (int i = 0; i < size; i++) {
                mCursorIdxs[i] = mCurrentPlaylistCursor.getLong(colidx);
                mCurrentPlaylistCursor.moveToNext();
            }
            mCurrentPlaylistCursor.moveToFirst();
            mCurPos = -1;

            // At this point we can verify the 'now playing' list we got
            // earlier to make sure that all the items in there still exist
            // in the database, and remove those that aren't. This way we
            // don't get any blank items in the list.
            int removed = 0;
            for (int i = playQueue.length - 1; i >= 0; i--) {
                long trackid = playQueue[i];
                int crsridx = Arrays.binarySearch(mCursorIdxs, trackid);
                if (crsridx < 0) {
                    Log.i(LOGTAG, "item no longer exists in db: " + trackid);
                    removed += service.removeTrack(trackid);
                }
            }
            if (removed > 0) {
                playQueue = service.getQueue();
                mSize = playQueue.length;
                if (mSize == 0) {
                    mCursorIdxs = null;
                }
            }
        }

        private String buildPlayQueueWhereClause(long[] playQueue) {
            StringBuilder where = new StringBuilder();
            where.append(MediaStore.Audio.AudioColumns._ID + " IN (");
            for (int i = 0; i < playQueue.length; i++) {
                where.append(playQueue[i]);
                if (i < playQueue.length - 1) {
                    where.append(",");
                }
            }
            where.append(")");
            return where.toString();
        }

        @Override
        public int getCount() {
            return mSize;
        }

        @Override
        public boolean onMove(int oldPosition, int newPosition) {
            if (oldPosition == newPosition)
                return true;

            if (playQueue == null || mCursorIdxs == null || newPosition >= playQueue.length) {
                return false;
            }

            // The cursor doesn't have any duplicates in it, and is not ordered
            // in queue-order, so we need to figure out where in the cursor we should be.

            long newid = playQueue[newPosition];
            int crsridx = Arrays.binarySearch(mCursorIdxs, newid);
            mCurrentPlaylistCursor.moveToPosition(crsridx);
            mCurPos = newPosition;

            return true;
        }

        public boolean removeItem(int which) {
            if (service.removeTracks(which, which) == 0) {
                return false; // delete failed
            }
            int i = which;
            mSize--;
            while (i < mSize) {
                playQueue[i] = playQueue[i + 1];
                i++;
            }
            onMove(-1, mCurPos);
            return true;
        }

        public void moveItem(int from, int to) {
            service.moveQueueItem(from, to);
            playQueue = service.getQueue();
            onMove(-1, mCurPos); // update the underlying cursor
        }

        @Override
        public String getString(int column) {
            try {
                return mCurrentPlaylistCursor.getString(column);
            } catch (Exception ex) {
                onChange(true);
                return "";
            }
        }

        @Override
        public short getShort(int column) {
            return mCurrentPlaylistCursor.getShort(column);
        }

        @Override
        public int getInt(int column) {
            try {
                return mCurrentPlaylistCursor.getInt(column);
            } catch (Exception ex) {
                onChange(true);
                return 0;
            }
        }

        @Override
        public long getLong(int column) {
            try {
                return mCurrentPlaylistCursor.getLong(column);
            } catch (Exception ex) {
                onChange(true);
                return 0;
            }
        }

        @Override
        public float getFloat(int column) {
            return mCurrentPlaylistCursor.getFloat(column);
        }

        @Override
        public double getDouble(int column) {
            return mCurrentPlaylistCursor.getDouble(column);
        }

        @Override
        public boolean isNull(int column) {
            return mCurrentPlaylistCursor.isNull(column);
        }

        @Override
        public String[] getColumnNames() {
            return mCols;
        }

        @Override
        public void deactivate() {
            if (mCurrentPlaylistCursor != null)
                mCurrentPlaylistCursor.deactivate();
        }

        @Override
        public boolean requery() {
            init();
            return true;
        }

        @Override
        public void close() {
            if (mCurrentPlaylistCursor != null)
                mCurrentPlaylistCursor.close();
            super.close();
        }
    }
}
