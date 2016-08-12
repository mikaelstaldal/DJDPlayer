/*
 * Copyright (C) 2014-2016 Mikael Ståldal
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
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.provider.MusicContract;

public class PlayerHeaderFragment extends Fragment implements FragmentServiceConnection, View.OnLongClickListener {

    @SuppressWarnings("unused")
    private static final String LOGTAG = "PlayerHeaderFragment";

    private MediaPlayback service;

    private View mainView;
    private TextView trackNameView;
    private TextView artistNameView;
    private TextView genreNameView;
    private View trackNameContainer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.player_header, container, false);

        mainView = view.findViewById(R.id.player_header);

        trackNameView = (TextView) view.findViewById(R.id.trackname);
        artistNameView = (TextView) view.findViewById(R.id.artistname);
        genreNameView = (TextView) view.findViewById(R.id.genrename);
        trackNameContainer = view.findViewById(R.id.trackname_container);

        registerForContextMenu(trackNameContainer);
        artistNameView.setOnLongClickListener(this);
        genreNameView.setOnLongClickListener(this);

        view.findViewById(R.id.context_menu).setOnClickListener(v -> trackNameContainer.showContextMenu());

        return view;
    }

    @Override
    public void onServiceConnected(MediaPlayback s) {
        service = s;

        update();
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MediaPlaybackService.META_CHANGED);
        filter.addAction(MediaPlaybackService.QUEUE_CHANGED);
        getActivity().registerReceiver(mStatusListener, filter);
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(mStatusListener);

        super.onPause();
    }

    @Override
    public void onServiceDisconnected() {
        service = null;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (service == null) return;

        SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub, R.id.playerheader_new_playlist, R.id.playerheader_selected_playlist);

        menu.add(0, R.id.playerheader_delete, 0, R.string.delete_item);

        menu.add(0, R.id.playerheader_info, 0, R.string.info);

        menu.add(0, R.id.playerheader_share_via, 0, R.string.share_via);

        menu.add(0, R.id.playerheader_search_for, 0, R.string.search_for);

        menu.setHeaderTitle(service.getTrackName());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (service == null) return false;

        switch (item.getItemId()) {
            case R.id.playerheader_new_playlist:
                CreatePlaylist.showMe(getActivity(), new long[] { service.getAudioId() });
                return true;

            case R.id.playerheader_selected_playlist: {
                long [] list = new long[1];
                list[0] = service.getAudioId();
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), list, playlist);
                return true;
            }

            case R.id.playerheader_delete: {
                final long [] list = new long[1];
                list[0] = service.getAudioId();
                String f = getString(R.string.delete_song_desc, service.getTrackName());
                new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_song_title)
                        .setMessage(f)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> { })
                        .setPositiveButton(R.string.delete_confirm_button_text, (dialog, which) ->
                                MusicUtils.deleteTracks(PlayerHeaderFragment.this.getActivity(), list))
                        .show();
                return true;
            }

            case R.id.playerheader_info:
                TrackInfoFragment.showMe(getActivity(),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, service.getAudioId()));
                return true;

            case R.id.playerheader_share_via: {
                startActivity(MusicUtils.shareVia(
                        service.getAudioId(),
                        service.getMimeType(),
                        getResources()));
                return true;
            }

            case R.id.playerheader_search_for:
                startActivity(MusicUtils.searchForTrack(
                        service.getTrackName(),
                        service.getArtistName(),
                        service.getAlbumName(),
                        getResources()));
                return true;
        }
        return super.onContextItemSelected(item);
    }
    public boolean onLongClick(View view) {
        if (service == null) return true;

        long audioId = service.getAudioId();
        long artistId = service.getArtistId();
        long genreId = service.getGenreId();
        String song = service.getTrackName();
        String artist = service.getArtistName();
        String album = service.getAlbumName();

        if (audioId < 0) {
            return false;
        }

        if (MediaStore.UNKNOWN_STRING.equals(album) &&
                MediaStore.UNKNOWN_STRING.equals(artist) &&
                song != null &&
                song.startsWith("recording")) {
            // not music
            return false;
        }

        if (view.equals(artistNameView)) {
            Intent intent = new Intent(getActivity(), MusicBrowserActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(MusicContract.Artist.getMembersUri(artistId));
            startActivity(intent);
            if (!(getActivity() instanceof MusicBrowserActivity)) getActivity().finish();
            return true;
        } else if (view.equals(genreNameView)) {
            Intent intent = new Intent(getActivity(), MusicBrowserActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(MusicContract.Genre.getMembersUri(genreId));
            startActivity(intent);
            if (!(getActivity() instanceof MusicBrowserActivity)) getActivity().finish();
            return true;
        } else {
            throw new RuntimeException("shouldn't be here");
        }
    }

    private final BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }
    };

    private void update() {
        if (service == null) return;

        if (service.getAudioId() != -1) {
            String trackName = service.getTrackName();
            trackNameView.setText(trackName);
            String artistName = service.getArtistName();
            if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                artistName = getString(R.string.unknown_artist_name);
            }
            artistNameView.setText(artistName);
            String genreName = service.getGenreName();
            if (MediaStore.UNKNOWN_STRING.equals(genreName)) {
                genreName = getString(R.string.unknown_genre_name);
            }
            genreNameView.setText(genreName);
        } else {
            trackNameView.setText("");
            artistNameView.setText("");
            genreNameView.setText("");
        }
    }

    public void show() {
        mainView.setVisibility(View.VISIBLE);
    }

    public void hide() {
        mainView.setVisibility(View.GONE);
    }
}
