/*
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
import android.content.*;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;

import java.io.File;

public class FolderBrowserActivity extends CategoryBrowserActivity<FolderBrowserActivity.FolderListAdapter> {
    private static final String CURRENT_FOLDER = "currentfolder";

    public static final String CATEGORY_ID = "folder";

    private String mCurrentFolder;

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getListView().invalidateViews();
        }
    };

    @Override
    protected int getTabId() {
        return R.id.foldertab;
    }

    protected Cursor getCursor(AsyncQueryHandler async) {
        String[] cols = new String[] {
            FolderContract.Folder._ID,
            FolderContract.Folder._COUNT,
            FolderContract.Folder.PATH,
            FolderContract.Folder.NAME
        };

        Cursor ret = null;
        Uri uri = FolderContract.Folder.CONTENT_URI;
        if (async != null) {
            async.startQuery(0, null, uri, cols, null, null, null);
        } else {
            ret = MusicUtils.query(this, uri,cols, null, null, null);
        }
        return ret;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mCurrentFolder = icicle.getString(CURRENT_FOLDER);
        }

        mAdapter = (FolderListAdapter)getLastNonConfigurationInstance();
        if (mAdapter == null) {
            //Log.i("@@@", "starting query");
            mAdapter = new FolderListAdapter(
                    getApplication(),
                    R.layout.track_list_item,
                    mCursor,
                    new String[] {},
                    new int[] {},
                    this);
            setListAdapter(mAdapter);
            if (!withTabs) setTitle(R.string.working_folders);
            getCursor(mAdapter.getQueryHandler());
        } else {
            mAdapter.setActivity(this);
            setListAdapter(mAdapter);
            mCursor = mAdapter.getCursor();
            if (mCursor != null) {
                init(mCursor);
            } else {
                getCursor(mAdapter.getQueryHandler());
            }
        }
        mToken = MusicUtils.bindToService(this, this);
    }

    @Override
    protected void setTitle() {
        setTitle(R.string.folders_title);
    }

    @Override
    protected void reloadData() {
        if (mAdapter != null) {
            getCursor(mAdapter.getQueryHandler());
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putString(CURRENT_FOLDER, mCurrentFolder);
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

    private long[] fetchSongList(String folder) {
        Cursor cursor = MusicUtils.query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.IS_MUSIC + "=1 AND " + MediaStore.Audio.Media.DATA + " LIKE ?",
                new String[] { folder + "%" }, null);

        if (cursor != null) {
            long [] list = MusicUtils.getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return MusicUtils.sEmptyList;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/djd.track");
        mCursor.moveToPosition(position);
        String path = mCursor.getString(mCursor.getColumnIndexOrThrow(FolderContract.Folder.PATH));
        intent.putExtra(CATEGORY_ID, path);
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
        mCurrentFolder = mCursor.getString(mCursor.getColumnIndexOrThrow(FolderContract.Folder.PATH));
        menu.setHeaderTitle(mCursor.getString(mCursor.getColumnIndexOrThrow(FolderContract.Folder.NAME)));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_ALL: {
                long[] songs = fetchSongList(mCurrentFolder);
                MusicUtils.playAll(this, songs);
                return true;
            }

            case QUEUE_ALL: {
                long[] songs = fetchSongList(mCurrentFolder);
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
                long[] songs = fetchSongList(mCurrentFolder);
                MusicUtils.addToPlaylist(this, songs, playlist);
                return true;
            }

            case DELETE_ITEM: {
                final long [] list = fetchSongList(mCurrentFolder);
                String f = getString(R.string.delete_folder_desc);
                String desc = String.format(f, mCurrentFolder);

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
                                MusicUtils.deleteTracks(FolderBrowserActivity.this, list);
                            }
                        }).show();
                return true;
            }

            default:
                if (item.getItemId() > INTERLEAVE_ALL) {
                    int currentCount = (item.getItemId() - INTERLEAVE_ALL) / 10;
                    int newCount = (item.getItemId() - INTERLEAVE_ALL) % 10;
                    long[] songs = fetchSongList(mCurrentFolder);
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
                        long[] songs = fetchSongList(mCurrentFolder);
                        MusicUtils.addToPlaylist(this, songs, Long.parseLong(uri.getLastPathSegment()));
                    }
                }
                break;
        }
    }

    static class FolderListAdapter extends SimpleCursorAdapter {
        protected final Drawable mNowPlayingOverlay;
        protected final Resources mResources;
        protected String mConstraint = null;
        protected boolean mConstraintIsValid = false;
        protected AsyncQueryHandler mQueryHandler;
        protected FolderBrowserActivity mActivity = null;

        public void setActivity(FolderBrowserActivity activity) {
            mActivity = activity;
        }

        class QueryHandler extends AsyncQueryHandler {
            QueryHandler(ContentResolver res) {
                super(res);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                //Log.i("@@@", "query complete: " + cursor.getCount() + "   " + mActivity);
                mActivity.init(cursor);
            }
        }

        public AsyncQueryHandler getQueryHandler() {
            return mQueryHandler;
        }

        protected static class ViewHolder {
            TextView line1;
            TextView line2;
            ImageView play_indicator;
        }

        public FolderListAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to,
                                           FolderBrowserActivity currentActivity) {
            super(context, layout, cursor, from, to);
            this.mActivity = currentActivity;

            Resources r = context.getResources();
            mNowPlayingOverlay = r.getDrawable(R.drawable.indicator_ic_mp_playing_list);

            mResources = context.getResources();

            mQueryHandler = new QueryHandler(context.getContentResolver());
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = super.newView(context, cursor, parent);
            ViewHolder vh = new ViewHolder();
            vh.line1 = (TextView) v.findViewById(R.id.line1);
            vh.line2 = (TextView) v.findViewById(R.id.line2);
            vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder vh = (ViewHolder) view.getTag();

            vh.line1.setText(cursor.getString(cursor.getColumnIndexOrThrow(FolderContract.Folder.NAME)));

            String folder = cursor.getString(cursor.getColumnIndexOrThrow(FolderContract.Folder.PATH));

            int numSongs = cursor.getInt(cursor.getColumnIndexOrThrow(FolderContract.Folder._COUNT));
            if (numSongs > 0) {
                vh.line2.setText(mResources.getQuantityString(R.plurals.Nsongs, numSongs, numSongs));
            }

            File currentFolder = MusicUtils.getCurrentFolder();
            ImageView iv = vh.play_indicator;
            if (currentFolder != null && currentFolder.getAbsolutePath().equals(folder)) {
                iv.setImageDrawable(mNowPlayingOverlay);
            } else {
                iv.setImageDrawable(null);
            }
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
            Cursor c = mActivity.getCursor(null);
            mConstraint = s;
            mConstraintIsValid = true;
            return c;
        }
    }
}
