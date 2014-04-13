/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.app.SearchManager;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.SubMenu;
import android.widget.Toast;
import nu.staldal.djdplayer.provider.ID3Utils;

import java.io.File;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

public class MusicUtils {
    private static final String LOGTAG = "MusicUtils";

    public interface Defs {
        public final static int ADD_TO_PLAYLIST = 1;
        public final static int USE_AS_RINGTONE = 2;
        public final static int PLAYLIST_SELECTED = 3;
        public final static int NEW_PLAYLIST = 4;
        public final static int PLAY_ALL_NOW = 5;
        public final static int SHUFFLE_PLAYLIST = 9;
        public final static int UNIQUEIFY_PLAYLIST = 10;
        public final static int QUEUE_ALL = 11;
        public final static int DELETE_ITEM = 12;
        public final static int QUEUE = 14;
        public final static int PLAY_NOW = 15;
        public final static int PLAY_NEXT = 16;
        public final static int SETTINGS = 17;
        public final static int SEARCH = 18;
        public final static int SHARE_VIA = 19;
        public final static int TRACK_INFO = 20;
        public final static int SEARCH_FOR = 21;
        public final static int PLAY_ALL_NEXT = 22;
        public final static int CHILD_MENU_BASE = 23; // this should be the last item

        public final static int INTERLEAVE_ALL = 1000;
    }

    /**
     * This is now only used for the query screen
     */
    public static String makeAlbumsSongsLabel(Context context, int numalbums, int numsongs, boolean isUnknown) {
        // There are several formats for the albums/songs information:
        // "1 Song"   - used if there is only 1 song
        // "N Songs" - used for the "unknown artist" item
        // "1 Album"/"N Songs" 
        // "N Album"/"M Songs"
        // Depending on locale, these may need to be further subdivided
        
        StringBuilder songs_albums = new StringBuilder();

        if (numsongs == 1) {
            songs_albums.append(context.getResources().getQuantityString(R.plurals.Nsongs, 1));
        } else {
            Resources r = context.getResources();
            if (! isUnknown) {
                String f = r.getQuantityText(R.plurals.Nalbums, numalbums).toString();
                sFormatBuilder.setLength(0);
                sFormatter.format(f, numalbums);
                songs_albums.append(sFormatBuilder);
                songs_albums.append(context.getString(R.string.albumsongseparator));
            }
            String f = r.getQuantityText(R.plurals.Nsongs, numsongs).toString();
            sFormatBuilder.setLength(0);
            sFormatter.format(f, numsongs);
            songs_albums.append(sFormatBuilder);
        }
        return songs_albums.toString();
    }
    
    public static MediaPlaybackService sService = null;
    private final static HashMap<Context, ServiceBinder> sConnectionMap = new HashMap<>();

    public static class ServiceToken {
        final ContextWrapper mWrappedContext;
        ServiceToken(ContextWrapper context) {
            mWrappedContext = context;
        }
    }

    public static ServiceToken bindToService(Activity context, ServiceConnection callback) {
        Activity realActivity = context.getParent();
        if (realActivity == null) {
            realActivity = context;
        }
        ContextWrapper cw = new ContextWrapper(realActivity);
        cw.startService(new Intent(cw, MediaPlaybackService.class));
        ServiceBinder sb = new ServiceBinder(callback);
        if (cw.bindService((new Intent()).setClass(cw, MediaPlaybackService.class), sb, 0)) {
            sConnectionMap.put(cw, sb);
            return new ServiceToken(cw);
        }
        Log.e(LOGTAG, "Failed to bind to service");
        return null;
    }

    public static void unbindFromService(ServiceToken token) {
        if (token == null) {
            Log.e(LOGTAG, "Trying to unbind with null token");
            return;
        }
        ContextWrapper cw = token.mWrappedContext;
        ServiceBinder sb = sConnectionMap.remove(cw);
        if (sb == null) {
            Log.e(LOGTAG, "Trying to unbind for unknown Context");
            return;
        }
        cw.unbindService(sb);
        if (sConnectionMap.isEmpty()) {
            // presumably there is nobody interested in the service at this point,
            // so don't hang on to the ServiceConnection
            sService = null;
        }
    }

    private static class ServiceBinder implements ServiceConnection {
        final ServiceConnection mCallback;
        ServiceBinder(ServiceConnection callback) {
            mCallback = callback;
        }
        
        public void onServiceConnected(ComponentName className, android.os.IBinder service) {
            sService = ((MediaPlaybackService.MediaPlaybackServiceBinder)service).getService();
            if (mCallback != null) {
                mCallback.onServiceConnected(className, service);
            }
        }
        
        public void onServiceDisconnected(ComponentName className) {
            if (mCallback != null) {
                mCallback.onServiceDisconnected(className);
            }
            sService = null;
        }
    }

    public static long getCurrentAlbumId() {
        if (sService != null) {
            return sService.getAlbumId();
        }
        return -1;
    }

    public static long getCurrentGenreId() {
        if (sService != null) {
            return sService.getGenreId();
        }
        return -1;
    }

    public static File getCurrentFolder() {
        if (sService != null) {
            return sService.getFolder();
        }
        return null;
    }

    public static String getCurrentMimeType() {
        if (sService != null) {
            return sService.getMimeType();
        }
        return null;
    }

    public static long getCurrentArtistId() {
        if (MusicUtils.sService != null) {
            return sService.getArtistId();
        }
        return -1;
    }

    public static long getCurrentAudioId() {
        if (MusicUtils.sService != null) {
            return sService.getAudioId();
        }
        return -1;
    }
    
    public static boolean isPlaying() {
        if (MusicUtils.sService != null) {
            return sService.isPlaying();
        }
        return false;
    }

    public final static long [] sEmptyList = new long[0];

    public static long [] getSongListForCursorAndClose(Cursor cursor) {
        try {
            return getSongListForCursor(cursor);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public static long [] getSongListForCursor(Cursor cursor) {
        if (cursor == null) {
            return sEmptyList;
        }
        int len = cursor.getCount();
        if (len == 0) {
            return sEmptyList;
        }
        long [] list = new long[len];
        cursor.moveToFirst();
        int colidx;
        try {
            colidx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
        } catch (IllegalArgumentException ex) {
            colidx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        }
        for (int i = 0; i < len; i++) {
            list[i] = cursor.getLong(colidx);
            cursor.moveToNext();
        }
        return list;
    }

    /**
     * Fills out the given submenu with items for "new playlist" and
     * any existing playlists. When the user selects an item, the
     * application will receive PLAYLIST_SELECTED with the Uri of
     * the selected playlist, NEW_PLAYLIST if a new playlist
     * should be created, and ADD_TO_CURRENT_PLAYLIST if the "current playlist" was
     * selected.
     * @param context The context to use for creating the menu items
     * @param sub The submenu to add the items to.
     */
    public static void makePlaylistMenu(Context context, SubMenu sub) {
        makePlaylistMenu(context, sub, Defs.NEW_PLAYLIST, Defs.PLAYLIST_SELECTED);

    }

    public static void makePlaylistMenu(Context context, SubMenu sub, int newPlaylist, int playlistSelected) {
        String[] cols = new String[] {
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME
        };
        ContentResolver resolver = context.getContentResolver();
        if (resolver == null) {
            Log.w(LOGTAG, "resolver = null");
        } else {
            String whereClause = MediaStore.Audio.Playlists.NAME + " != ''";
            Cursor cur = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                cols, whereClause, null, MediaStore.Audio.Playlists.NAME);
            sub.clear();
            sub.add(1, newPlaylist, 0, R.string.new_playlist);
            if (cur != null && cur.getCount() > 0) {
                //sub.addSeparator(1, 0);
                cur.moveToFirst();
                while (! cur.isAfterLast()) {
                    Intent intent = new Intent();
                    intent.putExtra("playlist", cur.getLong(0));
//                    if (cur.getInt(0) == mLastPlaylistSelected) {
//                        sub.add(0, MusicBaseActivity.PLAYLIST_SELECTED, cur.getString(1)).setIntent(intent);
//                    } else {
                    sub.add(1, playlistSelected, 0, cur.getString(1)).setIntent(intent);
//                    }
                    cur.moveToNext();
                }
            }
            if (cur != null) {
                cur.close();
            }
        }
    }

    public static void deleteTracks(Context context, long [] list) {
        String [] cols = new String [] { MediaStore.Audio.Media._ID, 
                MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID };
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media._ID + " IN (");
        for (int i = 0; i < list.length; i++) {
            where.append(list[i]);
            if (i < list.length - 1) {
                where.append(",");
            }
        }
        where.append(")");
        Cursor c = query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                where.toString(), null, null);

        if (c != null) {

            // step 1: remove selected tracks from the current playlist
            c.moveToFirst();
            while (! c.isAfterLast()) {
                // remove from current playlist
                long id = c.getLong(0);
                sService.removeTrack(id);
                c.moveToNext();
            }

            // step 2: remove selected tracks from the database
            context.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, where.toString(), null);

            // step 3: remove files from card
            c.moveToFirst();
            while (! c.isAfterLast()) {
                String name = c.getString(1);
                File f = new File(name);
                try {  // File.delete can throw a security exception
                    if (!f.delete()) {
                        // I'm not sure if we'd ever get here (deletion would
                        // have to fail, but no exception thrown)
                        Log.e(LOGTAG, "Failed to delete file " + name);
                    }
                    c.moveToNext();
                } catch (SecurityException ex) {
                    c.moveToNext();
                }
            }
            c.close();
        }

        String message = context.getResources().getQuantityString(
                R.plurals.NNNtracksdeleted, list.length, list.length);
        
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        // We deleted a number of tracks, which could affect any number of things
        // in the media content domain, so update everything.
        context.getContentResolver().notifyChange(Uri.parse("content://media"), null);
    }

    public static void playSong(Context context, long id) {
        switch (PreferenceManager.getDefaultSharedPreferences(context).getString(
                SettingsActivity.CLICK_ON_SONG, SettingsActivity.PLAY_NEXT)) {
            case SettingsActivity.PLAY_NOW:
                MusicUtils.queueAndPlayImmediately(context, new long[] { id });
                break;
            case SettingsActivity.QUEUE:
                MusicUtils.queue(context, new long[] { id });
                break;
            default:
                MusicUtils.queueNextAndPlayIfNotAlreadyPlaying(context, new long[] { id });
                break;
        }
    }

    public static void queueNextAndPlayIfNotAlreadyPlaying(Context context, long[] songs) {
         if (isPlaying()) {
             queueNext(context, songs);
         } else {
             queueAndPlayImmediately(context, songs);
         }
    }

    public static void queue(Context context, long[] list) {
        if (sService == null) {
            return;
        }
        sService.enqueue(list, MediaPlaybackService.LAST);
        String message = context.getResources().getQuantityString(
                R.plurals.NNNtrackstoplayqueue, list.length, list.length);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void interleave(Context context, long[] list, int currentCount, int newCount) {
        if (sService == null) {
            return;
        }
        sService.interleave(list, currentCount, newCount);
        String message = context.getResources().getQuantityString(
                R.plurals.NNNtrackstoplayqueue, list.length, list.length);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void queueNext(Context context, long[] songs) {
        if (sService == null) {
            return;
        }
        sService.enqueue(songs, MediaPlaybackService.NEXT);
        Toast.makeText(context, R.string.will_play_next, Toast.LENGTH_SHORT).show();
    }

    public static void queueAndPlayImmediately(Context context, long[] songs) {
        if (sService == null) {
            return;
        }
        sService.enqueue(songs, MediaPlaybackService.NOW);
    }

    private static ContentValues[] sContentValuesCache = null;

    /**
     * @param ids The source array containing all the ids to be added to the playlist
     * @param offset Where in the 'ids' array we start reading
     * @param len How many items to copy during this pass
     * @param base The play order offset to use for this pass
     */
    private static void makeInsertItems(long[] ids, int offset, int len, int base) {
        // adjust 'len' if would extend beyond the end of the source array
        if (offset + len > ids.length) {
            len = ids.length - offset;
        }
        // allocate the ContentValues array, or reallocate if it is the wrong size
        if (sContentValuesCache == null || sContentValuesCache.length != len) {
            sContentValuesCache = new ContentValues[len];
        }
        // fill in the ContentValues array with the right values for this pass
        for (int i = 0; i < len; i++) {
            if (sContentValuesCache[i] == null) {
                sContentValuesCache[i] = new ContentValues();
            }

            sContentValuesCache[i].put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, base + offset + i);
            sContentValuesCache[i].put(MediaStore.Audio.Playlists.Members.AUDIO_ID, ids[offset + i]);
        }
    }

    public static void addToPlaylist(Context context, long [] ids, long playlistid) {
        if (ids == null) {
            // this shouldn't happen (the menuitems shouldn't be visible
            // unless the selected item represents something playable
            Log.e(LOGTAG, "ListSelection null");
        } else {
            int size = ids.length;
            ContentResolver resolver = context.getContentResolver();
            // need to determine the number of items currently in the playlist,
            // so the play_order field can be maintained.
            Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistid);
            Cursor cur = resolver.query(uri, new String[] { "count(*)" }, null, null, null);
            if (cur != null) {
                cur.moveToFirst();
                int base = cur.getInt(0);
                cur.close();
                int numInserted = 0;
                for (int i = 0; i < size; i += 1000) {
                    makeInsertItems(ids, i, 1000, base);
                    numInserted += resolver.bulkInsert(uri, sContentValuesCache);
                }
                Toast.makeText(context, context.getResources().getQuantityString(
                        R.plurals.NNNtrackstoplaylist, numInserted, numInserted), Toast.LENGTH_SHORT).show();
                //mLastPlaylistSelected = playlistid;
            } else {
                Log.w(LOGTAG, "Unable to lookup playlist: " + playlistid);
            }
        }
    }

    public static Cursor query(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                return null;
            }
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        } catch (UnsupportedOperationException ex) {
            return null;
        }
    }

    /*  Try to use String.format() as little as possible, because it creates a
     *  new Formatter every time you call it, which is very inefficient.
     *  Reusing an existing Formatter more than tripled the speed of
     *  formatDuration().
     *  This Formatter/StringBuilder are also used by makeAlbumSongsLabel()
     */
    private static final StringBuilder sFormatBuilder = new StringBuilder();
    private static final Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
    private static final Object[] sTimeArgs = new Object[5];

    public static String formatDuration(Context context, long millis) {
        long secs = millis / 1000;
        String durationformat = context.getString(
                secs < 3600 ? R.string.durationformatshort : R.string.durationformatlong);

        /* Provide multiple arguments so the format can be changed easily
         * by modifying the xml.
         */
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format(durationformat, timeArgs).toString();
    }

    public static void playAll(Context context, long[] list) {
        if (list.length == 0 || sService == null) {
            Log.d(LOGTAG, "attempt to play empty song list");
            // Don't try to play empty playlists. Nothing good will come of it.
            String message = context.getString(R.string.emptyplaylist, list.length);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            return;
        }
        sService.open(list, 0);
        sService.play();
        Intent intent = new Intent("nu.staldal.djdplayer.PLAYBACK_VIEWER")
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    public static void shuffleArray(long[] array) {
        Random random = new Random();
        for (int i=0; i < array.length; i++) {
            int randomPosition = random.nextInt(array.length);
            long temp = array[i];
            array[i] = array[randomPosition];
            array[randomPosition] = temp;
        }
    }

    static void setIntPref(Context context, String name, int value) {
        SharedPreferences prefs =
            context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        Editor ed = prefs.edit();
        ed.putInt(name, value);
        ed.apply();
    }

    static void setStringPref(Context context, String name, String value) {
        SharedPreferences prefs =
            context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        Editor ed = prefs.edit();
        ed.putString(name, value);
        ed.apply();
    }

    static void setRingtone(Context context, long id) {
        ContentResolver resolver = context.getContentResolver();
        // Set the flag in the database to mark this as a ringtone
        Uri ringUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        try {
            ContentValues values = new ContentValues(2);
            values.put(MediaStore.Audio.Media.IS_RINGTONE, "1");
            values.put(MediaStore.Audio.Media.IS_ALARM, "1");
            resolver.update(ringUri, values, null, null);
        } catch (UnsupportedOperationException ex) {
            // most likely the card just got unmounted
            Log.e(LOGTAG, "couldn't set ringtone flag for id " + id);
            return;
        }

        String[] cols = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE
        };

        String where = MediaStore.Audio.Media._ID + "=" + id;
        Cursor cursor = query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                cols, where , null, null);
        try {
            if (cursor != null && cursor.getCount() == 1) {
                // Set the system setting to make this the current ringtone
                cursor.moveToFirst();
                Settings.System.putString(resolver, Settings.System.RINGTONE, ringUri.toString());
                String message = context.getString(R.string.ringtone_set, cursor.getString(2));
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    static int getCardId(Context context) {
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(Uri.parse("content://media/external/fs_id"), null, null, null, null);
        int id = -1;
        if (c != null) {
            c.moveToFirst();
            id = c.getInt(0);
            c.close();
        }
        return id;
    }

    static IdAndName fetchGenre(Context context, long songId) {
        Cursor c = context.getContentResolver().query(
                Uri.parse("content://media/external/audio/media/" + String.valueOf(songId) + "/genres"),
                new String[] { MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME },
                null,
                null,
                null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return new IdAndName(
                            c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)),
                            ID3Utils.decodeGenre(c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME))));
                } else {
                    return null;
                }
            } finally {
                c.close();
            }
        } else {
            return null;
        }
    }

    /**
     * Cursor should be positioned on the entry to be checked
     * Returns false if the entry matches the naming pattern used for recordings,
     * or if it is marked as not music in the database.
     */
    static boolean isMusic(Cursor c) {
        int titleidx = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int albumidx = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        int artistidx = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);

        String title = c.getString(titleidx);
        String album = c.getString(albumidx);
        String artist = c.getString(artistidx);
        if (MediaStore.UNKNOWN_STRING.equals(album) &&
                MediaStore.UNKNOWN_STRING.equals(artist) &&
                title != null &&
                title.startsWith("recording")) {
            // not music
            return false;
        }

        int ismusic_idx = c.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC);
        boolean ismusic = true;
        if (ismusic_idx >= 0) {
            ismusic = c.getInt(ismusic_idx) != 0;
        }
        return ismusic;
    }

    static Intent shareVia(long audioId, String mimeType, Resources resources) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM,
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId));
        intent.setType(mimeType);

        return Intent.createChooser(intent, resources.getString(R.string.share_via));
    }

    static Intent searchFor(String trackName, String artistNameForAlbum, String albumName, Resources resources) {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        String query;
        if (MediaStore.UNKNOWN_STRING.equals(artistNameForAlbum)) {
            query = trackName;
        } else {
            query = artistNameForAlbum + " " + trackName;
            intent.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artistNameForAlbum);
        }
        if (MediaStore.UNKNOWN_STRING.equals(albumName)) {
            intent.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, albumName);
        }
        intent.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "audio/*");
        intent.putExtra(SearchManager.QUERY, query);

        return Intent.createChooser(intent, resources.getString(R.string.mediasearch, trackName));
    }

    static long parseLong(String s) {
        return (s == null) ? -1 : Long.parseLong(s);
    }

    static boolean isLong(String s) {
        if (s == null) return false;
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

