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

import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;

import java.util.ArrayList;

public class PlaylistFragment extends CategoryFragment {
    private static final String LOGTAG = "PlaylistFragment";

    static final String[] cols = new String[] {
            MusicContract.Playlist._ID,
            MusicContract.Playlist.NAME,
            MusicContract.Playlist._COUNT
    };

    public static final String CATEGORY_ID = "playlist";

    private static final String CURRENT_PLAYLIST = "currentplaylist";
    private static final String CURRENT_PLAYLIST_NAME = "currentplaylistname";

    private static final int DELETE_PLAYLIST = CHILD_MENU_BASE + 1;
    private static final int EDIT_PLAYLIST = CHILD_MENU_BASE + 2;
    private static final int RENAME_PLAYLIST = CHILD_MENU_BASE + 3;
    private static final int CREATE_NEW_PLAYLIST = CHILD_MENU_BASE + 4;
    private static final int EXPORT_PLAYLIST = CHILD_MENU_BASE + 5;

    private static final long RECENTLY_ADDED_PLAYLIST = -1;
    private static final long ALL_SONGS_PLAYLIST = -2;

    private long mCurrentId;
    private String mPlaylistName;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mCurrentId = icicle.getLong(CURRENT_PLAYLIST);
            mPlaylistName = icicle.getString(CURRENT_PLAYLIST_NAME);
        }

        setHasOptionsMenu(true);
    }

    @Override
    protected CursorAdapter createListAdapter() {
        SimpleCursorAdapter listAdapter = new SimpleCursorAdapter(
                getActivity(),
                R.layout.track_list_item,
                null,
                new String[] { MusicContract.Playlist.NAME, MusicContract.Playlist._COUNT,
                               MusicContract.Playlist._ID, MusicContract.Playlist._ID },
                new int[] { R.id.line1, R.id.line2, R.id.play_indicator, R.id.icon },
                0);

        listAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                switch (view.getId()) {
                    case R.id.line2:
                        int numSongs = cursor.getInt(columnIndex);
                        ((TextView) view).setText(PlaylistFragment.this.getActivity().getResources()
                                .getQuantityString(R.plurals.Nsongs, numSongs, numSongs));
                        return true;

                    case R.id.play_indicator:
                        view.setVisibility(View.GONE);
                        return true;

                    case R.id.icon:
                        if (cursor.getLong(columnIndex) == RECENTLY_ADDED_PLAYLIST) {
                            ((ImageView)view).setImageResource(R.drawable.ic_mp_playlist_recently_added_list);
                        } else {
                            ((ImageView)view).setImageResource(R.drawable.ic_mp_playlist_list);
                        }
                        ViewGroup.LayoutParams p = view.getLayoutParams();
                        p.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                        p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        return true;

                    default:
                        return false;
                }
            }
        });

        return listAdapter;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(), MusicContract.Playlist.CONTENT_URI, cols,
                MusicContract.Playlist.NAME + " != ''", null,
                MusicContract.Playlist.NAME);
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
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;

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

        mAdapter.getCursor().moveToPosition(mi.position);
        mPlaylistName = mAdapter.getCursor().getString(mAdapter.getCursor().getColumnIndexOrThrow(MusicContract.Playlist.NAME));
        menu.setHeaderTitle(mPlaylistName);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_ALL:
                MusicUtils.playAll(getActivity(), fetchSongList(mCurrentId));
                return true;

            case QUEUE_ALL:
                MusicUtils.queue(getActivity(), fetchSongList(mCurrentId));
                return true;

            case DELETE_PLAYLIST:
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, mCurrentId);
                getActivity().getContentResolver().delete(uri, null, null);
                Toast.makeText(getActivity(), R.string.playlist_deleted_message, Toast.LENGTH_SHORT).show();
                if (mAdapter.getCursor().getCount() == 0) {
                    getActivity().setTitle(R.string.no_playlists_title);
                }
                return true;

            case EDIT_PLAYLIST:
                if (mCurrentId == RECENTLY_ADDED_PLAYLIST) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.weekpicker_title)
                            .setItems(R.array.weeklist, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                  int numweeks = which + 1;
                                  MusicUtils.setIntPref(PlaylistFragment.this.getActivity(), "numweeks", numweeks);
                                }
                            }).show();
                } else {
                    Log.e(LOGTAG, "should not be here");
                }
                return true;

            case RENAME_PLAYLIST: {
                View view = getActivity().getLayoutInflater().inflate(R.layout.rename_playlist, null);
                final EditText mPlaylist = (EditText)view.findViewById(R.id.playlist);
                final long playlistId = mCurrentId;

                if (playlistId >= 0 && mPlaylistName != null) {
                    mPlaylist.setText(mPlaylistName);
                    mPlaylist.setSelection(mPlaylistName.length());

                    new AlertDialog.Builder(getActivity())
                            .setTitle(String.format(PlaylistFragment.this.getString(R.string.rename_playlist_prompt),
                                    mPlaylistName))
                            .setView(view)
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .setPositiveButton(R.string.create_playlist_create_text, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    renamePlaylist(playlistId, mPlaylist.getText().toString());
                                }
                            }).show();
                }
                return true;
            }
            case EXPORT_PLAYLIST:
                new ExportPlaylistTask(getActivity().getApplicationContext()).execute(mPlaylistName, fetchSongList(mCurrentId));
                return true;

            default:
                if (item.getItemId() > INTERLEAVE_ALL) {
                    int currentCount = (item.getItemId() - INTERLEAVE_ALL) / 10;
                    int newCount = (item.getItemId() - INTERLEAVE_ALL) % 10;
                    MusicUtils.interleave(getActivity(), fetchSongList(mCurrentId), currentCount, newCount);
                    return true;
                }
        }
        return super.onContextItemSelected(item);
    }

    private void renamePlaylist(long playlistId, String name) {
        if (name != null && name.length() > 0) {
            if (idForPlaylist(name) >= 0) {
                Toast.makeText(getActivity(), R.string.playlist_already_exists, Toast.LENGTH_SHORT).show();
            } else {
                ContentValues values = new ContentValues(1);
                values.put(MediaStore.Audio.Playlists.NAME, name);
                getActivity().getContentResolver().update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                        values,
                        MediaStore.Audio.Playlists._ID + "=?",
                        new String[]{Long.valueOf(playlistId).toString()});

                Toast.makeText(getActivity(), R.string.playlist_renamed_message, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int idForPlaylist(String name) {
        Cursor c = MusicUtils.query(getActivity(), MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Playlists._ID },
                MediaStore.Audio.Playlists.NAME + "=?",
                new String[] { name },
                MediaStore.Audio.Playlists.NAME);
        int id = -1;
        if (c != null) {
            c.moveToFirst();
            if (!c.isAfterLast()) {
                id = c.getInt(0);
            }
            c.close();
        }
        return id;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        /*
        if (mCreateShortcut) {
            final Intent shortcut = new Intent();
            shortcut.setAction(Intent.ACTION_VIEW);
            shortcut.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/vnd.djdplayer.playlist");
            shortcut.putExtra(CATEGORY_ID, String.valueOf(id));

            final Intent intent = new Intent();
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, ((TextView) v.findViewById(R.id.line1)).getText());
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(
                    getActivity(), R.drawable.ic_launcher_shortcut_music_playlist));

            getActivity().setResult(Activity.RESULT_OK, intent);
            getActivity().finish();
            return;
        }
        */
        if (id == RECENTLY_ADDED_PLAYLIST) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/vnd.djdplayer.audio");
            intent.putExtra(CATEGORY_ID, "recentlyadded");
            startActivity(intent);
        } else {
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/vnd.djdplayer.audio");
            intent.putExtra(CATEGORY_ID, Long.valueOf(id).toString());
            startActivity(intent);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        menu.add(0, CREATE_NEW_PLAYLIST, 0, R.string.create_new_playlist).setIcon(R.drawable.ic_menu_add);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case CREATE_NEW_PLAYLIST:
                CreatePlaylist.showMe(getActivity(), null);
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
            int X = MusicUtils.getIntPref(getActivity(), "numweeks", 2) * (3600 * 24 * 7);
            final String[] ccols = new String[] { MediaStore.Audio.Media._ID};
            String where = MediaStore.MediaColumns.DATE_ADDED + ">" + (System.currentTimeMillis() / 1000 - X);
            return MusicUtils.query(getActivity(), MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    ccols, where, null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        } else if (playlistId == ALL_SONGS_PLAYLIST) {
            return MusicUtils.query(getActivity(), MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID}, MediaStore.Audio.Media.IS_MUSIC + "=1",
                    null, null);
        } else {
            final String[] ccols = new String[] { MediaStore.Audio.Playlists.Members.AUDIO_ID };
            return MusicUtils.query(getActivity(), MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                    ccols, null, null, MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
        }
    }

    private Cursor mergedCursor(Cursor c) {
        if (c == null) {
            return null;
        }
        if (c instanceof MergeCursor) {
            // this shouldn't happen, but fail gracefully
            Log.d("PlaylistFragment", "Already wrapped");
            return c;
        }
        MatrixCursor autoplaylistscursor = new MatrixCursor(cols);
        /*
        if (mCreateShortcut) {
            ArrayList<Object> all = new ArrayList<Object>(2);
            all.add(ALL_SONGS_PLAYLIST);
            all.add(getString(R.string.play_all));
            autoplaylistscursor.addRow(all);
        }
        */
        ArrayList<Object> recent = new ArrayList<Object>(2);
        recent.add(RECENTLY_ADDED_PLAYLIST);
        recent.add(getString(R.string.recentlyadded));
        autoplaylistscursor.addRow(recent);

        return new MergeCursor(new Cursor [] {autoplaylistscursor, c});
    }
}
