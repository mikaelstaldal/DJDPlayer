/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;

import java.text.Collator;
import java.util.ArrayList;

public class PlaylistBrowserActivity extends CategoryBrowserActivity<PlaylistBrowserActivity.PlaylistListAdapter> {
    private static final String TAG = "PlaylistBrowserActivity";

    public static final String CATEGORY_ID = "playlist";

    private static final String CURRENT_PLAYLIST = "currentplaylist";
    private static final String CURRENT_PLAYLIST_NAME = "currentplaylistname";

    private static final int DELETE_PLAYLIST = CHILD_MENU_BASE + 1;
    private static final int EDIT_PLAYLIST = CHILD_MENU_BASE + 2;
    private static final int RENAME_PLAYLIST = CHILD_MENU_BASE + 3;
    private static final int CHANGE_WEEKS = CHILD_MENU_BASE + 4;
    private static final int CREATE_NEW_PLAYLIST = CHILD_MENU_BASE + 5;
    private static final int EXPORT_PLAYLIST = CHILD_MENU_BASE + 6;

    private static final long RECENTLY_ADDED_PLAYLIST = -1;
    private static final long ALL_SONGS_PLAYLIST = -2;
    private static final long PODCASTS_PLAYLIST = -3;

    private long mCurrentId;
    private String mPlaylistName;
    private boolean mCreateShortcut;

    @Override
    protected int getTabId() {
        return R.id.playlisttab;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mCurrentId = icicle.getLong(CURRENT_PLAYLIST);
            mPlaylistName = icicle.getString(CURRENT_PLAYLIST_NAME);
        }

        if (Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())) {
            mCreateShortcut = true;
        }

        mAdapter = (PlaylistListAdapter)getLastNonConfigurationInstance();
        if (mAdapter == null) {
            //Log.i("@@@", "starting query");
            mAdapter = new PlaylistListAdapter(
                    getApplication(),
                    this,
                    R.layout.track_list_item,
                    mCursor,
                    new String[] { MediaStore.Audio.Playlists.NAME},
                    new int[] { android.R.id.text1 });
            setListAdapter(mAdapter);
            setTitle(R.string.working_playlists);
            getCursor(mAdapter.getQueryHandler(), null);
        } else {
            mAdapter.setActivity(this);
            setListAdapter(mAdapter);
            mCursor = mAdapter.getCursor();
            // If mCursor is null, this can be because it doesn't have
            // a cursor yet (because the initial query that sets its cursor
            // is still in progress), or because the query failed.
            // In order to not flash the error dialog at the user for the
            // first case, simply retry the query when the cursor is null.
            // Worst case, we end up doing the same query twice.
            if (mCursor != null) {
                init(mCursor);
            } else {
                setTitle(R.string.working_playlists);
                getCursor(mAdapter.getQueryHandler(), null);
            }
        }
        mToken = MusicUtils.bindToService(this, this);
    }

    @Override
    public void onServiceConnected(ComponentName classname, IBinder obj) {
        super.onServiceConnected(classname, obj);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            long id = Long.parseLong(intent.getExtras().getString("playlist"));
            MusicUtils.playAll(this, fetchSongList(id));
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putLong(CURRENT_PLAYLIST, mCurrentId);
        outcicle.putString(CURRENT_PLAYLIST_NAME, mPlaylistName);
        super.onSaveInstanceState(outcicle);
    }

    @Override
    protected void setTitle() {
        setTitle(R.string.playlists_title);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        if (mCreateShortcut) {
            return;
        }

        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;
        mCurrentId = mi.id;

        menu.add(0, PLAY_ALL, 0, R.string.play_all);
        menu.add(0, QUEUE_ALL, 0, R.string.queue_all);
        SubMenu interleave = menu.addSubMenu(0, INTERLEAVE_ALL, 0, R.string.interleave_all);
        for (int i = 1; i<=5; i++) {
            for (int j = 1; j<=5; j++) {
                interleave.add(2, INTERLEAVE_ALL+10*i+j, 0, getResources().getString(R.string.interleaveNNN, i, j));
            }
        }

        if (mCurrentId >= 0) {
            menu.add(0, DELETE_PLAYLIST, 0, R.string.delete_playlist_menu);
        }

        if (mCurrentId == RECENTLY_ADDED_PLAYLIST) {
            menu.add(0, EDIT_PLAYLIST, 0, R.string.edit_playlist_menu);
        }

        if (mCurrentId >= 0) {
            menu.add(0, RENAME_PLAYLIST, 0, R.string.rename_playlist_menu);
        }

        menu.add(0, EXPORT_PLAYLIST, 0, R.string.export_playlist_menu);

        mCursor.moveToPosition(mi.position);
        mPlaylistName = mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME));
        menu.setHeaderTitle(mPlaylistName);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_ALL:
                MusicUtils.playAll(this, fetchSongList(mCurrentId));
                return true;

            case QUEUE_ALL:
                MusicUtils.queue(this, fetchSongList(mCurrentId));
                return true;

            case DELETE_PLAYLIST:
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, mCurrentId);
                getContentResolver().delete(uri, null, null);
                Toast.makeText(this, R.string.playlist_deleted_message, Toast.LENGTH_SHORT).show();
                if (mCursor.getCount() == 0) {
                    setTitle(R.string.no_playlists_title);
                }
                return true;

            case EDIT_PLAYLIST:
                if (mCurrentId == RECENTLY_ADDED_PLAYLIST) {
                    Intent intent = new Intent();
                    intent.setClass(this, WeekSelector.class);
                    startActivityForResult(intent, CHANGE_WEEKS);
                    return true;
                } else {
                    Log.e(TAG, "should not be here");
                }
                return true;

            case RENAME_PLAYLIST:
                Intent intent = new Intent();
                intent.setClass(this, RenamePlaylist.class);
                intent.putExtra("rename", mCurrentId);
                startActivityForResult(intent, RENAME_PLAYLIST);
                return true;

            case EXPORT_PLAYLIST:
                MusicUtils.exportPlaylist(this, mPlaylistName, fetchSongList(mCurrentId));
                return true;

            default:
                if (item.getItemId() > INTERLEAVE_ALL) {
                    int currentCount = (item.getItemId() - INTERLEAVE_ALL) / 10;
                    int newCount = (item.getItemId() - INTERLEAVE_ALL) % 10;
                    MusicUtils.interleave(this, fetchSongList(mCurrentId), currentCount, newCount);
                    return true;
                }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (mCreateShortcut) {
            final Intent shortcut = new Intent();
            shortcut.setAction(Intent.ACTION_VIEW);
            shortcut.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/playlist");
            shortcut.putExtra(CATEGORY_ID, String.valueOf(id));

            final Intent intent = new Intent();
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, ((TextView) v.findViewById(R.id.line1)).getText());
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(
                    this, R.drawable.ic_launcher_shortcut_music_playlist));

            setResult(RESULT_OK, intent);
            finish();
            return;
        }
        if (id == RECENTLY_ADDED_PLAYLIST) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/djd.track");
            intent.putExtra(CATEGORY_ID, "recentlyadded");
            startActivity(intent);
        } else if (id == PODCASTS_PLAYLIST) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/djd.track");
            intent.putExtra(CATEGORY_ID, "podcasts");
            startActivity(intent);
        } else {
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/djd.track");
            intent.putExtra(CATEGORY_ID, Long.valueOf(id).toString());
            startActivity(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, CREATE_NEW_PLAYLIST, 0, R.string.create_new_playlist).setIcon(android.R.drawable.ic_menu_add);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case CREATE_NEW_PLAYLIST:
                startActivity(new Intent(this, CreatePlaylist.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private long[] fetchSongList(long playlistId) {
        return MusicUtils.getSongListForCursorAndClose(fetchSongListCursor(playlistId));
    }

    private Cursor fetchSongListCursor(long playlistId) {
        if (playlistId == RECENTLY_ADDED_PLAYLIST) {
            // do a query for all songs added in the last X weeks
            int X = MusicUtils.getIntPref(this, "numweeks", 2) * (3600 * 24 * 7);
            final String[] ccols = new String[] { MediaStore.Audio.Media._ID};
            String where = MediaStore.MediaColumns.DATE_ADDED + ">" + (System.currentTimeMillis() / 1000 - X);
            return MusicUtils.query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    ccols, where, null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        } else if (playlistId == PODCASTS_PLAYLIST) {
            // do a query for all files that are podcasts
            final String[] ccols = new String[] { MediaStore.Audio.Media._ID};
            return MusicUtils.query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    ccols, MediaStore.Audio.Media.IS_PODCAST + "=1",
                    null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        } else if (playlistId == ALL_SONGS_PLAYLIST) {
            return MusicUtils.query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID}, MediaStore.Audio.Media.IS_MUSIC + "=1",
                    null, null);
        } else {
            final String[] ccols = new String[] { MediaStore.Audio.Playlists.Members.AUDIO_ID };
            return MusicUtils.query(this, MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                    ccols, null, null, MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
        }
    }

    String[] mCols = new String[] {
            MediaStore.Audio.Playlists._ID,
            MediaStore.Audio.Playlists.NAME
    };

    @Override
    protected void reloadData() {
        if (mAdapter != null) {
            getCursor(mAdapter.getQueryHandler(), null);
        }
    }

    protected Cursor getCursor(AsyncQueryHandler async, String filter) {
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Playlists.NAME + " != ''");
        
        // Add in the filtering constraints
        String [] keywords = null;
        if (filter != null) {
            String [] searchWords = filter.split(" ");
            keywords = new String[searchWords.length];
            Collator col = Collator.getInstance();
            col.setStrength(Collator.PRIMARY);
            for (int i = 0; i < searchWords.length; i++) {
                keywords[i] = '%' + searchWords[i] + '%';
                where.append(" AND ");
                where.append(MediaStore.Audio.Playlists.NAME + " LIKE ?");
            }
        }
        
        String whereclause = where.toString();

        if (async != null) {
            async.startQuery(0, null, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    mCols, whereclause, keywords, MediaStore.Audio.Playlists.NAME);
            return null;
        }
        Cursor c = MusicUtils.query(this, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                mCols, whereclause, keywords, MediaStore.Audio.Playlists.NAME);
        
        return mergedCursor(c);
    }
    
    private Cursor mergedCursor(Cursor c) {
        if (c == null) {
            return null;
        }
        if (c instanceof MergeCursor) {
            // this shouldn't happen, but fail gracefully
            Log.d("PlaylistBrowserActivity", "Already wrapped");
            return c;
        }
        MatrixCursor autoplaylistscursor = new MatrixCursor(mCols);
        if (mCreateShortcut) {
            ArrayList<Object> all = new ArrayList<Object>(2);
            all.add(ALL_SONGS_PLAYLIST);
            all.add(getString(R.string.play_all));
            autoplaylistscursor.addRow(all);
        }
        ArrayList<Object> recent = new ArrayList<Object>(2);
        recent.add(RECENTLY_ADDED_PLAYLIST);
        recent.add(getString(R.string.recentlyadded));
        autoplaylistscursor.addRow(recent);
        
        // check if there are any podcasts
        Cursor counter = MusicUtils.query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] {"count(*)"}, "is_podcast=1", null, null);
        if (counter != null) {
            counter.moveToFirst();
            int numpodcasts = counter.getInt(0);
            counter.close();
            if (numpodcasts > 0) {
                ArrayList<Object> podcasts = new ArrayList<Object>(2);
                podcasts.add(PODCASTS_PLAYLIST);
                podcasts.add(getString(R.string.podcasts_listitem));
                autoplaylistscursor.addRow(podcasts);
            }
        }

        return new MergeCursor(new Cursor [] {autoplaylistscursor, c});
    }
    
    static class PlaylistListAdapter extends SimpleCursorAdapter {
        int mTitleIdx;
        int mIdIdx;
        private String mConstraint = null;
        private boolean mConstraintIsValid = false;
        private AsyncQueryHandler mQueryHandler;
        private PlaylistBrowserActivity mActivity = null;

        public void setActivity(PlaylistBrowserActivity activity) {
            mActivity = activity;
        }

        class QueryHandler extends AsyncQueryHandler {
            QueryHandler(ContentResolver res) {
                super(res);
            }
            
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                //Log.i("@@@", "query complete: " + cursor.getCount() + "   " + mActivity);
                if (cursor != null) {
                    cursor = mActivity.mergedCursor(cursor);
                }
                mActivity.init(cursor);
            }
        }

        PlaylistListAdapter(Context context, PlaylistBrowserActivity currentactivity,
                int layout, Cursor cursor, String[] from, int[] to) {
            super(context, layout, cursor, from, to);
            mActivity = currentactivity;
            getColumnIndices(cursor);
            mQueryHandler = new QueryHandler(context.getContentResolver());
        }

        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
                mTitleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME);
                mIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID);
            }
        }

        public AsyncQueryHandler getQueryHandler() {
            return mQueryHandler;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView line1 = (TextView)view.findViewById(R.id.line1);
            TextView line2 = (TextView)view.findViewById(R.id.line2);
            ImageView icon = (ImageView)view.findViewById(R.id.icon);
            ImageView playIndicator = (ImageView)view.findViewById(R.id.play_indicator);

            String name = cursor.getString(mTitleIdx);
            line1.setText(name);

            long id = cursor.getLong(mIdIdx);

            if (id == RECENTLY_ADDED_PLAYLIST) {
                icon.setImageResource(R.drawable.ic_mp_playlist_recently_added_list);
            } else {
                icon.setImageResource(R.drawable.ic_mp_playlist_list);
            }
            ViewGroup.LayoutParams p = icon.getLayoutParams();
            p.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;

            int numSongs = mActivity.fetchNumberOfSongs(cursor, id);
            if (numSongs > 0) {
                line2.setText(context.getResources().getQuantityString(R.plurals.Nsongs, numSongs, numSongs));
            } else {
                line2.setText("");
            }

            playIndicator.setVisibility(View.GONE);
        }

        @Override
        public void changeCursor(Cursor cursor) {
            if (mActivity.isFinishing() && cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor != mActivity.mCursor) {
                mActivity.mCursor = cursor;
                super.changeCursor(cursor);
                getColumnIndices(cursor);
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
            Cursor c = mActivity.getCursor(null, s);
            mConstraint = s;
            mConstraintIsValid = true;
            return c;
        }
    }

    private int fetchNumberOfSongs(Cursor playlistCursor, long id) {
        if (id < 0) return 0;
        Cursor cursor = fetchSongListCursor(id); // TODO [mikes] this is quite slow
        if (cursor == null) return 0;
        int count = cursor.getCount();
        cursor.close();
        return count;
    }
}
