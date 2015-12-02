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

package nu.staldal.djdplayer.mobile;

import android.app.AlertDialog;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.provider.MusicContract;

import java.io.File;

public class FolderFragment extends CategoryFragment {
    static final String[] cols = new String[] {
            MusicContract.Folder.NAME,
            MusicContract.Folder._COUNT,
            MusicContract.Folder.PATH,
            MusicContract.Folder._ID,
    };

    private static final String CURRENT_FOLDER = "currentfolder";

    private String mCurrentFolder;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mCurrentFolder = icicle.getString(CURRENT_FOLDER);
        }
    }

    @Override
    protected CursorAdapter createListAdapter() {
        SimpleCursorAdapter listAdapter = new SimpleCursorAdapterWithContextMenu(
                getActivity(),
                R.layout.track_list_item,
                null,
                cols,
                new int[] { R.id.line1, R.id.line2, R.id.play_indicator },
                0);

        listAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                switch (view.getId()) {
                    case R.id.line2:
                        int numSongs = cursor.getInt(columnIndex);
                        ((TextView) view).setText(FolderFragment.this.getActivity().getResources()
                                .getQuantityString(R.plurals.Nsongs, numSongs, numSongs));
                        return true;

                    case R.id.play_indicator:
                        String folder = cursor.getString(columnIndex);

                        File currentFolder = (MusicUtils.sService != null)
                                ? MusicUtils.sService.getFolder()
                                : null;

                        if (currentFolder != null && currentFolder.getAbsolutePath().equals(folder)) {
                            view.setVisibility(View.VISIBLE);
                        } else {
                            view.setVisibility(View.INVISIBLE);
                        }
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
        return new CursorLoader(getActivity(), MusicContract.Folder.CONTENT_URI, cols, null, null, null);
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putString(CURRENT_FOLDER, mCurrentFolder);
        super.onSaveInstanceState(outcicle);
    }

    private long[] fetchSongList(String folder) {
        Cursor cursor = MusicUtils.query(getActivity(),
                MusicContract.Folder.getMembersUri(folder),
                new String[] { MediaStore.Audio.AudioColumns._ID },
                null,
                null,
                null);

        if (cursor != null) {
            long [] list = MusicUtils.getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return MusicUtils.sEmptyList;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        adapter.getCursor().moveToPosition(position);
        String path = adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(MusicContract.Folder.PATH));
        viewCategory(MusicContract.Folder.getMembersUri(path));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;

        AdapterView.AdapterContextMenuInfo mi = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        adapter.getCursor().moveToPosition(mi.position);
        mCurrentFolder = adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(MusicContract.Folder.PATH));
        String title = adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(MusicContract.Folder.NAME));
        menu.setHeaderTitle(title);

        menu.add(0, PLAY_ALL_NOW, 0, R.string.play_all_now);
        menu.add(0, PLAY_ALL_NEXT, 0, R.string.play_all_next);
        menu.add(0, QUEUE_ALL, 0, R.string.queue_all);
        SubMenu interleave = menu.addSubMenu(Menu.NONE, INTERLEAVE_ALL, Menu.NONE, R.string.interleave_all);
        for (int i = 1; i<=5; i++) {
            for (int j = 1; j<=5; j++) {
                interleave.add(2, INTERLEAVE_ALL+10*i+j, 0,
                        getResources().getString(R.string.interleaveNNN, i, j));
            }
        }

        SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_all_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub);

        menu.add(0, DELETE_ALL, 0, R.string.delete_all);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_ALL_NOW: {
                MusicUtils.playAll(getActivity(), fetchSongList(mCurrentFolder));
                return true;
            }

            case PLAY_ALL_NEXT: {
                MusicUtils.queueNext(getActivity(), fetchSongList(mCurrentFolder));
                return true;
            }

            case QUEUE_ALL: {
                MusicUtils.queue(getActivity(), fetchSongList(mCurrentFolder));
                return true;
            }

            case NEW_PLAYLIST: {
                CreatePlaylist.showMe(getActivity(), fetchSongList(mCurrentFolder));
                return true;
            }

            case PLAYLIST_SELECTED: {
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), fetchSongList(mCurrentFolder), playlist);
                return true;
            }

            case DELETE_ALL: {
                final long [] list = fetchSongList(mCurrentFolder);
                String f = getString(R.string.delete_folder_desc);
                String desc = String.format(f, mCurrentFolder);

                new AlertDialog.Builder(getActivity())
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
                                MusicUtils.deleteTracks(FolderFragment.this.getActivity(), list);
                            }
                        }).show();
                return true;
            }

            default:
                if (item.getItemId() > INTERLEAVE_ALL) {
                    int currentCount = (item.getItemId() - INTERLEAVE_ALL) / 10;
                    int newCount = (item.getItemId() - INTERLEAVE_ALL) % 10;
                    long[] songs = fetchSongList(mCurrentFolder);
                    MusicUtils.interleave(getActivity(), songs, currentCount, newCount);
                    return true;
                }

        }
        return super.onContextItemSelected(item);
    }
}
