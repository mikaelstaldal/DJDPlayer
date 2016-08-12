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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AlphabetIndexer;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SectionIndexer;
import android.widget.TextView;
import nu.staldal.djdplayer.ExportPlaylistTask;
import nu.staldal.djdplayer.MusicAlphabetIndexer;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.SettingsActivity;
import nu.staldal.djdplayer.ShufflePlaylistTask;
import nu.staldal.djdplayer.provider.MusicContract;
import nu.staldal.ui.TouchInterceptor;
import nu.staldal.ui.WithSectionMenu;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TrackFragment extends BrowserFragment implements PopupMenu.OnMenuItemClickListener, WithSectionMenu {
    private static final String LOGTAG = "TrackFragment";

    private static final String CURRENT_COUNT = "currentCount";
    private static final String NEW_COUNT = "newCount";

    public static final String URI = "uri";

    int selectedPosition;
    long selectedId;

    private Uri uri;
    private boolean isPlaylist;
    private long playlist;
    private boolean isAlbum;
    private boolean isMedadataCategory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            selectedPosition = savedInstanceState.getInt("selectedposition");
            selectedId = savedInstanceState.getLong("selectedtrack");
        }

        String uriString = getArguments() != null ? getArguments().getString(URI) : null;
        if (uriString != null) {
            uri = Uri.parse(uriString);
            isPlaylist = uriString.startsWith(MusicContract.Playlist.CONTENT_URI.toString());
            playlist = isPlaylist ? ContentUris.parseId(uri) : -1;
            isAlbum = uriString.startsWith(MusicContract.Album.CONTENT_URI.toString());
            isMedadataCategory = uriString.startsWith(MusicContract.Artist.CONTENT_URI.toString())
                    || uriString.startsWith(MusicContract.Album.CONTENT_URI.toString())
                    || uriString.startsWith(MusicContract.Genre.CONTENT_URI.toString());
        } else {
            uri = MusicContract.CONTENT_URI;
            isPlaylist = false;
            playlist = -1;
            isAlbum = false;
            isMedadataCategory = false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ListView listView;

        if (isEditMode()) {
            listView = new TouchInterceptor(getActivity(), null);
            ((TouchInterceptor) listView).setDropListener((from, to) ->
                    MediaStore.Audio.Playlists.Members.moveItem(getActivity().getContentResolver(), playlist, from, to));
            ((TouchInterceptor) listView).setRemoveListener(this::removePlaylistItem);
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
    protected CursorAdapter createListAdapter() {
        return new TrackListAdapter(
                getActivity(), // need to use application context to avoid leaks
                isEditMode() ? R.layout.edit_track_list_item : R.layout.track_list_item,
                new String[]{},
                new int[]{});
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(), uri, null, null, null, null);
    }

    private void removePlaylistItem(int which) {
        View v = getListView().getChildAt(which - getListView().getFirstVisiblePosition());
        if (v != null) {
            v.setVisibility(View.GONE);
            getListView().invalidateViews();
        }
        removeItemFromPlaylist(which);
        if (v != null) {
            v.setVisibility(View.VISIBLE);
            getListView().invalidateViews();
        }
    }

    private void removeItemFromPlaylist(int which) {
        adapter.getCursor().moveToPosition(which);
        long itemId = adapter.getCursor().getLong(adapter.getCursor().getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members._ID));
        Log.d(LOGTAG, "Removing item " + itemId + " from playlist " + uri.toString());
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlist);
        int rowCount = getActivity().getContentResolver().delete(ContentUris.withAppendedId(uri, itemId), null, null);
        if (rowCount < 1) {
            Log.i(LOGTAG, "Unable to remove item " + itemId + " from playlist " + uri.toString());
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;

        AdapterView.AdapterContextMenuInfo mi = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        selectedPosition = mi.position;
        adapter.getCursor().moveToPosition(selectedPosition);
        try {
            int id_idx = adapter.getCursor().getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
            selectedId = adapter.getCursor().getLong(id_idx);
        } catch (IllegalArgumentException ex) {
            selectedId = mi.id;
        }

        menu.setHeaderTitle(adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(
                MediaStore.Audio.AudioColumns.TITLE)));

        menu.add(0, R.id.track_play_now, 0, R.string.play_now);
        menu.add(0, R.id.track_play_next, 0, R.string.play_next);
        menu.add(0, R.id.track_queue, 0, R.string.queue);

        SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub, R.id.track_new_playlist, R.id.track_selected_playlist);

        if (isEditMode()) {
            menu.add(0, R.id.track_remove_from_playlist, 0, R.string.remove_from_playlist);
        }

        if (!isEditMode()) {
            menu.add(0, R.id.track_delete, 0, R.string.delete_item);
        }

        menu.add(0, R.id.track_info, 0, R.string.info);

        menu.add(0, R.id.track_share_via, 0, R.string.share_via);

        // only add the 'search' menu if the selected item is music
        if (MusicUtils.isMusic(adapter.getCursor())) {
            menu.add(0, R.id.track_search_for_track, 0, R.string.search_for);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.track_play_now:
                MusicUtils.queueAndPlayImmediately(getActivity(), new long[]{selectedId});
                return true;

            case R.id.track_play_next:
                MusicUtils.queueNext(getActivity(), new long[]{selectedId});
                return true;

            case R.id.track_queue:
                MusicUtils.queue(getActivity(), new long[]{selectedId});
                return true;

            case R.id.track_new_playlist:
                CreatePlaylist.showMe(getActivity(), new long[]{selectedId});
                return true;

            case R.id.track_selected_playlist:
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), new long[]{selectedId}, playlist);
                return true;

            case R.id.track_delete: {
                final long[] list = new long[1];
                list[0] = (int) selectedId;
                String f = getString(R.string.delete_song_desc);
                String desc = String.format(f, adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(
                        MediaStore.Audio.AudioColumns.TITLE)));

                new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_song_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> { })
                        .setPositiveButton(R.string.delete_confirm_button_text, (dialog, which) ->
                                MusicUtils.deleteTracks(TrackFragment.this.getActivity(), list))
                        .show();
                return true;
            }

            case R.id.track_remove_from_playlist:
                removePlaylistItem(selectedPosition);
                return true;

            case R.id.track_info:
                TrackInfoFragment.showMe(getActivity(),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId));
                return true;

            case R.id.track_share_via:
                startActivity(MusicUtils.shareVia(
                        selectedId,
                        adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(
                                MediaStore.Audio.AudioColumns.MIME_TYPE)),
                        getResources()
                ));
                return true;

            case R.id.track_search_for_track:
                String currentTrackName = adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(
                        MediaStore.Audio.AudioColumns.TITLE));

                startActivity(MusicUtils.searchForTrack(
                        currentTrackName,
                        adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(
                                MediaStore.Audio.AudioColumns.ARTIST)),
                        adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(
                                MediaStore.Audio.AudioColumns.ALBUM)),
                        getResources()
                ));
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateSectionMenu(View view) {
        PopupMenu sectionMenu = new PopupMenu(getActivity(), view);
        sectionMenu.setOnMenuItemClickListener(this);
        Menu menu = sectionMenu.getMenu();

        if (isEditMode()) {
            menu.add(0, R.id.tracks_shuffle_playlist, 0, R.string.shuffleplaylist).setIcon(R.drawable.ic_menu_shuffle);
            menu.add(0, R.id.tracks_uniqueify_playlist, 0, R.string.uniqueifyplaylist).setIcon(R.drawable.ic_menu_uniqueify);
        }

        menu.add(0, R.id.tracks_play_all_now, 0, R.string.play_all_now).setIcon(R.drawable.ic_menu_play_clip);
        menu.add(0, R.id.tracks_play_all_next, 0, R.string.play_all_next).setIcon(R.drawable.ic_menu_play_clip);
        menu.add(0, R.id.tracks_queue_all, 0, R.string.queue_all).setIcon(R.drawable.ic_menu_play_clip);
        SubMenu interleave = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.interleave_all).setIcon(
                R.drawable.ic_menu_interleave);
        for (int i = 1; i <= 5; i++) {
            for (int j = 1; j <= 5; j++) {
                Intent intent = new Intent();
                intent.putExtra(CURRENT_COUNT, i);
                intent.putExtra(NEW_COUNT, j);
                interleave.add(2, R.id.tracks_interleave_all, 0, getResources().getString(R.string.interleaveNNN, i, j)).setIntent(intent);
            }
        }

        SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_all_to_playlist).setIcon(R.drawable.ic_menu_add);
        MusicUtils.makePlaylistMenu(getActivity(), sub, R.id.tracks_new_playlist, R.id.tracks_selected_playlist);

        if (!isPlaylist) {
            menu.add(0, R.id.tracks_delete_all, 0, R.string.delete_all).setIcon(R.drawable.ic_menu_delete);
        }

        CharSequence title = getActivity().getTitle();
        if (isMedadataCategory && title != null && !title.equals(MediaStore.UNKNOWN_STRING)) {
            menu.add(0, R.id.tracks_search_for_category, 0, R.string.search_for).setIcon(R.drawable.ic_menu_search);
        }

        if (playlist == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
            menu.add(0, R.id.tracks_edit_playlist, 0, R.string.edit_playlist_menu);
        }

        if (playlist >= 0) {
            menu.add(0, R.id.tracks_export_playlist, 0, R.string.export_playlist_menu);
            menu.add(0, R.id.tracks_share_playlist, 0, R.string.share_via);
        }

        sectionMenu.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.tracks_shuffle_playlist: {
                new ShufflePlaylistTask(getActivity().getApplicationContext()).execute(
                        playlist, MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case R.id.tracks_uniqueify_playlist: {
                long[] songs = MusicUtils.getSongListForCursor(adapter.getCursor());
                Set<Long> found = new HashSet<>();
                for (int i = 0; i < songs.length; i++) {
                    if (!found.add(songs[i])) {
                        removePlaylistItem(i);
                    }
                }
                return true;
            }

            case R.id.tracks_play_all_now: {
                MusicUtils.playAll(getActivity(), MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case R.id.tracks_play_all_next: {
                MusicUtils.queueNext(getActivity(), MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case R.id.tracks_queue_all: {
                MusicUtils.queue(getActivity(), MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case R.id.tracks_interleave_all: {
                Intent intent = item.getIntent();
                int currentCount = intent.getIntExtra(CURRENT_COUNT, 0);
                int newCount = intent.getIntExtra(NEW_COUNT, 0);
                long[] songs = MusicUtils.getSongListForCursor(adapter.getCursor());
                MusicUtils.interleave(getActivity(), songs, currentCount, newCount);
                return true;
            }

            case R.id.tracks_new_playlist: {
                CreatePlaylist.showMe(getActivity(), MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case R.id.tracks_selected_playlist: {
                long[] songs = MusicUtils.getSongListForCursor(adapter.getCursor());
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), songs, playlist);
                return true;
            }

            case R.id.tracks_delete_all: {
                final long[] songs = MusicUtils.getSongListForCursor(adapter.getCursor());
                String f = getString(R.string.delete_category_desc);
                String desc = String.format(f, getActivity().getTitle());
                new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_songs_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> { })
                        .setPositiveButton(R.string.delete_confirm_button_text, (dialog, which) ->
                                MusicUtils.deleteTracks(TrackFragment.this.getActivity(), songs))
                        .show();
                return true;
            }

            case R.id.tracks_search_for_category:
                startActivity(MusicUtils.searchForCategory(getActivity().getTitle(),
                        MediaStore.Audio.Media.CONTENT_TYPE, getResources()));
                return true;

            case R.id.tracks_edit_playlist:
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.weekpicker_title)
                        .setItems(R.array.weeklist, (dialog, which) -> {
                            int numweeks = which + 1;
                            MusicUtils.setIntPref(TrackFragment.this.getActivity(), SettingsActivity.NUMWEEKS,
                                    numweeks);
                            getLoaderManager().restartLoader(0, null, TrackFragment.this);
                        })
                        .show();
                return true;

            case R.id.tracks_export_playlist:
                new ExportPlaylistTask(getActivity().getApplicationContext()).execute(getActivity().getTitle(), playlist, false);
                return true;

            case R.id.tracks_share_playlist:
                new ExportPlaylistTask(getActivity().getApplicationContext()).execute(getActivity().getTitle(), playlist, true);
                return true;
        }
        return false;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        selectedPosition = position;
        adapter.getCursor().moveToPosition(selectedPosition);
        try {
            int id_idx = adapter.getCursor().getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
            selectedId = adapter.getCursor().getLong(id_idx);
        } catch (IllegalArgumentException ex) {
            selectedId = id;
        }

        if (isPicking()) {
            getActivity().setResult(Activity.RESULT_OK, new Intent().setData(
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId)));
            getActivity().finish();
        } else {
            MusicUtils.playSong(getActivity(), selectedId);
        }
    }


    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putInt("selectedposition", selectedPosition);
        outcicle.putLong("selectedtrack", selectedId);

        super.onSaveInstanceState(outcicle);
    }

    public boolean isEditMode() {
        return playlist >= 0;
    }

    class TrackListAdapter extends SimpleCursorAdapterWithContextMenu implements SectionIndexer {
        int titleIdx = -1;
        int artistIdx = -1;
        int durationIdx = -1;
        int audioIdIdx = -1;

        private final StringBuilder stringBuilder = new StringBuilder();
        private final String unknownArtistLabel;

        private AlphabetIndexer indexer;

        class ViewHolder {
            TextView line1;
            TextView line2;
            TextView duration;
            ImageView play_indicator;
            ImageView crossfade_indicator;
            CharArrayBuffer buffer1;
            char[] buffer2;
        }

        TrackListAdapter(Context context, int layout, String[] from, int[] to) {
            super(context, layout, null, from, to, 0);
            unknownArtistLabel = context.getString(R.string.unknown_artist_name);
        }

        @Override
        public Cursor swapCursor(Cursor c) {
            Cursor res = super.swapCursor(c);
            if (c != null) {
                getColumnIndices(c);
            }
            return res;
        }

        private void getColumnIndices(Cursor cursor) {
            try {
                titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE);
                artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST);
                durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION);

                audioIdIdx = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID);
                if (audioIdIdx < 0) {
                    audioIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID);
                }

                if (indexer != null) {
                    indexer.setCursor(cursor);
                } else if (!isEditMode() && !isAlbum) {
                    String alpha = getString(R.string.fast_scroll_alphabet);

                    indexer = new MusicAlphabetIndexer(cursor, titleIdx, alpha);
                }
            } catch (IllegalArgumentException e) {
                Log.w(LOGTAG, "Cursor does not contain expected columns, actually contains: "
                        + Arrays.toString(cursor.getColumnNames()) + " - " + e.toString());
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
            vh.crossfade_indicator = (ImageView) v.findViewById(R.id.crossfade_indicator);
            vh.buffer1 = new CharArrayBuffer(100);
            vh.buffer2 = new char[200];
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder vh = (ViewHolder) view.getTag();

            cursor.copyStringToBuffer(titleIdx, vh.buffer1);
            vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);

            int secs = cursor.getInt(durationIdx);
            if (secs == 0) {
                vh.duration.setText("");
            } else {
                vh.duration.setText(MusicUtils.formatDuration(context, secs));
            }

            final StringBuilder builder = stringBuilder;
            builder.delete(0, builder.length());

            String name = cursor.getString(artistIdx);
            if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                builder.append(unknownArtistLabel);
            } else {
                builder.append(name);
            }
            int len = builder.length();
            if (vh.buffer2.length < len) {
                vh.buffer2 = new char[len];
            }
            builder.getChars(0, len, vh.buffer2, 0);
            vh.line2.setText(vh.buffer2, 0, len);

            long audioId = cursor.getLong(audioIdIdx);

            long playingId = -1;
            if (MusicUtils.sService != null) {
                playingId = MusicUtils.sService.getAudioId();
            }

            long crossfadingId = -1;
            if (MusicUtils.sService != null) {
                crossfadingId = MusicUtils.sService.getCrossfadeAudioId();
            }

            if (audioId == playingId) {
                vh.play_indicator.setVisibility(View.VISIBLE);
                vh.crossfade_indicator.setVisibility(View.INVISIBLE);
            } else if (audioId == crossfadingId) {
                vh.play_indicator.setVisibility(View.INVISIBLE);
                vh.crossfade_indicator.setVisibility(View.VISIBLE);
            } else {
                vh.play_indicator.setVisibility(View.INVISIBLE);
                vh.crossfade_indicator.setVisibility(View.INVISIBLE);
            }
        }

        // SectionIndexer methods

        public Object[] getSections() {
            if (indexer != null) {
                return indexer.getSections();
            } else {
                return null;
            }
        }

        public int getPositionForSection(int section) {
            if (indexer != null) {
                return indexer.getPositionForSection(section);
            } else {
                return 0;
            }
        }

        public int getSectionForPosition(int position) {
            return 0;
        }
    }

}
