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
import android.app.KeyguardManager;
import android.app.SearchManager;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.widget.SeekBar.OnSeekBarChangeListener;


public class MediaPlaybackActivity extends Activity
        implements MusicUtils.Defs, View.OnTouchListener, View.OnLongClickListener {
    private static final int USE_AS_RINGTONE = CHILD_MENU_BASE;
    private static final int TRACK_INFO = CHILD_MENU_BASE+1;

    private boolean mSeeking = false;
    private boolean mDeviceHasDpad;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private MediaPlaybackService mService = null;
    private RepeatingImageButton mPrevButton;
    private ImageButton mPauseButton;
    private RepeatingImageButton mNextButton;
    private ImageButton mRepeatButton;
    private ImageButton mShuffleButton;
    private ImageButton mQueueButton;
    private ImageButton mLibraryButton;
    private Toast mToast;
    private int mTouchSlop;
    private MusicUtils.ServiceToken mToken;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setContentView(R.layout.audio_player);

        mCurrentTime = (TextView) findViewById(R.id.currenttime);
        mTotalTime = (TextView) findViewById(R.id.totaltime);
        mProgress = (ProgressBar) findViewById(android.R.id.progress);
        mArtistName = (TextView) findViewById(R.id.artistname);
        mGenreName = (TextView) findViewById(R.id.genrename);
        mTrackName = (TextView) findViewById(R.id.trackname);
        mNextTrackName = (TextView) findViewById(R.id.nexttrackname);
        mNextArtistName = (TextView) findViewById(R.id.nextartistname);
        mNextGenreName = (TextView) findViewById(R.id.nextgenrename);

        mArtistName.setOnTouchListener(this);
        mArtistName.setOnLongClickListener(this);

        mGenreName.setOnTouchListener(this);
        mGenreName.setOnLongClickListener(this);

        mTrackName.setOnTouchListener(this);
        mTrackName.setOnLongClickListener(this);

        mNextTrackName.setOnTouchListener(this);
        mNextTrackName.setOnLongClickListener(this);

        mNextArtistName.setOnTouchListener(this);
        mNextArtistName.setOnLongClickListener(this);

        mNextGenreName.setOnTouchListener(this);
        mNextGenreName.setOnLongClickListener(this);

        mPrevButton = (RepeatingImageButton) findViewById(R.id.prev);
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mService == null) return;
                if (mService.position() < 2000) {
                    mService.prev();
                } else {
                    mService.seek(0);
                    mService.play();
                }
            }
        });
        mPrevButton.setRepeatListener(new RepeatingImageButton.RepeatListener() {
            public void onRepeat(View v, long howlong, int repcnt) {
                scanBackward(repcnt, howlong);
            }
        }, 260);
        mPauseButton = (ImageButton) findViewById(R.id.pause);
        mPauseButton.requestFocus();
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doPauseResume();
            }
        });
        mNextButton = (RepeatingImageButton) findViewById(R.id.next);
        mNextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mService == null) return;
                mService.next(true);
            }
        });
        mNextButton.setRepeatListener(new RepeatingImageButton.RepeatListener() {
            public void onRepeat(View v, long howlong, int repcnt) {
                scanForward(repcnt, howlong);
            }
        }, 260);
        seekmethod = 1;

        mDeviceHasDpad = (getResources().getConfiguration().navigation ==
            Configuration.NAVIGATION_DPAD);
        
        mQueueButton = (ImageButton) findViewById(R.id.curplaylist);
        mQueueButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(
                        new Intent(Intent.ACTION_EDIT)
                        .setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/vnd.djdplayer.audio")
                        .putExtra("playlist", TrackBrowserActivity.PLAYQUEUE)
                );
            }
        });

        mShuffleButton = ((ImageButton) findViewById(R.id.shuffle));
        mShuffleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                shuffle();
            }
        });

        mRepeatButton = ((ImageButton) findViewById(R.id.repeat));
        mRepeatButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                cycleRepeat();
            }
        });
        mLibraryButton = ((ImageButton) findViewById(R.id.library));
        mLibraryButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MediaPlaybackActivity.this, MusicBrowserActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });

        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);

        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
    }

    int mInitialX = -1;
    int mLastX = -1;
    int mTextWidth = 0;
    int mViewWidth = 0;
    boolean mDraggingLabel = false;
    
    TextView textViewForContainer(View v) {
        View vv = v.findViewById(R.id.artistname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.genrename);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.trackname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.nexttrackname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.nextartistname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.nextgenrename);
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
                    tv.setEllipsize(TruncateAt.END);
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
                tv.setEllipsize(TruncateAt.END);
            } else {
                Message newmsg = obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(newmsg, 15);
            }
        }
    };
    
    public boolean onLongClick(View view) {
        if (mService == null) return true;

        long audioId = mService.getAudioId();
        long artistId = mService.getArtistId();
        long genreId = mService.getGenreId();
        String song = mService.getTrackName();
        String artist = mService.getArtistName();
        String album = mService.getAlbumName();
        long nextArtistId = mService.getNextArtistId();
        long nextGenreId = mService.getNextGenreId();
        String nextSong = mService.getNextTrackName();
        String nextArtist = mService.getNextArtistName();
        String nextAlbum = mService.getNextAlbumName();

        if (MediaStore.UNKNOWN_STRING.equals(album) &&
                MediaStore.UNKNOWN_STRING.equals(artist) &&
                song != null &&
                song.startsWith("recording")) {
            // not music
            return false;
        }

        if (audioId < 0) {
            return false;
        }

        Cursor c = MusicUtils.query(this,
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId),
                new String[] {MediaStore.Audio.Media.IS_MUSIC}, null, null, null);
        boolean isMusic = true;
        if (c != null) {
            if (c.moveToFirst()) {
                isMusic = c.getInt(0) != 0;
            }
            c.close();
        }
        if (!isMusic) {
            return false;
        }

        if (view.equals(mArtistName)) {
            browseCategory("artist", artistId);
            return true;
        } else if (view.equals(mGenreName)) {
            browseCategory("genre", genreId);
            return true;
        } else if (view.equals(mNextArtistName)) {
            browseCategory("artist", nextArtistId);
            return true;
        } else if (view.equals(mNextGenreName)) {
            browseCategory("genre", nextGenreId);
            return true;
        } else if (view.equals(mTrackName)) {
            return searchSong(artist, album, song);
        } else if (view.equals(mNextTrackName)) {
            return searchSong(nextArtist, nextAlbum, nextSong);
        } else {
            throw new RuntimeException("shouldn't be here");
        }
    }

    private void browseCategory(String categoryId, long id) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/vnd.djdplayer.audio");
        intent.putExtra(categoryId, String.valueOf(id));
        startActivity(intent);
    }

    private boolean searchSong(String artist, String album, String song) {
        if ((song == null) || MediaStore.UNKNOWN_STRING.equals(song)) {
            // A popup of the form "Search for null/'' using ..." is pretty
            // unhelpful, plus, we won't find any way to buy it anyway.
            return false;
        }

        boolean knownArtist =
            (artist != null) && !MediaStore.UNKNOWN_STRING.equals(artist);

        boolean knownAlbum =
            (album != null) && !MediaStore.UNKNOWN_STRING.equals(album);

        String query;
        CharSequence title = song;
        if (knownArtist) {
            query = artist + " " + song;
        } else {
            query = song;
        }
        String mime = "audio/*"; // the specific type doesn't matter, so don't bother retrieving it

        title = getString(R.string.mediasearch, title);

        Intent i = new Intent();
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.putExtra(SearchManager.QUERY, query);
        if(knownArtist) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
        }
        if(knownAlbum) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
        }
        i.putExtra(MediaStore.EXTRA_MEDIA_TITLE, song);
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, mime);

        startActivity(Intent.createChooser(i, title));
        return true;
    }

    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (mService == null)) return;
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;
                mService.seek(mPosOverride);

                // trackball event, allow progress updates
                if (!mFromTouch) {
                    refreshNow();
                    mPosOverride = -1;
                }
            }
        }
        public void onStopTrackingTouch(SeekBar bar) {
            mPosOverride = -1;
            mFromTouch = false;
        }
    };

    @Override
    public void onStop() {
        paused = true;
        mHandler.removeMessages(REFRESH);
        unregisterReceiver(mStatusListener);
        MusicUtils.unbindFromService(mToken);
        mService = null;
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        paused = false;

        mToken = MusicUtils.bindToService(this, osc);
        if (mToken == null) {
            // something went wrong
            mHandler.sendEmptyMessage(QUIT);
        }
        
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        registerReceiver(mStatusListener, new IntentFilter(f));
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        updateTrackInfo();
        setPauseButtonImage();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(1, SETTINGS, 0, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(0, TRACK_INFO, 0, R.string.info).setIcon(android.R.drawable.ic_menu_info_details);

        menu.add(1, USE_AS_RINGTONE, 0, R.string.ringtone_menu_short)
                .setIcon(R.drawable.ic_menu_set_as_ringtone);
        menu.add(1, DELETE_ITEM, 0, R.string.delete_item)
                .setIcon(R.drawable.ic_menu_delete);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
            if (getPackageManager().resolveActivity(i, 0) != null) {
                menu.add(0, EFFECTS_PANEL, 0, R.string.effectspanel).setIcon(R.drawable.ic_menu_eq);
            }
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mService != null) {
            MenuItem item = menu.findItem(ADD_TO_PLAYLIST);
            SubMenu sub = (item != null)
                ? item.getSubMenu()
                : menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                        R.string.add_to_playlist).setIcon(android.R.drawable.ic_menu_add);
            MusicUtils.makePlaylistMenu(this, sub);
        }

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        menu.setGroupVisible(1, !km.inKeyguardRestrictedInputMode());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case TRACK_INFO:
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MusicUtils.getCurrentAudioId()),
                    "vnd.android.cursor.item/vnd.djdplayer.audio");
                startActivity(intent);
                return true;

            case USE_AS_RINGTONE: {
                // Set the system setting to make this the current ringtone
                if (mService != null) {
                    MusicUtils.setRingtone(this, mService.getAudioId());
                }
                return true;
            }

            case NEW_PLAYLIST: {
                intent = new Intent();
                intent.setClass(this, CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long [] list = new long[1];
                list[0] = MusicUtils.getCurrentAudioId();
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(this, list, playlist);
                return true;
            }

            case DELETE_ITEM: {
                if (mService != null) {
                    final long [] list = new long[1];
                    list[0] = MusicUtils.getCurrentAudioId();
                    String f = getString(R.string.delete_song_desc, mService.getTrackName());
                    new AlertDialog.Builder(this)
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
                                    MusicUtils.deleteTracks(MediaPlaybackActivity.this, list);
                                }
                            }).show();
                }
                return true;
            }

            case EFFECTS_PANEL: {
                Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mService.getAudioSessionId());
                startActivityForResult(i, EFFECTS_PANEL);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case NEW_PLAYLIST:
                Uri uri = intent.getData();
                if (uri != null) {
                    long [] list = new long[1];
                    list[0] = MusicUtils.getCurrentAudioId();
                    int playlist = Integer.parseInt(uri.getLastPathSegment());
                    MusicUtils.addToPlaylist(this, list, playlist);
                }
                break;
        }
    }
    private final int keyboard[][] = {
        {
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
        },
        {
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_DEL,
        },
        {
            KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_ENTER
        }

    };

    private int lastX;
    private int lastY;

    private boolean seekMethod1(int keyCode)
    {
        if (mService == null) return false;
        for(int x=0;x<10;x++) {
            for(int y=0;y<3;y++) {
                if(keyboard[y][x] == keyCode) {
                    int dir = 0;
                    // top row
                    if(x == lastX && y == lastY) dir = 0;
                    else if (y == 0 && lastY == 0 && x > lastX) dir = 1;
                    else if (y == 0 && lastY == 0 && x < lastX) dir = -1;
                    // bottom row
                    else if (y == 2 && lastY == 2 && x > lastX) dir = -1;
                    else if (y == 2 && lastY == 2 && x < lastX) dir = 1;
                    // moving up
                    else if (y < lastY && x <= 4) dir = 1; 
                    else if (y < lastY && x >= 5) dir = -1; 
                    // moving down
                    else if (y > lastY && x <= 4) dir = -1; 
                    else if (y > lastY && x >= 5) dir = 1; 
                    lastX = x;
                    lastY = y;
                    mService.seek(mService.position() + dir * 5);
                    refreshNow();
                    return true;
                }
            }
        }
        lastX = -1;
        lastY = -1;
        return false;
    }

    private boolean seekMethod2(int keyCode)
    {
        if (mService == null) return false;
        for(int i=0;i<10;i++) {
            if(keyboard[0][i] == keyCode) {
                int seekpercentage = 100*i/10;
                mService.seek(mService.duration() * seekpercentage / 100);
                refreshNow();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode)
        {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (mService != null) {
                    if (!mSeeking && mStartSeekPos >= 0) {
                        mPauseButton.requestFocus();
                        if (mStartSeekPos < 1000) {
                            mService.prev();
                        } else {
                            mService.seek(0);
                        }
                    } else {
                        scanBackward(-1, event.getEventTime() - event.getDownTime());
                        mPauseButton.requestFocus();
                        mStartSeekPos = -1;
                    }
                }
                mSeeking = false;
                mPosOverride = -1;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (mService != null) {
                    if (!mSeeking && mStartSeekPos >= 0) {
                        mPauseButton.requestFocus();
                        mService.next(true);
                    } else {
                        scanForward(-1, event.getEventTime() - event.getDownTime());
                        mPauseButton.requestFocus();
                        mStartSeekPos = -1;
                    }
                }
                mSeeking = false;
                mPosOverride = -1;
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean useDpadMusicControl() {
        return mDeviceHasDpad &&
                (mPrevButton.isFocused() ||
                 mNextButton.isFocused() ||
                 mPauseButton.isFocused());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        int repcnt = event.getRepeatCount();

        if((seekmethod==0)?seekMethod1(keyCode):seekMethod2(keyCode))
            return true;

        switch(keyCode)
        {
/*
            // image scale
            case KeyEvent.KEYCODE_Q: av.adjustParams(-0.05, 0.0, 0.0, 0.0, 0.0,-1.0); break;
            case KeyEvent.KEYCODE_E: av.adjustParams( 0.05, 0.0, 0.0, 0.0, 0.0, 1.0); break;
            // image translate
            case KeyEvent.KEYCODE_W: av.adjustParams(    0.0, 0.0,-1.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_X: av.adjustParams(    0.0, 0.0, 1.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_A: av.adjustParams(    0.0,-1.0, 0.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_D: av.adjustParams(    0.0, 1.0, 0.0, 0.0, 0.0, 0.0); break;
            // camera rotation
            case KeyEvent.KEYCODE_R: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 0.0,-1.0); break;
            case KeyEvent.KEYCODE_U: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 0.0, 1.0); break;
            // camera translate
            case KeyEvent.KEYCODE_Y: av.adjustParams(    0.0, 0.0, 0.0, 0.0,-1.0, 0.0); break;
            case KeyEvent.KEYCODE_N: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 1.0, 0.0); break;
            case KeyEvent.KEYCODE_G: av.adjustParams(    0.0, 0.0, 0.0,-1.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_J: av.adjustParams(    0.0, 0.0, 0.0, 1.0, 0.0, 0.0); break;

*/

            case KeyEvent.KEYCODE_SLASH:
                seekmethod = 1 - seekmethod;
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mPrevButton.hasFocus()) {
                    mPrevButton.requestFocus();
                }
                scanBackward(repcnt, event.getEventTime() - event.getDownTime());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mNextButton.hasFocus()) {
                    mNextButton.requestFocus();
                }
                scanForward(repcnt, event.getEventTime() - event.getDownTime());
                return true;

            case KeyEvent.KEYCODE_S:
                shuffle();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_SPACE:
                doPauseResume();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private void scanBackward(int repcnt, long delta) {
        if(mService == null) return;
        if(repcnt == 0) {
            mStartSeekPos = mService.position();
            mLastSeekEventTime = 0;
            mSeeking = false;
        } else {
            mSeeking = true;
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos - delta;
            if (newpos < 0) {
                // move to previous track
                mService.prev();
                long duration = mService.duration();
                mStartSeekPos += duration;
                newpos += duration;
            }
            if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                mService.seek(newpos);
                mLastSeekEventTime = delta;
            }
            if (repcnt >= 0) {
                mPosOverride = newpos;
            } else {
                mPosOverride = -1;
            }
            refreshNow();
        }
    }

    private void scanForward(int repcnt, long delta) {
        if(mService == null) return;
        if(repcnt == 0) {
            mStartSeekPos = mService.position();
            mLastSeekEventTime = 0;
            mSeeking = false;
        } else {
            mSeeking = true;
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos + delta;
            long duration = mService.duration();
            if (newpos >= duration) {
                // move to next track
                mService.next(true);
                mStartSeekPos -= duration; // is OK to go negative
                newpos -= duration;
            }
            if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                mService.seek(newpos);
                mLastSeekEventTime = delta;
            }
            if (repcnt >= 0) {
                mPosOverride = newpos;
            } else {
                mPosOverride = -1;
            }
            refreshNow();
        }
    }
    
    private void doPauseResume() {
        if(mService != null) {
            if (mService.isPlaying()) {
                mService.pause();
            } else {
                mService.play();
            }
            refreshNow();
            setPauseButtonImage();
        }
    }
    
    private void shuffle() {
        if (mService == null) {
            return;
        }
        mService.doShuffle();
    }
    
    private void cycleRepeat() {
        if (mService == null) {
            return;
        }
        int mode = mService.getRepeatMode();
        if (mode == MediaPlaybackService.REPEAT_NONE) {
            mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
            showToast(R.string.repeat_all_notif);
        } else if (mode == MediaPlaybackService.REPEAT_ALL) {
            mService.setRepeatMode(MediaPlaybackService.REPEAT_CURRENT);
            showToast(R.string.repeat_current_notif);
        } else if (mode == MediaPlaybackService.REPEAT_CURRENT) {
            mService.setRepeatMode(MediaPlaybackService.REPEAT_STOPAFTER);
            showToast(R.string.repeat_stopafter_notif);
        } else {
            mService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
            showToast(R.string.repeat_off_notif);
        }
        setRepeatButtonImage();
    }
    
    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        mToast.setText(resid);
        mToast.show();
    }

    private void startPlayback() {
        if (mService == null)
            return;
        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (uri != null && uri.toString().length() > 0) {
            // If this is a file:// URI, just use the path directly instead
            // of going through the open-from-filedescriptor codepath.
            String filename;
            if ("file".equals(uri.getScheme())) {
                filename = uri.getPath();
            } else {
                filename = uri.toString();
            }
            try {
                mService.stop();
                mService.open(filename);
                mService.play();
                setIntent(new Intent());
            } catch (Exception ex) {
                Log.d("MediaPlaybackActivity", "couldn't start playback: " + ex);
            }
        }

        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }

    private final ServiceConnection osc = new ServiceConnection() {
            public void onServiceConnected(ComponentName classname, IBinder service) {
                mService = ((MediaPlaybackService.MediaPlaybackServiceBinder)service).getService();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    invalidateOptionsMenu();
                }
                startPlayback();
                // Assume something is playing when the service says it is,
                // but also if the audio ID is valid but the service is paused.
                if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                        mService.getPath() != null) {
                    // something is playing now, we're done
                    mRepeatButton.setVisibility(View.VISIBLE);
                    mShuffleButton.setVisibility(View.VISIBLE);
                    mQueueButton.setVisibility(View.VISIBLE);
                    mLibraryButton.setVisibility(View.VISIBLE);
                    setRepeatButtonImage();
                    setPauseButtonImage();
                    return;
                }
                // Service is dead or not playing anything. If we got here as part
                // of a "play this file" Intent, exit. Otherwise go to the Music
                // app start screen.
                if (getIntent().getData() == null) {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setClass(MediaPlaybackActivity.this, MusicBrowserActivity.class);
                    startActivity(intent);
                }
                finish();
            }
            public void onServiceDisconnected(ComponentName classname) {
                mService = null;
            }
    };

    private void setRepeatButtonImage() {
        if (mService == null) return;
        switch (mService.getRepeatMode()) {
            case MediaPlaybackService.REPEAT_ALL:
                mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_all_btn);
                break;
            case MediaPlaybackService.REPEAT_CURRENT:
                mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_once_btn);
                break;
            case MediaPlaybackService.REPEAT_STOPAFTER:
                mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_stopafter_btn);
                break;
            default:
                mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_off_btn);
                break;
        }
    }
    
    private void setPauseButtonImage() {
        if (mService != null && mService.isPlaying()) {
            mPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            mPauseButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }
    
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistName;
    private TextView mGenreName;
    private TextView mTrackName;
    private TextView mNextTrackName;
    private TextView mNextArtistName;
    private TextView mNextGenreName;
    private ProgressBar mProgress;
    private long mPosOverride = -1;
    private boolean mFromTouch = false;
    private long mDuration;
    private int seekmethod;
    private boolean paused;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        if(mService == null)
            return 500;
        long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
        long remaining = 1000 - (pos % 1000);
        if ((pos >= 0) && (mDuration > 0)) {
            mCurrentTime.setText(MusicUtils.formatDuration(this, pos));

            if (mService.isPlaying()) {
                mCurrentTime.setVisibility(View.VISIBLE);
            } else {
                // blink the counter
                int vis = mCurrentTime.getVisibility();
                mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                remaining = 500;
            }

            mProgress.setProgress((int) (1000 * pos / mDuration));
        } else {
            mCurrentTime.setText("--:--");
            mProgress.setProgress(1000);
        }
        // return the number of milliseconds until the next full second, so
        // the counter can be updated at just the right time
        return remaining;
    }
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;
                    
                case QUIT:
                    // This can be moved back to onCreate once the bug that prevents
                    // Dialogs from being started from onCreate/onResume is fixed.
                    new AlertDialog.Builder(MediaPlaybackActivity.this)
                            .setTitle(R.string.service_start_error_title)
                            .setMessage(R.string.service_start_error_msg)
                            .setPositiveButton(R.string.service_start_error_button,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            finish();
                                        }
                                    })
                            .setCancelable(false)
                            .show();
                    break;

                default:
                    break;
            }
        }
    };

    private final BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MediaPlaybackService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                setPauseButtonImage();
                queueNextRefresh(1);
            } else if (action.equals(MediaPlaybackService.QUEUE_CHANGED)) {
                updateTrackInfo();
            } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                setPauseButtonImage();
            }
        }
    };

    private void updateTrackInfo() {
        if (mService == null) {
            return;
        }
        String path = mService.getPath();
        if (path == null) {
            finish();
            return;
        }

        long songid = mService.getAudioId();
        if (songid < 0 && path.toLowerCase().startsWith("http://")) {
            // Once we can get meta data from MediaPlayer,
            // we can show that info again when streaming.
            mArtistName.setVisibility(View.INVISIBLE);
            mGenreName.setVisibility(View.INVISIBLE);
            mTrackName.setText(path);
            mNextTrackName.setVisibility(View.INVISIBLE);
            mNextArtistName.setVisibility(View.INVISIBLE);
            mNextGenreName.setVisibility(View.INVISIBLE);
        } else {
            mArtistName.setVisibility(View.VISIBLE);
            mGenreName.setVisibility(View.VISIBLE);
            mNextTrackName.setVisibility(View.VISIBLE);
            mNextArtistName.setVisibility(View.VISIBLE);
            mNextGenreName.setVisibility(View.VISIBLE);
            String artistName = mService.getArtistName();
            if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                artistName = getString(R.string.unknown_artist_name);
            }
            mArtistName.setText(artistName);
            String genreName = mService.getGenreName();
            if (MediaStore.UNKNOWN_STRING.equals(genreName)) {
                genreName = getString(R.string.unknown_genre_name);
            }
            mGenreName.setText(genreName);
            mTrackName.setText(mService.getTrackName());

            String nextTrackName = mService.getNextTrackName();
            if (nextTrackName != null) {
                mNextTrackName.setText(nextTrackName);
                mNextArtistName.setText(mService.getNextArtistName());
                mNextGenreName.setText(mService.getNextGenreName());
            } else {
                mNextTrackName.setText("");
                mNextArtistName.setText("");
                mNextGenreName.setText("");
            }
        }
        mDuration = mService.duration();
        mTotalTime.setText(MusicUtils.formatDuration(this, mDuration));
    }
}
