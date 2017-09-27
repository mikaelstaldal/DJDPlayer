/*
 * Copyright (C) 2016-2017 Mikael Ståldal
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
package nu.staldal.djdplayer

import android.content.ContentResolver
import android.database.AbstractCursor
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log

import java.util.Arrays

private const val TAG = "PlayQueueCursor"

class PlayQueueCursor(private val service: MediaPlayback, private val contentResolver: ContentResolver) : AbstractCursor() {

    companion object {
        val COLUMNS = arrayOf(
            MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.DURATION,
            MediaStore.Audio.AudioColumns._ID,
            MediaStore.Audio.AudioColumns._ID,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.MIME_TYPE)
    }

    private var mCurrentPlaylistCursor: Cursor? = null      // updated in onMove
    private var mSize: Int = 0                              // size of the queue
    private var playQueue: LongArray? = null
    private var mCursorIdxs: LongArray? = null
    private var mCurPos: Int = 0

    init {
        init()
    }

    private fun init() {
        if (mCurrentPlaylistCursor != null) {
            mCurrentPlaylistCursor!!.close()
            mCurrentPlaylistCursor = null
        }
        playQueue = service.queue
        mSize = playQueue!!.size
        if (mSize == 0) {
            return
        }

        mCurrentPlaylistCursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                COLUMNS,
                buildPlayQueueWhereClause(playQueue!!),
                null,
                MediaStore.Audio.AudioColumns._ID)

        if (mCurrentPlaylistCursor == null) {
            mSize = 0
            return
        }

        val size = mCurrentPlaylistCursor!!.count
        mCursorIdxs = LongArray(size)
        mCurrentPlaylistCursor!!.moveToFirst()
        val columnIndex = mCurrentPlaylistCursor!!.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID)
        for (i in 0 until size) {
            mCursorIdxs!![i] = mCurrentPlaylistCursor!!.getLong(columnIndex)
            mCurrentPlaylistCursor!!.moveToNext()
        }
        mCurrentPlaylistCursor!!.moveToFirst()
        mCurPos = -1

        // At this point we can verify the 'now playing' list we got
        // earlier to make sure that all the items in there still exist
        // in the database, and remove those that aren't. This way we
        // don't get any blank items in the list.
        var removed = 0
        for (i in playQueue!!.indices.reversed()) {
            val trackId = playQueue!![i]
            val cursorIndex = Arrays.binarySearch(mCursorIdxs, trackId)
            if (cursorIndex < 0) {
                Log.i(TAG, "item no longer exists in db: " + trackId)
                removed += service.removeTrack(trackId)
            }
        }
        if (removed > 0) {
            playQueue = service.queue
            mSize = playQueue!!.size
            if (mSize == 0) {
                mCursorIdxs = null
            }
        }
    }

    private fun buildPlayQueueWhereClause(playQueue: LongArray): String {
        val where = StringBuilder()
        where.append(MediaStore.Audio.AudioColumns._ID + " IN (")
        for (i in playQueue.indices) {
            where.append(playQueue[i])
            if (i < playQueue.size - 1) {
                where.append(",")
            }
        }
        where.append(")")
        return where.toString()
    }

    override fun getCount(): Int = mSize

    override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
        if (oldPosition == newPosition) {
            return true
        }

        if (playQueue == null || mCursorIdxs == null || newPosition >= playQueue!!.size) {
            return false
        }

        // The cursor doesn't have any duplicates in it, and is not ordered
        // in queue-order, so we need to figure out where in the cursor we should be.

        val newId = playQueue!![newPosition]
        val cursorIndex = Arrays.binarySearch(mCursorIdxs, newId)
        mCurrentPlaylistCursor!!.moveToPosition(cursorIndex)
        mCurPos = newPosition

        return true
    }

    fun removeItem(which: Int): Boolean {
        if (service.removeTracks(which, which) == 0) {
            return false // delete failed
        }
        mSize--
        var i = which
        while (i < mSize) {
            playQueue!![i] = playQueue!![i + 1]
            i++
        }
        onMove(-1, mCurPos)
        return true
    }

    fun moveItem(from: Int, to: Int) {
        service.moveQueueItem(from, to)
        playQueue = service.queue
        onMove(-1, mCurPos) // update the underlying cursor
    }

    override fun getString(column: Int): String? =
        try {
            mCurrentPlaylistCursor!!.getString(column)
        } catch (e: Exception) {
            onChange(true)
            ""
        }

    override fun getShort(column: Int): Short = mCurrentPlaylistCursor!!.getShort(column)

    override fun getInt(column: Int): Int =
        try {
            mCurrentPlaylistCursor!!.getInt(column)
        } catch (e: Exception) {
            onChange(true)
            0
        }

    override fun getLong(column: Int): Long =
        try {
            mCurrentPlaylistCursor!!.getLong(column)
        } catch (e: Exception) {
            onChange(true)
            0
        }

    override fun getFloat(column: Int): Float = mCurrentPlaylistCursor!!.getFloat(column)

    override fun getDouble(column: Int): Double = mCurrentPlaylistCursor!!.getDouble(column)

    override fun isNull(column: Int): Boolean = mCurrentPlaylistCursor!!.isNull(column)

    override fun getColumnNames(): Array<String> = COLUMNS

    override fun deactivate() {
        mCurrentPlaylistCursor?.deactivate()
    }

    override fun requery(): Boolean {
        init()
        return true
    }

    override fun close() {
        mCurrentPlaylistCursor?.close()
        super.close()
    }

}
