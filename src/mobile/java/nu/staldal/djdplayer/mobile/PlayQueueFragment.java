/*
 * Copyright (C) 2013-2016 Mikael Ståldal
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
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.PlayQueueCursor;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.SettingsActivity;
import nu.staldal.ui.TouchInterceptor;

public class PlayQueueFragment extends ListFragment
        implements FragmentServiceConnection, AbsListView.OnScrollListener {

    private static final String TAG = PlayQueueFragment.class.getSimpleName();

    private MediaPlayback service;

    PlayQueueCursor playQueueCursor;
    SimpleCursorAdapter listAdapter;
    boolean deletedOneRow;

    private boolean queueZoomed;
    private boolean listScrolled;

    int mSelectedPosition = -1;
    long mSelectedId = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mSelectedPosition = savedInstanceState.getInt("selectedposition", -1);
            mSelectedId = savedInstanceState.getLong("selectedtrack", -1);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        TouchInterceptor listView = new TouchInterceptor(getActivity(), null);
        listView.setId(android.R.id.list);
        listView.setFastScrollEnabled(true);
        listView.setDropListener((from, to) -> {
            playQueueCursor.moveItem(from, to);
            listAdapter.notifyDataSetChanged();
            getListView().invalidateViews();
            deletedOneRow = true;
        });
        listView.setRemoveListener(this::removePlaylistItem);
        listView.setDivider(null);
        listView.setSelector(R.drawable.list_selector_background);

        registerForContextMenu(listView);

        listScrolled = false;
        listView.setOnScrollListener(this);

        return listView;
    }

    @Override
    public void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MediaPlaybackService.META_CHANGED);
        filter.addAction(MediaPlaybackService.QUEUE_CHANGED);
        getActivity().registerReceiver(mNowPlayingListener, filter);
    }

    @Override
    public void onServiceConnected(MediaPlayback s) {
        service = s;
        playQueueCursor = new PlayQueueCursor(service, getActivity().getContentResolver());
        listAdapter = new SimpleCursorAdapterWithContextMenu(
                getActivity(),
                R.layout.edit_track_list_item,
                playQueueCursor,
                PlayQueueCursor.COLUMNS,
                new int[]{R.id.line1, R.id.line2, R.id.duration, R.id.play_indicator, R.id.crossfade_indicator},
                0);
        listAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            final String unknownArtist = PlayQueueFragment.this.getActivity().getString(R.string.unknown_artist_name);

            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                switch (view.getId()) {
                    case R.id.line2:
                        String name = cursor.getString(columnIndex);
                        if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                            ((TextView) view).setText(unknownArtist);
                        } else {
                            ((TextView) view).setText(name);
                        }

                        return true;

                    case R.id.duration:
                        int secs = cursor.getInt(columnIndex);
                        if (secs == 0) {
                            ((TextView) view).setText("");
                        } else {
                            ((TextView) view).setText(MusicUtils.formatDuration(PlayQueueFragment.this.getActivity(), secs));
                        }
                        return true;

                    case R.id.play_indicator:
                        if (service != null) {
                            int cursorPosition = cursor.getPosition();
                            if (cursorPosition == service.getQueuePosition()) {
                                view.setVisibility(View.VISIBLE);
                            } else {
                                view.setVisibility(View.INVISIBLE);
                            }
                            return true;
                        }

                    case R.id.crossfade_indicator:
                        if (service != null) {
                            int cursorPosition = cursor.getPosition();
                            if (cursorPosition == service.getCrossfadeQueuePosition()) {
                                view.setVisibility(View.VISIBLE);
                            } else {
                                view.setVisibility(View.INVISIBLE);
                            }
                            return true;
                        }

                    default:
                        return false;
                }

            }
        });
        setListAdapter(listAdapter);
    }

    public void setQueueZoomed(boolean queueZoomed) {
        this.queueZoomed = queueZoomed;
        if (!queueZoomed) listScrolled = false;
    }

    public boolean isQueueZoomed() {
        return queueZoomed;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (service != null) {
            String clickOnSong = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(
                    SettingsActivity.CLICK_ON_SONG, SettingsActivity.PLAY_NEXT);
            if (clickOnSong.equals(SettingsActivity.PLAY_NOW)) {
                service.setQueuePosition(position);
            } else {
                if (!service.isPlaying()) {
                    service.setQueuePosition(position);
                }
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(mNowPlayingListener);
        listScrolled = false;
    }

    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putInt("selectedposition", mSelectedPosition);
        outcicle.putLong("selectedtrack", mSelectedId);

        super.onSaveInstanceState(outcicle);
    }

    private boolean removePlaylistItem(int which) {
        View v = getListView().getChildAt(which - getListView().getFirstVisiblePosition());
        if (v == null) {
            Log.i(TAG, "No view when removing playlist item " + which);
            return false;
        }
        if (service != null && which != service.getQueuePosition()) {
            deletedOneRow = true;
        }
        v.setVisibility(View.GONE);
        getListView().invalidateViews();
        boolean ret = playQueueCursor.removeItem(which);
        listAdapter.notifyDataSetChanged();
        v.setVisibility(View.VISIBLE);
        getListView().invalidateViews();
        return ret;
    }

    private final BroadcastReceiver mNowPlayingListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // The service could disappear while the broadcast was in flight,
            // so check to see if it's still valid
            if (service == null) {
                return;
            }

            if (intent.getAction().equals(MediaPlaybackService.QUEUE_CHANGED)) {
                if (deletedOneRow) {
                    // This is the notification for a single row that was
                    // deleted previously, which is already reflected in the UI.
                    deletedOneRow = false;
                    return;
                }
                playQueueCursor.requery();
                listAdapter.notifyDataSetChanged();
            }

            getListView().invalidateViews();
            if (!listScrolled && !queueZoomed) getListView().setSelection(service.getQueuePosition() + 1);
        }
    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;
        AdapterView.AdapterContextMenuInfo mi = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        mSelectedPosition = mi.position;
        playQueueCursor.moveToPosition(mSelectedPosition);
        mSelectedId = playQueueCursor.getLong(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID));

        menu.add(0, R.id.playqueue_play_now, 0, R.string.play_now);

        SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub, R.id.playqueue_new_playlist, R.id.playqueue_selected_playlist);

        menu.add(0, R.id.playqueue_delete, 0, R.string.delete_item);

        menu.add(0, R.id.playqueue_info, 0, R.string.info);

        menu.add(0, R.id.playqueue_share_via, 0, R.string.share_via);

        // only add the 'search' menu if the selected item is music
        if (MusicUtils.isMusic(playQueueCursor)) {
            menu.add(0, R.id.playqueue_search_for, 0, R.string.search_for);
        }

        menu.setHeaderTitle(
                playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.playqueue_play_now: {
                if (service != null) service.setQueuePosition(mSelectedPosition);
                return true;
            }

            case R.id.playqueue_new_playlist: {
                CreatePlaylist.showMe(getActivity(), new long[]{mSelectedId});
                return true;
            }

            case R.id.playqueue_selected_playlist: {
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), new long[]{mSelectedId}, playlist);
                return true;
            }

            case R.id.playqueue_delete: {
                final long[] list = new long[1];
                list[0] = (int) mSelectedId;
                String f = getString(R.string.delete_song_desc);
                String desc = String.format(f,
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)));

                new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_song_title)
                        .setMessage(desc)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> {
                        })
                        .setPositiveButton(R.string.delete_confirm_button_text, (dialog, which) ->
                                MusicUtils.deleteTracks(PlayQueueFragment.this.getActivity(), list))
                        .show();
                return true;
            }

            case R.id.playqueue_info:
                TrackInfoFragment.showMe(getActivity(),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mSelectedId));

                return true;

            case R.id.playqueue_share_via:
                startActivity(MusicUtils.shareVia(
                        mSelectedId,
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE)),
                        getResources()));
                return true;

            case R.id.playqueue_search_for:
                startActivity(MusicUtils.searchForTrack(
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)),
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)),
                        playQueueCursor.getString(playQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM)),
                        getResources()));
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == SCROLL_STATE_TOUCH_SCROLL || scrollState == SCROLL_STATE_FLING)
            listScrolled = true;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    @Override
    public void onServiceDisconnected() {
        service = null;
    }

}
