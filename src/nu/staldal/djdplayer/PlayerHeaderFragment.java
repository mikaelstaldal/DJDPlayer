/*
 * Copyright (C) 2014 Mikael Ståldal
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
import android.app.Fragment;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.TextUtils;
import android.view.*;
import android.widget.TextView;
import nu.staldal.djdplayer.provider.MusicContract;

public class PlayerHeaderFragment extends Fragment implements
        FragmentServiceConnection, View.OnTouchListener, View.OnLongClickListener, MusicUtils.Defs {
    private static final int ADD_TO_PLAYLIST2 = CHILD_MENU_BASE+4;
    private static final int USE_AS_RINGTONE2 = CHILD_MENU_BASE+5;
    private static final int DELETE_ITEM2 = CHILD_MENU_BASE+6;
    private static final int TRACK_INFO2 = CHILD_MENU_BASE+7;
    private static final int SHARE_VIA2 = CHILD_MENU_BASE+8;
    private static final int SEARCH_FOR2 = CHILD_MENU_BASE+9;
    private static final int NEW_PLAYLIST2 = CHILD_MENU_BASE+10;
    private static final int PLAYLIST_SELECTED2 = CHILD_MENU_BASE+11;

    private MediaPlaybackService service;

    private View mainView;
    private TextView trackNameView;
    private TextView artistNameView;
    private TextView genreNameView;

    private int mTouchSlop;
    private int mInitialX = -1;
    private int mLastX = -1;
    private int mTextWidth = 0;
    private int mViewWidth = 0;
    boolean mDraggingLabel = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.player_header, container, false);

        mainView = view.findViewById(R.id.player_header);

        trackNameView = (TextView) view.findViewById(R.id.trackname);
        artistNameView = (TextView) view.findViewById(R.id.artistname);
        genreNameView = (TextView) view.findViewById(R.id.genrename);

        trackNameView.setOnTouchListener(this);
        registerForContextMenu(trackNameView);

        artistNameView.setOnTouchListener(this);
        artistNameView.setOnLongClickListener(this);

        genreNameView.setOnTouchListener(this);
        genreNameView.setOnLongClickListener(this);

        mTouchSlop = ViewConfiguration.get(getActivity()).getScaledTouchSlop();
        
        return view;
    }

    @Override
    public void onServiceConnected(MediaPlaybackService s) {
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

        SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST2, 0, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub, NEW_PLAYLIST2, PLAYLIST_SELECTED2);

        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            menu.add(0, USE_AS_RINGTONE2, 0, R.string.ringtone_menu);
        }

        menu.add(0, DELETE_ITEM2, 0, R.string.delete_item);

        menu.add(0, TRACK_INFO2, 0, R.string.info);

        menu.add(0, SHARE_VIA2, 0, R.string.share_via);

        menu.add(0, SEARCH_FOR2, 0, R.string.search_for);

        menu.setHeaderTitle(service.getTrackName());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (service == null) return false;

        switch (item.getItemId()) {
            case NEW_PLAYLIST2: {
                CreatePlaylist.showMe(getActivity(), new long[] { MusicUtils.getCurrentAudioId() });
                return true;
            }

            case PLAYLIST_SELECTED2: {
                long [] list = new long[1];
                list[0] = MusicUtils.getCurrentAudioId();
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(getActivity(), list, playlist);
                return true;
            }

            case USE_AS_RINGTONE2:
                // Set the system setting to make this the current ringtone
                MusicUtils.setRingtone(getActivity(), service.getAudioId());
                return true;

            case DELETE_ITEM2: {
                final long [] list = new long[1];
                list[0] = MusicUtils.getCurrentAudioId();
                String f = getString(R.string.delete_song_desc, service.getTrackName());
                new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.delete_song_title)
                        .setMessage(f)
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setPositiveButton(R.string.delete_confirm_button_text, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MusicUtils.deleteTracks(PlayerHeaderFragment.this.getActivity(), list);
                            }
                        }).show();
                return true;
            }

            case TRACK_INFO2: {
                TrackInfoFragment.showMe(getActivity(),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MusicUtils.getCurrentAudioId()));

                return true;
            }

            case SHARE_VIA2: {
                startActivity(MusicUtils.shareVia(
                        MusicUtils.getCurrentAudioId(),
                        MusicUtils.getCurrentMimeType(),
                        getResources()));
                return true;
            }

            case SEARCH_FOR2:
                startActivity(MusicUtils.searchFor(
                        service.getTrackName(),
                        service.getArtistName(),
                        service.getAlbumName(),
                        getResources()));
                return true;
        }
        return super.onContextItemSelected(item);
    }

    private TextView textViewForContainer(View v) {
        View vv = v.findViewById(R.id.artistname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.genrename);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.trackname);
        if (vv != null) return (TextView) vv;
        return null;
    }
    
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        TextView tv = textViewForContainer(v);
        if (tv == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            v.setBackgroundColor(0xff606060);
            mInitialX = mLastX = (int) event.getX();
            mDraggingLabel = false;
        } else if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL) {
            v.setBackgroundColor(0);
            if (mDraggingLabel) {
                Message msg = mLabelScroller.obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(msg, 1000);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mDraggingLabel) {
                int scrollx = tv.getScrollX();
                int x = (int) event.getX();
                int delta = mLastX - x;
                if (delta != 0) {
                    mLastX = x;
                    scrollx += delta;
                    if (scrollx > mTextWidth) {
                        // scrolled the text completely off the view to the left
                        scrollx -= mTextWidth;
                        scrollx -= mViewWidth;
                    }
                    if (scrollx < -mViewWidth) {
                        // scrolled the text completely off the view to the right
                        scrollx += mViewWidth;
                        scrollx += mTextWidth;
                    }
                    tv.scrollTo(scrollx, 0);
                }
                return true;
            }
            int delta = mInitialX - (int) event.getX();
            if (Math.abs(delta) > mTouchSlop) {
                // start moving
                mLabelScroller.removeMessages(0, tv);
                
                // Only turn ellipsizing off when it's not already off, because it
                // causes the scroll position to be reset to 0.
                if (tv.getEllipsize() != null) {
                    tv.setEllipsize(null);
                }
                Layout ll = tv.getLayout();
                // layout might be null if the text just changed, or ellipsizing
                // was just turned off
                if (ll == null) {
                    return false;
                }
                // get the non-ellipsized line width, to determine whether scrolling
                // should even be allowed
                mTextWidth = (int) tv.getLayout().getLineWidth(0);
                mViewWidth = tv.getWidth();
                if (mViewWidth > mTextWidth) {
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    v.cancelLongPress();
                    return false;
                }
                mDraggingLabel = true;
                tv.setHorizontalFadingEdgeEnabled(true);
                v.cancelLongPress();
                return true;
            }
        }
        return false; 
    }

    final Handler mLabelScroller = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            TextView tv = (TextView) msg.obj;
            int x = tv.getScrollX();
            x = x * 3 / 4;
            tv.scrollTo(x, 0);
            if (x == 0) {
                tv.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                Message newmsg = obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(newmsg, 15);
            }
        }
    };
        
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
        if (service != null && service.getAudioId() != -1) {
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
        }
    }

    public void show() {
        mainView.setVisibility(View.VISIBLE);
    }

    public void hide() {
        mainView.setVisibility(View.GONE);
    }
}
