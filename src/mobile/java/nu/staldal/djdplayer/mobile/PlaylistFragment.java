/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012-2016 Mikael Ståldal
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import nu.staldal.djdplayer.ExportPlaylistTask;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.SettingsActivity;
import nu.staldal.djdplayer.provider.MusicContract;

public class PlaylistFragment extends CategoryFragment {
    private static final String LOGTAG = "PlaylistFragment";

    private static final String[] cols = new String[] {
            MusicContract.Playlist._ID,
            MusicContract.Playlist.NAME,
            MusicContract.Playlist._COUNT
    };

    private static final String CURRENT_COUNT = "currentCount";
    private static final String NEW_COUNT = "newCount";

    private static final String CURRENT_PLAYLIST = "currentplaylist";
    private static final String CURRENT_PLAYLIST_NAME = "currentplaylistname";

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
    }

    @Override
    protected CursorAdapter createListAdapter() {
        SimpleCursorAdapter listAdapter = new SimpleCursorAdapterWithContextMenu(
                getActivity(),
                R.layout.track_list_item,
                null,
                new String[] { MusicContract.Playlist.NAME, MusicContract.Playlist._COUNT },
                new int[] { R.id.line1, R.id.line2 },
                0);

        listAdapter.setViewBinder((view, cursor, columnIndex) -> {
            switch (view.getId()) {
                case R.id.line2:
                    int numSongs = cursor.getInt(columnIndex);
                    ((TextView) view).setText(PlaylistFragment.this.getActivity().getResources()
                            .getQuantityString(R.plurals.Nsongs, numSongs, numSongs));
                    return true;

                default:
                    return false;
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
        adapter.getCursor().moveToPosition(mi.position);
        playlistName = adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(MusicContract.Playlist.NAME));
        menu.setHeaderTitle(playlistName);

        menu.add(0, R.id.playlist_play_all_now, 0, R.string.play_all_now);
        menu.add(0, R.id.playlist_play_all_next, 0, R.string.play_all_next);
        menu.add(0, R.id.playlist_queue_all, 0, R.string.queue_all);
        SubMenu interleave = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.interleave_all);
        for (int i = 1; i <= 5; i++) {
            for (int j = 1; j <= 5; j++) {
                Intent intent = new Intent();
                intent.putExtra(CURRENT_COUNT, i);
                intent.putExtra(NEW_COUNT, j);
                interleave.add(2, R.id.playlist_interleave_all, 0, getResources().getString(R.string.interleaveNNN, i, j)).setIntent(intent);
            }
        }

        SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_all_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub, R.id.playlist_new_playlist, R.id.playlist_selected_playlist);

        if (currentId >= 0) {
            menu.add(0, R.id.playlist_delete_playlist, 0, R.string.delete_playlist_menu);
            menu.add(0, R.id.playlist_rename_playlist, 0, R.string.rename_playlist_menu);
        }

        if (currentId == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
            menu.add(0, R.id.playlist_edit_playlist, 0, R.string.edit_playlist_menu);
        }

        if (currentId >= 0) {
            menu.add(0, R.id.playlist_export_playlist, 0, R.string.export_playlist_menu);
            menu.add(0, R.id.playlist_share_playlist, 0, R.string.share_via);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.playlist_play_all_now:
                MusicUtils.playAll(getActivity(), fetchSongList(currentId));
                return true;

            case R.id.playlist_play_all_next:
                MusicUtils.queueNext(getActivity(), fetchSongList(currentId));
                return true;

            case R.id.playlist_queue_all:
                MusicUtils.queue(getActivity(), fetchSongList(currentId));
                return true;

            case R.id.playlist_interleave_all:
                Intent intent = item.getIntent();
                int currentCount = intent.getIntExtra(CURRENT_COUNT, 0);
                int newCount = intent.getIntExtra(NEW_COUNT, 0);
                MusicUtils.interleave(getActivity(), fetchSongList(currentId), currentCount, newCount);
                return true;

            case R.id.playlist_new_playlist:
                CreatePlaylist.showMe(getActivity(), fetchSongList(currentId));
                return true;

            case R.id.playlist_selected_playlist: {
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), fetchSongList(currentId), playlist);
                return true;
            }

            case R.id.playlist_delete_playlist:
                String desc = String.format(getString(R.string.delete_playlist_desc),
                        adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(MusicContract.Playlist.NAME)));
                new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_playlist_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> { })
                        .setPositiveButton(R.string.delete_confirm_button_text, (dialog, which) -> {
                            Uri uri = ContentUris.withAppendedId(
                                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, currentId);
                            getActivity().getContentResolver().delete(uri, null, null);
                            Toast.makeText(getActivity(), R.string.playlist_deleted_message, Toast.LENGTH_SHORT).show();
                        })
                        .show();
                return true;

            case R.id.playlist_edit_playlist:
                if (currentId == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.weekpicker_title)
                            .setItems(R.array.weeklist, (dialog, which) -> {
                                int numweeks = which + 1;
                                MusicUtils.setIntPref(PlaylistFragment.this.getActivity(), SettingsActivity.NUMWEEKS,
                                        numweeks);
                                getLoaderManager().restartLoader(0, null, PlaylistFragment.this);
                            }).show();
                } else {
                    Log.e(LOGTAG, "should not be here");
                }
                return true;

            case R.id.playlist_rename_playlist: {
                @SuppressLint("InflateParams") View view = getActivity().getLayoutInflater().inflate(R.layout.rename_playlist, null);
                final EditText mPlaylist = (EditText) view.findViewById(R.id.playlist);
                final long playlistId = currentId;

                if (playlistId >= 0 && playlistName != null) {
                    mPlaylist.setText(playlistName);
                    mPlaylist.setSelection(playlistName.length());

                    new AlertDialog.Builder(getActivity())
                            .setTitle(String.format(PlaylistFragment.this.getString(R.string.rename_playlist_prompt),
                                    playlistName))
                            .setView(view)
                            .setNegativeButton(R.string.cancel, (dialog, which) -> { })
                            .setPositiveButton(R.string.create_playlist_create_text, (dialog, which) ->
                                    MusicUtils.renamePlaylist(getActivity(), playlistId, mPlaylist.getText().toString()))
                            .show();
                }
                return true;
            }

            case R.id.playlist_export_playlist:
                new ExportPlaylistTask(getActivity().getApplicationContext()).execute(playlistName, currentId, false);
                return true;

            case R.id.playlist_share_playlist:
                new ExportPlaylistTask(getActivity().getApplicationContext()).execute(playlistName, currentId, true);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (createShortcut) {
            Intent shortcut = new Intent();
            shortcut.setAction(Intent.ACTION_VIEW);
            shortcut.setData(MusicContract.Playlist.getMembersUri(id));

            Intent intent = new Intent();
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(MusicContract.Playlist.NAME)));
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(
                    getActivity(), R.drawable.ic_launcher_shortcut_music_playlist));

            getActivity().setResult(Activity.RESULT_OK, intent);
            getActivity().finish();
        } else {
            viewCategory(MusicContract.Playlist.getMembersUri(id));
        }
    }

    private long[] fetchSongList(long playlistId) {
        return MusicUtils.getSongListForCursorAndClose(MusicUtils.query(getActivity(),
                MusicContract.Playlist.getMembersUri(playlistId),
                null,
                null,
                null,
                null));
    }

}
