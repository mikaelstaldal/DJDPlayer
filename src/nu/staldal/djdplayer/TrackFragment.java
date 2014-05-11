/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012-2014 Mikael Ståldal
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
import android.content.pm.PackageManager;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.*;
import android.widget.*;
import nu.staldal.djdplayer.provider.MusicContract;
import nu.staldal.ui.TouchInterceptor;
import nu.staldal.ui.WithSectionMenu;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TrackFragment extends BrowserFragment implements MusicUtils.Defs, PopupMenu.OnMenuItemClickListener, WithSectionMenu {
    private static final String LOGTAG = "TrackFragment";

    private static final int REMOVE_FROM_PLAYLIST = CHILD_MENU_BASE + 2;
    private static final int TRACK_INFO = CHILD_MENU_BASE + 3;
    private static final int SEARCH_FOR = CHILD_MENU_BASE + 4;

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
            ((TouchInterceptor) listView).setDropListener(new TouchInterceptor.DropListener() {
                public void drop(int from, int to) {
                    MediaStore.Audio.Playlists.Members.moveItem(getActivity().getContentResolver(),
                            playlist, from, to);
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

    private boolean removePlaylistItem(int which) {
        View v = getListView().getChildAt(which - getListView().getFirstVisiblePosition());
        if (v == null) {
            Log.d(LOGTAG, "No view when removing playlist item " + which);
            return false;
        }
        v.setVisibility(View.GONE);
        getListView().invalidateViews();
        int colidx = adapter.getCursor().getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members._ID);
        adapter.getCursor().moveToPosition(which);
        long id = adapter.getCursor().getLong(colidx);
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlist);
        boolean ret = getActivity().getContentResolver().delete(ContentUris.withAppendedId(uri, id), null, null) > 0;
        v.setVisibility(View.VISIBLE);
        getListView().invalidateViews();
        return ret;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;

        menu.setHeaderTitle(adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(
                MediaStore.Audio.AudioColumns.TITLE)));

        menu.add(0, PLAY_NOW, 0, R.string.play_now);
        menu.add(0, PLAY_NEXT, 0, R.string.play_next);
        menu.add(0, QUEUE, 0, R.string.queue);
        SubMenu sub = menu.addSubMenu(0, Menu.NONE, 0, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub);
        if (isEditMode()) {
            menu.add(0, REMOVE_FROM_PLAYLIST, 0, R.string.remove_from_playlist);
        }
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu);
        }
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);
        AdapterView.AdapterContextMenuInfo mi = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        selectedPosition = mi.position;
        adapter.getCursor().moveToPosition(selectedPosition);
        try {
            int id_idx = adapter.getCursor().getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
            selectedId = adapter.getCursor().getLong(id_idx);
        } catch (IllegalArgumentException ex) {
            selectedId = mi.id;
        }

        menu.add(0, TRACK_INFO, 0, R.string.info);

        menu.add(0, SHARE_VIA, 0, R.string.share_via);

        // only add the 'search' menu if the selected item is music
        if (MusicUtils.isMusic(adapter.getCursor())) {
            menu.add(0, SEARCH_FOR, 0, R.string.search_for);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_NOW: {
                MusicUtils.queueAndPlayImmediately(getActivity(), new long[]{selectedId});
                return true;
            }

            case PLAY_NEXT: {
                MusicUtils.queueNext(getActivity(), new long[]{selectedId});
                return true;
            }

            case QUEUE: {
                MusicUtils.queue(getActivity(), new long[]{selectedId});
                return true;
            }

            case NEW_PLAYLIST: {
                CreatePlaylist.showMe(getActivity(), new long[]{selectedId});
                return true;
            }

            case PLAYLIST_SELECTED: {
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), new long[]{selectedId}, playlist);
                return true;
            }

            case USE_AS_RINGTONE:
                // Set the system setting to make this the current ringtone
                MusicUtils.setRingtone(getActivity(), selectedId);
                return true;

            case DELETE_ITEM: {
                final long[] list = new long[1];
                list[0] = (int) selectedId;
                String f = getString(R.string.delete_song_desc);
                String desc = String.format(f, adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(
                        MediaStore.Audio.AudioColumns.TITLE)));

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
                removePlaylistItem(selectedPosition);
                return true;

            case TRACK_INFO:
                TrackInfoFragment.showMe(getActivity(),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId));
                return true;

            case SHARE_VIA:
                startActivity(MusicUtils.shareVia(
                        selectedId,
                        adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(
                                MediaStore.Audio.AudioColumns.MIME_TYPE)),
                        getResources()
                ));
                return true;

            case SEARCH_FOR:
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
            menu.add(0, SHUFFLE_PLAYLIST, 0, R.string.shuffleplaylist).setIcon(R.drawable.ic_menu_shuffle);
            menu.add(0, UNIQUEIFY_PLAYLIST, 0, R.string.uniqueifyplaylist).setIcon(R.drawable.ic_menu_uniqueify);
        }

        menu.add(0, PLAY_ALL_NOW, 0, R.string.play_all_now).setIcon(R.drawable.ic_menu_play_clip);
        menu.add(0, PLAY_ALL_NEXT, 0, R.string.play_all_next).setIcon(R.drawable.ic_menu_play_clip);
        menu.add(0, QUEUE_ALL, 0, R.string.queue_all).setIcon(R.drawable.btn_playback_ic_play_small);
        SubMenu interleave = menu.addSubMenu(0, INTERLEAVE_ALL, 0, R.string.interleave_all).setIcon(
                R.drawable.ic_menu_interleave);
        for (int i = 1; i <= 5; i++) {
            for (int j = 1; j <= 5; j++) {
                interleave.add(2, INTERLEAVE_ALL + 10 * i + j, 0, getResources().getString(R.string.interleaveNNN, i, j));
            }
        }

        if (!isPlaylist) {
            SubMenu sub = menu.addSubMenu(0, Menu.NONE, 0, R.string.add_all_to_playlist).setIcon(R.drawable.ic_menu_add);
            MusicUtils.makePlaylistMenu(getActivity(), sub, NEW_PLAYLIST_ALL, PLAYLIST_SELECTED_ALL);

            menu.add(0, DELETE_ALL, 0, R.string.delete_all).setIcon(R.drawable.ic_menu_delete);
        }

        CharSequence title = getActivity().getTitle();
        if (isMedadataCategory && title != null && !title.equals(MediaStore.UNKNOWN_STRING)) {
            menu.add(0, SEARCH_FOR_CATEGORY, 0, R.string.search_for).setIcon(R.drawable.ic_menu_search);
        }

        if (playlist == MusicContract.Playlist.RECENTLY_ADDED_PLAYLIST) {
            menu.add(0, EDIT_PLAYLIST, 0, R.string.edit_playlist_menu);
        }

        if (playlist >= 0) {
            menu.add(0, EXPORT_PLAYLIST, 0, R.string.export_playlist_menu);
        }

        sectionMenu.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case SHUFFLE_PLAYLIST: {
                new ShufflePlaylistTask(getActivity().getApplicationContext()).execute(
                        playlist, MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case UNIQUEIFY_PLAYLIST: {
                long[] songs = MusicUtils.getSongListForCursor(adapter.getCursor());
                Set<Long> found = new HashSet<>();
                for (int i = 0; i < songs.length; i++) {
                    if (!found.add(songs[i])) {
                        removePlaylistItem(i);
                    }
                }
                return true;
            }

            case PLAY_ALL_NOW: {
                MusicUtils.playAll(getActivity(), MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case PLAY_ALL_NEXT: {
                MusicUtils.queueNext(getActivity(), MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case QUEUE_ALL: {
                MusicUtils.queue(getActivity(), MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case NEW_PLAYLIST_ALL: {
                CreatePlaylist.showMe(getActivity(), MusicUtils.getSongListForCursor(adapter.getCursor()));
                return true;
            }

            case PLAYLIST_SELECTED_ALL: {
                long[] songs = MusicUtils.getSongListForCursor(adapter.getCursor());
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), songs, playlist);
                return true;
            }

            case DELETE_ALL: {
                final long[] songs = MusicUtils.getSongListForCursor(adapter.getCursor());
                String f = getString(R.string.delete_category_desc);
                String desc = String.format(f, getActivity().getTitle());
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
                                MusicUtils.deleteTracks(TrackFragment.this.getActivity(), songs);
                            }
                        }).show();
                return true;
            }

            case SEARCH_FOR_CATEGORY:
                startActivity(MusicUtils.searchForCategory(getActivity().getTitle(),
                        MediaStore.Audio.Media.CONTENT_TYPE, getResources()));
                return true;

            case EDIT_PLAYLIST:
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.weekpicker_title)
                        .setItems(R.array.weeklist, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                int numweeks = which + 1;
                                MusicUtils.setIntPref(TrackFragment.this.getActivity(), SettingsActivity.NUMWEEKS,
                                        numweeks);
                                getLoaderManager().restartLoader(0, null, TrackFragment.this);
                            }
                        }).show();
                return true;

            case EXPORT_PLAYLIST:
                new ExportPlaylistTask(getActivity().getApplicationContext()).execute(getActivity().getTitle(), playlist);
                return true;

            default:
                if (item.getItemId() > INTERLEAVE_ALL && item.getItemId() != android.R.id.home) {
                    int currentCount = (item.getItemId() - INTERLEAVE_ALL) / 10;
                    int newCount = (item.getItemId() - INTERLEAVE_ALL) % 10;
                    long[] songs = MusicUtils.getSongListForCursor(adapter.getCursor());
                    MusicUtils.interleave(getActivity(), songs, currentCount, newCount);
                    return true;
                }
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

    class TrackListAdapter extends SimpleCursorAdapter implements SectionIndexer {
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

            ImageView iv = vh.play_indicator;
            long id = -1;
            if (MusicUtils.sService != null) {
                id = MusicUtils.sService.getAudioId();
            }

            if (cursor.getLong(audioIdIdx) == id) {
                iv.setImageResource(R.drawable.indicator_ic_mp_playing_list);
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.GONE);
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
