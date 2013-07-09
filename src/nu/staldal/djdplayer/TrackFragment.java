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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.*;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.*;
import android.widget.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class TrackFragment extends BrowserFragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String LOGTAG = "TrackFragment";

    private static final int NEW_PLAYLIST_ALL = CHILD_MENU_BASE + 2;
    private static final int NEW_PLAYLIST_SINGLE = CHILD_MENU_BASE + 3;
    private static final int REMOVE_FROM_PLAYLIST = CHILD_MENU_BASE + 4;
    private static final int TRACK_INFO = CHILD_MENU_BASE + 5;
    private static final int SEARCH_FOR = CHILD_MENU_BASE + 6;

    private static final String[] CURSOR_COLS = new String[] {
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ARTIST_ID,
        MediaStore.Audio.Media.DURATION
    };
    private static final String[] PLAYLIST_MEMBER_COLS = new String[] {
        MediaStore.Audio.Playlists.Members._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ARTIST_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Playlists.Members.PLAY_ORDER,
        MediaStore.Audio.Playlists.Members.AUDIO_ID,
        MediaStore.Audio.Media.IS_MUSIC
    };

    private String mCurrentTrackName;
    private String mCurrentAlbumName;
    private String mCurrentArtistNameForAlbum;
    private TrackListAdapter mAdapter;

    private int mSelectedPosition;
    private long mSelectedId;

    private long mAlbumId;
    private long mArtistId;
    private String mPlaylist;
    private long mGenreId;
    private String mFolder;
    private boolean mEditMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (savedInstanceState != null) {
            mSelectedPosition = savedInstanceState.getInt("selectedposition");
            mSelectedId = savedInstanceState.getLong("selectedtrack");
            mAlbumId = savedInstanceState.getLong("album");
            mArtistId = savedInstanceState.getLong("artist");
            mPlaylist = savedInstanceState.getString("playlist");
            mGenreId = savedInstanceState.getLong("genre");
            mFolder = savedInstanceState.getString("folder");
            mEditMode = savedInstanceState.getBoolean("editmode", false);
        } else {
            mAlbumId = MusicUtils.parseLong(getActivity().getIntent().getStringExtra("album"));
            mArtistId = MusicUtils.parseLong(getActivity().getIntent().getStringExtra("artist"));
            mPlaylist = getActivity().getIntent().getStringExtra(PlaylistFragment.CATEGORY_ID);
            mGenreId = MusicUtils.parseLong(getActivity().getIntent().getStringExtra("genre"));
            mFolder = getActivity().getIntent().getStringExtra(FolderFragment.CATEGORY_ID);
            mEditMode = Intent.ACTION_EDIT.equals(getActivity().getIntent().getAction());
        }

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ListView listView;

        if (mEditMode) {
            listView = new TouchInterceptor(getActivity(), null);
            ((TouchInterceptor) listView).setDropListener(new TouchInterceptor.DropListener() {
                    public void drop(int from, int to) {
                        MediaStore.Audio.Playlists.Members.moveItem(getActivity().getContentResolver(),
                                Long.valueOf(mPlaylist), from, to);
                    }
                });
            ((TouchInterceptor) listView).setRemoveListener(new TouchInterceptor.RemoveListener() {
                    public void remove(int which) {
                        removePlaylistItem(which);
                    }
                });
            listView.setDivider(null);
            listView.setSelector(R.drawable.list_selector_background);
        } else {
            listView = new ListView(getActivity());
            listView.setTextFilterEnabled(true);
        }

        listView.setId(android.R.id.list);
        listView.setFastScrollEnabled(true);

        registerForContextMenu(listView);

        return listView;
    }
    
    @Override
    public void onActivityCreated(Bundle icicle) {
        super.onActivityCreated(icicle);

        mAdapter = new TrackListAdapter(
                getActivity(), // need to use application context to avoid leaks
                mEditMode ? R.layout.edit_track_list_item : R.layout.track_list_item,
                null, // cursor
                new String[] {},
                new int[] {},
                mPlaylist != null && !(mPlaylist.equals("podcasts") || mPlaylist.equals("recentlyadded")));
        setListAdapter(mAdapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");

        if (mGenreId != -1) {
            Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", mGenreId);
            return new CursorLoader(getActivity(), uri, CURSOR_COLS, where.toString(), null,
                    MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER);
        } else if (mPlaylist != null) {
            if (mPlaylist.equals("podcasts")) {
                where.append(" AND " + MediaStore.Audio.Media.IS_PODCAST + "=1");
                Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                return new CursorLoader(getActivity(), uri, CURSOR_COLS, where.toString(), null,
                        MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
            } else if (mPlaylist.equals("recentlyadded")) {
                // do a query for all songs added in the last X weeks
                Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                int X = MusicUtils.getIntPref(getActivity(), "numweeks", 2) * (3600 * 24 * 7);
                where.append(" AND " + MediaStore.MediaColumns.DATE_ADDED + ">");
                where.append(System.currentTimeMillis() / 1000 - X);
                return new CursorLoader(getActivity(), uri, CURSOR_COLS, where.toString(), null,
                        MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
            } else {
                Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                        Long.valueOf(mPlaylist));
                return new CursorLoader(getActivity(), uri, PLAYLIST_MEMBER_COLS, where.toString(), null,
                        MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
            }
        } else if (mFolder != null) {
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            where.append(" AND " + MediaStore.Audio.Media.DATA + " LIKE ?");
            where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
            return new CursorLoader(getActivity(), uri, CURSOR_COLS, where.toString(), new String[] { mFolder + "%" },
                                MediaStore.Audio.Media.DATA);
        } else if (mAlbumId != -1) {
            where.append(" AND " + MediaStore.Audio.Media.ALBUM_ID + "=" + mAlbumId);
            String mSortOrder = MediaStore.Audio.Media.TRACK + ", " + MediaStore.Audio.Media.TITLE_KEY;

            where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            return new CursorLoader(getActivity(), uri, CURSOR_COLS, where.toString(), null, mSortOrder);
        } else if (mArtistId != -1) {
            where.append(" AND " + MediaStore.Audio.Media.ARTIST_ID + "=" + mArtistId);
            String mSortOrder = MediaStore.Audio.Media.DEFAULT_SORT_ORDER;
            
            where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            return new CursorLoader(getActivity(), uri, CURSOR_COLS, where.toString(), null, mSortOrder);
        } else { // all songs
            String mSortOrder = MediaStore.Audio.Media.DEFAULT_SORT_ORDER;

            where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            return new CursorLoader(getActivity(), uri, CURSOR_COLS, where.toString(), null, mSortOrder);
        }
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

    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putInt("selectedposition", mSelectedPosition);
        outcicle.putLong("selectedtrack", mSelectedId);

        outcicle.putLong("artist", mArtistId);
        outcicle.putLong("album", mAlbumId);
        outcicle.putString("playlist", mPlaylist);
        outcicle.putLong("genre", mGenreId);
        outcicle.putString("folder", mFolder);
        outcicle.putBoolean("editmode", mEditMode);

        super.onSaveInstanceState(outcicle);
    }

    private boolean removePlaylistItem(int which) {
        View v = getListView().getChildAt(which - getListView().getFirstVisiblePosition());
        if (v == null) {
            Log.d(LOGTAG, "No view when removing playlist item " + which);
            return false;
        }
        v.setVisibility(View.GONE);
        getListView().invalidateViews();
        int colidx = mAdapter.getCursor().getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members._ID);
        mAdapter.getCursor().moveToPosition(which);
        long id = mAdapter.getCursor().getLong(colidx);
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                Long.valueOf(mPlaylist));
        boolean ret = getActivity().getContentResolver().delete(ContentUris.withAppendedId(uri, id), null, null) > 0;
        v.setVisibility(View.VISIBLE);
        getListView().invalidateViews();
        return ret;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;

        menu.add(0, PLAY_NOW, 0, R.string.play_now);
        menu.add(0, PLAY_NEXT, 0, R.string.play_next);
        menu.add(0, QUEUE, 0, R.string.queue);
        SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub);
        if (mEditMode) {
            menu.add(0, REMOVE_FROM_PLAYLIST, 0, R.string.remove_from_playlist);
        }
        menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu);
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);
        AdapterView.AdapterContextMenuInfo mi = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        mSelectedPosition = mi.position;
        mAdapter.getCursor().moveToPosition(mSelectedPosition);
        try {
            int id_idx = mAdapter.getCursor().getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
            mSelectedId = mAdapter.getCursor().getLong(id_idx);
        } catch (IllegalArgumentException ex) {
            mSelectedId = mi.id;
        }

        menu.add(0, TRACK_INFO, 0, R.string.info);

        menu.add(0, SHARE_VIA, 0, R.string.share_via);

        // only add the 'search' menu if the selected item is music
        if (MusicUtils.isMusic(mAdapter.getCursor())) {
            menu.add(0, SEARCH_FOR, 0, R.string.search_for);
        }
        mCurrentAlbumName = mAdapter.getCursor().getString(mAdapter.getCursor().getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM));
        mCurrentArtistNameForAlbum = mAdapter.getCursor().getString(mAdapter.getCursor().getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST));
        mCurrentTrackName = mAdapter.getCursor().getString(mAdapter.getCursor().getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE));
        menu.setHeaderTitle(mCurrentTrackName);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_NOW: {
                MusicUtils.queueAndPlayImmediately(getActivity(), mSelectedId);
                return true;
            }

            case PLAY_NEXT: {
                MusicUtils.queueNext(getActivity(), mSelectedId);
                return true;
            }

            case QUEUE: {
                MusicUtils.queue(getActivity(), new long[] { mSelectedId });
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(getActivity(), CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST_SINGLE);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), new long[] { mSelectedId }, playlist);
                return true;
            }

            case USE_AS_RINGTONE:
                // Set the system setting to make this the current ringtone
                MusicUtils.setRingtone(getActivity(), mSelectedId);
                return true;

            case DELETE_ITEM: {
                final long [] list = new long[1];
                list[0] = (int) mSelectedId;
                String f = getString(R.string.delete_song_desc);
                String desc = String.format(f, mCurrentTrackName);

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
                                MusicUtils.deleteTracks(TrackFragment.this.getActivity(), list);
                            }
                        }).show();
                return true;
            }

            case REMOVE_FROM_PLAYLIST:
                removePlaylistItem(mSelectedPosition);
                return true;

            case TRACK_INFO:
                TrackInfoFragment.showMe(getActivity(),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mSelectedId));
                return true;

            case SHARE_VIA:
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM,
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MusicUtils.getCurrentAudioId()));
                intent.setType(MusicUtils.getCurrentMimeType());
                startActivity(Intent.createChooser(intent,getResources().getString(R.string.share_via)));
                return true;

            case SEARCH_FOR:
                startActivity(Intent.createChooser(
                        MusicUtils.buildSearchForIntent(mCurrentTrackName, mCurrentArtistNameForAlbum, mCurrentAlbumName),
                        getString(R.string.mediasearch, mCurrentTrackName)));
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        mSelectedPosition = position;
        mAdapter.getCursor().moveToPosition(mSelectedPosition);
        try {
            int id_idx = mAdapter.getCursor().getColumnIndexOrThrow(
                    MediaStore.Audio.Playlists.Members.AUDIO_ID);
            mSelectedId = mAdapter.getCursor().getLong(id_idx);
        } catch (IllegalArgumentException ex) {
            mSelectedId = id;
        }

        MusicUtils.playSong(getActivity(), mSelectedId);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (mEditMode) {
            menu.add(0, SHUFFLE_PLAYLIST, 0, R.string.shuffle).setIcon(R.drawable.ic_menu_shuffle).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(0, UNIQUEIFY_PLAYLIST, 0, R.string.uniqueify).setIcon(R.drawable.ic_menu_uniqueify);
        }

        menu.add(0, PLAY_ALL, 0, R.string.play_all).setIcon(R.drawable.ic_menu_play_clip);
        menu.add(0, QUEUE_ALL, 0, R.string.queue_all).setIcon(R.drawable.btn_playback_ic_play_small);
        SubMenu interleave = menu.addSubMenu(0, INTERLEAVE_ALL, 0, R.string.interleave_all).setIcon(
                R.drawable.ic_menu_interleave);
        for (int i = 1; i<=5; i++) {
            for (int j = 1; j<=5; j++) {
                interleave.add(2, INTERLEAVE_ALL+10*i+j, 0, getResources().getString(R.string.interleaveNNN, i, j));
            }
        }

        menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist).setIcon(R.drawable.ic_menu_add);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(ADD_TO_PLAYLIST);
        if (item != null) {
            SubMenu sub = item.getSubMenu();
            MusicUtils.makePlaylistMenu(getActivity(), sub);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_ALL: {
                MusicUtils.playAll(getActivity(), MusicUtils.getSongListForCursor(mAdapter.getCursor()));
                return true;
            }

            case QUEUE_ALL: {
                MusicUtils.queue(getActivity(), MusicUtils.getSongListForCursor(mAdapter.getCursor()));
                return true;
            }

            case SHUFFLE_PLAYLIST: {
                Random random = new Random();
                long[] songs = MusicUtils.getSongListForCursor(mAdapter.getCursor());
                for (int i=0; i < songs.length; i++) {
                    int randomPosition = random.nextInt(songs.length);
                    MediaStore.Audio.Playlists.Members.moveItem(getActivity().getContentResolver(),
                            Long.valueOf(mPlaylist), i, randomPosition);
                }
                return true;
            }

            case UNIQUEIFY_PLAYLIST: {
                long[] songs = MusicUtils.getSongListForCursor(mAdapter.getCursor());
                Set<Long> found = new HashSet<Long>();
                for (int i = 0; i < songs.length; i++) {
                    if (!found.add(songs[i])) {
                        removePlaylistItem(i);
                    }
                }
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(getActivity(), CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST_ALL);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long [] list = MusicUtils.getSongListForCursor(mAdapter.getCursor());
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), list, playlist);
                return true;
            }

            default:
                if (item.getItemId() > INTERLEAVE_ALL && item.getItemId() != android.R.id.home) {
                    int currentCount = (item.getItemId() - INTERLEAVE_ALL) / 10;
                    int newCount = (item.getItemId() - INTERLEAVE_ALL) % 10;
                    long[] songs = MusicUtils.getSongListForCursor(mAdapter.getCursor());
                    MusicUtils.interleave(getActivity(), songs, currentCount, newCount);
                    return true;
                }

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case NEW_PLAYLIST_SINGLE:
                if (resultCode == Activity.RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long [] list = new long[] { mSelectedId };
                        MusicUtils.addToPlaylist(getActivity(), list, Integer.valueOf(uri.getLastPathSegment()));
                    }
                }
                break;

            case NEW_PLAYLIST_ALL:
                if (resultCode == Activity.RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long [] list = MusicUtils.getSongListForCursor(mAdapter.getCursor());
                        MusicUtils.addToPlaylist(getActivity(), list, Integer.parseInt(uri.getLastPathSegment()));
                    }
                }
                break;
        }
    }

    class TrackListAdapter extends SimpleCursorAdapter implements SectionIndexer {
        final boolean mDisableNowPlayingIndicator;

        int mTitleIdx;
        int mArtistIdx;
        int mDurationIdx;
        int mAudioIdIdx;

        private final StringBuilder mBuilder = new StringBuilder();
        private final String mUnknownArtist;

        private AlphabetIndexer mIndexer;

        class ViewHolder {
            TextView line1;
            TextView line2;
            TextView duration;
            ImageView play_indicator;
            CharArrayBuffer buffer1;
            char [] buffer2;
        }

        TrackListAdapter(Context context,
                int layout, Cursor cursor, String[] from, int[] to,
                boolean disablenowplayingindicator) {
            super(context, layout, cursor, from, to, 0);
            getColumnIndices(cursor);
            mDisableNowPlayingIndicator = disablenowplayingindicator;
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
        }

        @Override
        public Cursor swapCursor(Cursor c) {
            Cursor res = super.swapCursor(c);
            getColumnIndices(c);
            return res;
        }

        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
                mTitleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                mArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                mDurationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                try {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Playlists.Members.AUDIO_ID);
                } catch (IllegalArgumentException ex) {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                }

                if (mIndexer != null) {
                    mIndexer.setCursor(cursor);
                } else if (!TrackFragment.this.mEditMode && TrackFragment.this.mAlbumId == -1) {
                    String alpha = TrackFragment.this.getString(R.string.fast_scroll_alphabet);

                    mIndexer = new MusicAlphabetIndexer(cursor, mTitleIdx, alpha);
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = super.newView(context, cursor, parent);
            ImageView iv = (ImageView) v.findViewById(R.id.icon);
            iv.setVisibility(View.GONE);

            ViewHolder vh = new ViewHolder();
            vh.line1 = (TextView) v.findViewById(R.id.line1);
            vh.line2 = (TextView) v.findViewById(R.id.line2);
            vh.duration = (TextView) v.findViewById(R.id.duration);
            vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
            vh.buffer1 = new CharArrayBuffer(100);
            vh.buffer2 = new char[200];
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder vh = (ViewHolder) view.getTag();

            cursor.copyStringToBuffer(mTitleIdx, vh.buffer1);
            vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);

            int secs = cursor.getInt(mDurationIdx);
            if (secs == 0) {
                vh.duration.setText("");
            } else {
                vh.duration.setText(MusicUtils.formatDuration(context, secs));
            }

            final StringBuilder builder = mBuilder;
            builder.delete(0, builder.length());

            String name = cursor.getString(mArtistIdx);
            if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                builder.append(mUnknownArtist);
            } else {
                builder.append(name);
            }
            int len = builder.length();
            if (vh.buffer2.length < len) {
                vh.buffer2 = new char[len];
            }
            builder.getChars(0, len, vh.buffer2, 0);
            vh.line2.setText(vh.buffer2, 0, len);

            ImageView iv = vh.play_indicator;
            long id = -1;
            if (MusicUtils.sService != null) {
                id = MusicUtils.sService.getAudioId();
            }

            if (!mDisableNowPlayingIndicator && cursor.getLong(mAudioIdIdx) == id) {
                iv.setImageResource(R.drawable.indicator_ic_mp_playing_list);
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.GONE);
            }
        }

        // SectionIndexer methods

        public Object[] getSections() {
            if (mIndexer != null) {
                return mIndexer.getSections();
            } else {
                return null;
            }
        }

        public int getPositionForSection(int section) {
            if (mIndexer != null) {
                return mIndexer.getPositionForSection(section);
            } else {
                return 0;
            }
        }

        public int getSectionForPosition(int position) {
            return 0;
        }
    }

}
