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
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;

public class PlaylistFragment extends CategoryFragment {
    private static final String LOGTAG = "PlaylistFragment";

    static final String[] cols = new String[]{
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

    private long currentId;
    private String playlistName;

    private boolean createShortcut;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            currentId = icicle.getLong(CURRENT_PLAYLIST);
            playlistName = icicle.getString(CURRENT_PLAYLIST_NAME);
        }

        createShortcut = (getActivity() instanceof PlaylistShortcutActivity);

        setHasOptionsMenu(true);
    }

    @Override
    protected CursorAdapter createListAdapter() {
        SimpleCursorAdapter listAdapter = new SimpleCursorAdapter(
                getActivity(),
                R.layout.track_list_item,
                null,
                new String[]{MusicContract.Playlist.NAME, MusicContract.Playlist._COUNT,
                        MusicContract.Playlist._ID, MusicContract.Playlist._ID},
                new int[]{R.id.line1, R.id.line2, R.id.play_indicator, R.id.icon},
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
                        if (cursor.getLong(columnIndex) == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
                            ((ImageView) view).setImageResource(R.drawable.ic_mp_playlist_recently_added_list);
                        } else {
                            ((ImageView) view).setImageResource(R.drawable.ic_mp_playlist_list);
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
        outcicle.putLong(CURRENT_PLAYLIST, currentId);
        outcicle.putString(CURRENT_PLAYLIST_NAME, playlistName);
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;

        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;
        currentId = mi.id;

        menu.add(0, PLAY_ALL, 0, R.string.play_all);
        menu.add(0, QUEUE_ALL, 0, R.string.queue_all);
        SubMenu interleave = menu.addSubMenu(0, INTERLEAVE_ALL, 0, R.string.interleave_all);
        for (int i = 1; i <= 5; i++) {
            for (int j = 1; j <= 5; j++) {
                interleave.add(2, INTERLEAVE_ALL + 10 * i + j, 0, getResources().getString(R.string.interleaveNNN, i, j));
            }
        }

        if (currentId >= 0) {
            menu.add(0, DELETE_PLAYLIST, 0, R.string.delete_playlist_menu);
            menu.add(0, RENAME_PLAYLIST, 0, R.string.rename_playlist_menu);
        }

        if (currentId == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
            menu.add(0, EDIT_PLAYLIST, 0, R.string.edit_playlist_menu);
        }

        menu.add(0, EXPORT_PLAYLIST, 0, R.string.export_playlist_menu);

        mAdapter.getCursor().moveToPosition(mi.position);
        playlistName = mAdapter.getCursor().getString(mAdapter.getCursor().getColumnIndexOrThrow(MusicContract.Playlist.NAME));
        menu.setHeaderTitle(playlistName);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_ALL:
                MusicUtils.playAll(getActivity(), fetchSongList(currentId));
                return true;

            case QUEUE_ALL:
                MusicUtils.queue(getActivity(), fetchSongList(currentId));
                return true;

            case DELETE_PLAYLIST:
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, currentId);
                getActivity().getContentResolver().delete(uri, null, null);
                Toast.makeText(getActivity(), R.string.playlist_deleted_message, Toast.LENGTH_SHORT).show();
                if (mAdapter.getCursor().getCount() == 0) {
                    getActivity().setTitle(R.string.no_playlists_title);
                }
                return true;

            case EDIT_PLAYLIST:
                if (currentId == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.weekpicker_title)
                            .setItems(R.array.weeklist, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    int numweeks = which + 1;
                                    MusicUtils.setIntPref(PlaylistFragment.this.getActivity(), "numweeks", numweeks);
                                    getLoaderManager().restartLoader(0, null, PlaylistFragment.this);
                                }
                            }).show();
                } else {
                    Log.e(LOGTAG, "should not be here");
                }
                return true;

            case RENAME_PLAYLIST: {
                View view = getActivity().getLayoutInflater().inflate(R.layout.rename_playlist, null);
                final EditText mPlaylist = (EditText) view.findViewById(R.id.playlist);
                final long playlistId = currentId;

                if (playlistId >= 0 && playlistName != null) {
                    mPlaylist.setText(playlistName);
                    mPlaylist.setSelection(playlistName.length());

                    new AlertDialog.Builder(getActivity())
                            .setTitle(String.format(PlaylistFragment.this.getString(R.string.rename_playlist_prompt),
                                    playlistName))
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
                new ExportPlaylistTask(getActivity().getApplicationContext()).execute(playlistName, fetchSongList(currentId));
                return true;

            default:
                if (item.getItemId() > INTERLEAVE_ALL) {
                    int currentCount = (item.getItemId() - INTERLEAVE_ALL) / 10;
                    int newCount = (item.getItemId() - INTERLEAVE_ALL) % 10;
                    MusicUtils.interleave(getActivity(), fetchSongList(currentId), currentCount, newCount);
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
                new String[]{MediaStore.Audio.Playlists._ID},
                MediaStore.Audio.Playlists.NAME + "=?",
                new String[]{name},
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
        if (createShortcut) {
            Intent shortcut = new Intent();
            shortcut.setAction(Intent.ACTION_VIEW);
            shortcut.setDataAndType(Uri.EMPTY, MimeTypes.DIR_DJDPLAYER_AUDIO);
            shortcut.putExtra(CATEGORY_ID, String.valueOf(id));

            Intent intent = new Intent();
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    mAdapter.getCursor().getString(mAdapter.getCursor().getColumnIndexOrThrow(MusicContract.Playlist.NAME)));
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(
                    getActivity(), R.drawable.ic_launcher_shortcut_music_playlist));

            getActivity().setResult(Activity.RESULT_OK, intent);
            getActivity().finish();
        } else {
            Intent intent = new Intent((id < 0) ? Intent.ACTION_VIEW : Intent.ACTION_EDIT);
            intent.setDataAndType(Uri.EMPTY, MimeTypes.DIR_DJDPLAYER_AUDIO);
            intent.putExtra(CATEGORY_ID, String.valueOf(id));
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
        if (playlistId == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
            // do a query for all songs added in the last X weeks
            int X = MusicUtils.getIntPref(getActivity(), "numweeks", 2) * (3600 * 24 * 7);
            final String[] ccols = new String[]{MediaStore.Audio.Media._ID};
            String where = MediaStore.MediaColumns.DATE_ADDED + ">" + (System.currentTimeMillis() / 1000 - X);
            return MusicUtils.query(getActivity(), MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    ccols, where, null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        } else if (playlistId == MusicContract.Playlist.ALL_SONGS_PLAYLIST) {
            return MusicUtils.query(getActivity(), MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID}, MediaStore.Audio.Media.IS_MUSIC + "=1",
                    null, null);
        } else {
            final String[] ccols = new String[]{MediaStore.Audio.Playlists.Members.AUDIO_ID};
            return MusicUtils.query(getActivity(), MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                    ccols, null, null, MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
        }
    }
}
