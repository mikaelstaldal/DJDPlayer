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
import android.content.*;
import android.content.res.Configuration;
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
import nu.staldal.ui.RepeatingImageButton;


public class MediaPlaybackActivity extends Activity
        implements MusicUtils.Defs, View.OnTouchListener, View.OnLongClickListener, ServiceConnection {
    private static final String LOGTAG = "MediaPlaybackActivity";

    private static final int REPEAT = CHILD_MENU_BASE+2;
    private static final int CLEAR_QUEUE = CHILD_MENU_BASE+3;

    private static final int ADD_TO_PLAYLIST2 = CHILD_MENU_BASE+4;
    private static final int USE_AS_RINGTONE2 = CHILD_MENU_BASE+5;
    private static final int DELETE_ITEM2 = CHILD_MENU_BASE+6;
    private static final int TRACK_INFO2 = CHILD_MENU_BASE+7;
    private static final int SHARE_VIA2 = CHILD_MENU_BASE+8;
    private static final int SEARCH_FOR2 = CHILD_MENU_BASE+9;
    private static final int NEW_PLAYLIST2 = CHILD_MENU_BASE+10;
    private static final int PLAYLIST_SELECTED2 = CHILD_MENU_BASE+11;

    private boolean mSeeking = false;
    private boolean mDeviceHasDpad;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private MediaPlaybackService mService = null;
    private RepeatingImageButton mPrevButton;
    private ImageButton mPauseButton;
    private RepeatingImageButton mNextButton;
    private Toast mToast;
    private int mTouchSlop;
    private MusicUtils.ServiceToken mToken;
    private PlayQueueFragment playQueueFragment;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.i(LOGTAG, "onCreate");
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        getActionBar().setHomeButtonEnabled(true);

        setContentView(R.layout.audio_player);

        playQueueFragment = (PlayQueueFragment)getFragmentManager().findFragmentById(R.id.playqueue);

        mCurrentTime = (TextView) findViewById(R.id.currenttime);
        mTotalTime = (TextView) findViewById(R.id.totaltime);
        mProgress = (ProgressBar) findViewById(android.R.id.progress);
        mTrackName = (TextView) findViewById(R.id.trackname);
        mArtistName = (TextView) findViewById(R.id.artistname);
        mGenreName = (TextView) findViewById(R.id.genrename);

        mTrackName.setOnTouchListener(this);
        registerForContextMenu(mTrackName);

        mArtistName.setOnTouchListener(this);
        mArtistName.setOnLongClickListener(this);

        mGenreName.setOnTouchListener(this);
        mGenreName.setOnLongClickListener(this);

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

        if (view.equals(mArtistName)) {
            browseCategory("artist", artistId);
            return true;
        } else if (view.equals(mGenreName)) {
            browseCategory("genre", genreId);
            return true;
        } else {
            throw new RuntimeException("shouldn't be here");
        }
    }

    private void browseCategory(String categoryId, long id) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.EMPTY, MimeTypes.DIR_DJDPLAYER_AUDIO);
        intent.putExtra(categoryId, String.valueOf(id));
        startActivity(intent);
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

        mToken = MusicUtils.bindToService(this, this);
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

        menu.add(2, REPEAT, 0, R.string.repeat)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(2, SHUFFLE, 0, R.string.shuffle).setIcon(R.drawable.ic_menu_shuffle)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(3, UNIQUEIFY, 0, R.string.uniqueify).setIcon(R.drawable.ic_menu_uniqueify);

        menu.add(3, CLEAR_QUEUE, 0, R.string.clear_queue).setIcon(R.drawable.ic_menu_clear_playlist);

        menu.add(1, SETTINGS, 0, R.string.settings).setIcon(R.drawable.ic_menu_preferences);

        menu.add(0, SEARCH, 0, R.string.search_title).setIcon(R.drawable.ic_menu_search);

        Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (getPackageManager().resolveActivity(i, 0) != null) {
            menu.add(0, EFFECTS_PANEL, 0, R.string.effectspanel).setIcon(R.drawable.ic_menu_eq)
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateRepeatItem(menu);

        updatePlayingItems(menu);

        applyKeyguard(menu);

        return true;
    }

    private void updateRepeatItem(Menu menu) {
        MenuItem item = menu.findItem(REPEAT);

        if (mService != null) {
            switch (mService.getRepeatMode()) {
                case MediaPlaybackService.REPEAT_ALL:
                    item.setIcon(R.drawable.ic_mp_repeat_all_btn);
                    break;
                case MediaPlaybackService.REPEAT_CURRENT:
                    item.setIcon(R.drawable.ic_mp_repeat_once_btn);
                    break;
                case MediaPlaybackService.REPEAT_STOPAFTER:
                    item.setIcon(R.drawable.ic_mp_repeat_stopafter_btn);
                    break;
                default:
                    item.setIcon(R.drawable.ic_mp_repeat_off_btn);
                    break;
            }
        } else {
            item.setIcon(R.drawable.ic_mp_repeat_off_btn);
        }
    }

    private void updatePlayingItems(Menu menu) {
        menu.setGroupVisible(3, mService != null && !mService.isPlaying());
    }

    private void applyKeyguard(Menu menu) {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        menu.setGroupVisible(1, !km.inKeyguardRestrictedInputMode());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                Intent intent = new Intent(this, MusicBrowserActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return true;
            }
            case REPEAT:
                cycleRepeat();
                return true;

            case SHUFFLE:
                if (mService != null) mService.doShuffle();
                return true;

            case UNIQUEIFY:
                if (mService != null) mService.uniqueify();
                return true;

            case CLEAR_QUEUE:
                if (mService != null) mService.removeTracks(0, Integer.MAX_VALUE);
                return true;

            case SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case SEARCH:
                return onSearchRequested();

            case EFFECTS_PANEL: {
                Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mService.getAudioSessionId());
                startActivityForResult(intent, EFFECTS_PANEL);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (mService == null) return;

        SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST2, 0, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(this, sub, NEW_PLAYLIST2, PLAYLIST_SELECTED2);

        menu.add(0, USE_AS_RINGTONE2, 0, R.string.ringtone_menu);

        menu.add(0, DELETE_ITEM2, 0, R.string.delete_item);

        menu.add(0, TRACK_INFO2, 0, R.string.info);

        menu.add(0, SHARE_VIA2, 0, R.string.share_via);

        menu.add(0, SEARCH_FOR2, 0, R.string.search_for);

        menu.setHeaderTitle(mService.getTrackName());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mService == null) return false;

        switch (item.getItemId()) {
            case NEW_PLAYLIST2: {
                CreatePlaylist.showMe(this, new long[] { MusicUtils.getCurrentAudioId() });
                return true;
            }

            case PLAYLIST_SELECTED2: {
                long [] list = new long[1];
                list[0] = MusicUtils.getCurrentAudioId();
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(this, list, playlist);
                return true;
            }

            case USE_AS_RINGTONE2:
                // Set the system setting to make this the current ringtone
                MusicUtils.setRingtone(this, mService.getAudioId());
                return true;

            case DELETE_ITEM2: {
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
                return true;
            }

            case TRACK_INFO2: {
                TrackInfoFragment.showMe(this,
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MusicUtils.getCurrentAudioId()));

                return true;
            }

            case SHARE_VIA2: {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM,
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MusicUtils.getCurrentAudioId()));
                intent.setType(MusicUtils.getCurrentMimeType());
                startActivity(Intent.createChooser(intent,getResources().getString(R.string.share_via)));
                return true;
            }

            case SEARCH_FOR2:
                startActivity(Intent.createChooser(
                        MusicUtils.buildSearchForIntent(mService.getTrackName(), mService.getArtistName(), mService.getAlbumName()),
                        getString(R.string.mediasearch, mService.getTrackName())));
                return true;
        }
        return super.onContextItemSelected(item);
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
                if (mService != null) mService.doShuffle();
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
        if (mService != null) {
            if (mService.isPlaying()) {
                mService.pause();
            } else {
                mService.play();
            }
            refreshNow();
            setPauseButtonImage();
        }
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
        invalidateOptionsMenu();
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

    public void onServiceConnected(ComponentName classname, IBinder service) {
        mService = ((MediaPlaybackService.MediaPlaybackServiceBinder)service).getService();

        playQueueFragment.onServiceConnected(mService);

        invalidateOptionsMenu();
        startPlayback();

        // Assume something is playing when the service says it is,
        // but also if the audio ID is valid but the service is paused.
        if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                mService.getPath() != null) {
            // something is playing now, we're done
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
        } else {
            mArtistName.setVisibility(View.VISIBLE);
            mGenreName.setVisibility(View.VISIBLE);
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

            setTitle((mService.getQueuePosition() + 1) + "/" + mService.getQueueLength());
        }
        mDuration = mService.duration();
        mTotalTime.setText(MusicUtils.formatDuration(this, mDuration));
    }
}
