/*
 * Copyright (C) 2016 Mikael Ståldal
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

import android.content.ContentResolver;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.provider.MediaStore;
import android.util.Log;

import java.util.Arrays;

public class PlayQueueCursor extends AbstractCursor {

    private static final String TAG = PlayQueueCursor.class.getSimpleName();

    public final static String[] COLUMNS = new String[]{
            MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.DURATION,
            MediaStore.Audio.AudioColumns._ID,
            MediaStore.Audio.AudioColumns._ID,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.MIME_TYPE
    };

    private final MediaPlayback service;
    private final ContentResolver contentResolver;

    private Cursor mCurrentPlaylistCursor;     // updated in onMove
    private int mSize;                         // size of the queue
    private long[] playQueue;
    private long[] mCursorIdxs;
    private int mCurPos;

    public PlayQueueCursor(MediaPlayback service, ContentResolver contentResolver) {
        this.service = service;
        this.contentResolver = contentResolver;
        init();
    }

    private void init() {
        if (mCurrentPlaylistCursor != null) {
            mCurrentPlaylistCursor.close();
            mCurrentPlaylistCursor = null;
        }
        playQueue = service.getQueue();
        mSize = playQueue.length;
        if (mSize == 0) {
            return;
        }

        mCurrentPlaylistCursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                COLUMNS, buildPlayQueueWhereClause(playQueue), null, MediaStore.Audio.AudioColumns._ID);

        if (mCurrentPlaylistCursor == null) {
            mSize = 0;
            return;
        }

        int size = mCurrentPlaylistCursor.getCount();
        mCursorIdxs = new long[size];
        mCurrentPlaylistCursor.moveToFirst();
        int colidx = mCurrentPlaylistCursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID);
        for (int i = 0; i < size; i++) {
            mCursorIdxs[i] = mCurrentPlaylistCursor.getLong(colidx);
            mCurrentPlaylistCursor.moveToNext();
        }
        mCurrentPlaylistCursor.moveToFirst();
        mCurPos = -1;

        // At this point we can verify the 'now playing' list we got
        // earlier to make sure that all the items in there still exist
        // in the database, and remove those that aren't. This way we
        // don't get any blank items in the list.
        int removed = 0;
        for (int i = playQueue.length - 1; i >= 0; i--) {
            long trackid = playQueue[i];
            int crsridx = Arrays.binarySearch(mCursorIdxs, trackid);
            if (crsridx < 0) {
                Log.i(TAG, "item no longer exists in db: " + trackid);
                removed += service.removeTrack(trackid);
            }
        }
        if (removed > 0) {
            playQueue = service.getQueue();
            mSize = playQueue.length;
            if (mSize == 0) {
                mCursorIdxs = null;
            }
        }
    }

    private String buildPlayQueueWhereClause(long[] playQueue) {
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.AudioColumns._ID + " IN (");
        for (int i = 0; i < playQueue.length; i++) {
            where.append(playQueue[i]);
            if (i < playQueue.length - 1) {
                where.append(",");
            }
        }
        where.append(")");
        return where.toString();
    }

    @Override
    public int getCount() {
        return mSize;
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        if (oldPosition == newPosition)
            return true;

        if (playQueue == null || mCursorIdxs == null || newPosition >= playQueue.length) {
            return false;
        }

        // The cursor doesn't have any duplicates in it, and is not ordered
        // in queue-order, so we need to figure out where in the cursor we should be.

        long newid = playQueue[newPosition];
        int crsridx = Arrays.binarySearch(mCursorIdxs, newid);
        mCurrentPlaylistCursor.moveToPosition(crsridx);
        mCurPos = newPosition;

        return true;
    }

    public boolean removeItem(int which) {
        if (service.removeTracks(which, which) == 0) {
            return false; // delete failed
        }
        int i = which;
        mSize--;
        while (i < mSize) {
            playQueue[i] = playQueue[i + 1];
            i++;
        }
        onMove(-1, mCurPos);
        return true;
    }

    public void moveItem(int from, int to) {
        service.moveQueueItem(from, to);
        playQueue = service.getQueue();
        onMove(-1, mCurPos); // update the underlying cursor
    }

    @Override
    public String getString(int column) {
        try {
            return mCurrentPlaylistCursor.getString(column);
        } catch (Exception ex) {
            onChange(true);
            return "";
        }
    }

    @Override
    public short getShort(int column) {
        return mCurrentPlaylistCursor.getShort(column);
    }

    @Override
    public int getInt(int column) {
        try {
            return mCurrentPlaylistCursor.getInt(column);
        } catch (Exception ex) {
            onChange(true);
            return 0;
        }
    }

    @Override
    public long getLong(int column) {
        try {
            return mCurrentPlaylistCursor.getLong(column);
        } catch (Exception ex) {
            onChange(true);
            return 0;
        }
    }

    @Override
    public float getFloat(int column) {
        return mCurrentPlaylistCursor.getFloat(column);
    }

    @Override
    public double getDouble(int column) {
        return mCurrentPlaylistCursor.getDouble(column);
    }

    @Override
    public boolean isNull(int column) {
        return mCurrentPlaylistCursor.isNull(column);
    }

    @Override
    public String[] getColumnNames() {
        return COLUMNS;
    }

    @Override
    public void deactivate() {
        if (mCurrentPlaylistCursor != null)
            mCurrentPlaylistCursor.deactivate();
    }

    @Override
    public boolean requery() {
        init();
        return true;
    }

    @Override
    public void close() {
        if (mCurrentPlaylistCursor != null)
            mCurrentPlaylistCursor.close();
        super.close();
    }

}
