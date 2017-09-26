/*
 * Copyright (C) 2015-2017 Mikael Ståldal
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

import java.io.File

interface MediaPlayback {

    companion object {
        /**
         * Used to specify whether enqueue() should start playing the new list of files right away,
         * next or once all the currently queued files have been played.
         */
        const val NOW = 1
        const val NEXT = 2
        const val LAST = 3

        const val REPEAT_NONE = 0
        const val REPEAT_CURRENT = 1
        const val REPEAT_ALL = 2
        const val REPEAT_STOPAFTER = 3
    }

    /**
     * Returns the current play list.
     *
     * @return An array of integers containing the IDs of the tracks in the play list
     */
    val queue: LongArray

    val queueLength: Int

    /**
     * Returns whether something is currently playing.
     *
     * @return true if something is playing (or will be playing shortly, in case
     * we're currently transitioning between tracks), false if not.
     */
    val isPlaying: Boolean

    var repeatMode: Int

    /**
     * Returns the rowid of the currently playing file, or -1 if
     * no file is currently playing.
     */
    val audioId: Long

    /**
     * Returns the rowid of the currently crossfading file, or -1 if
     * no file is currently crossfading.
     */
    val crossfadeAudioId: Long

    /**
     * The position in the queue.
     */
    var queuePosition: Int

    /**
     * Returns the position in the queue currently crossfading file, or -1 if
     * no file is currently crossfading.
     */
    val crossfadeQueuePosition: Int

    val artistName: String?

    val artistId: Long

    val albumName: String?

    val albumId: Long

    val genreName: String?

    val genreId: Long

    val mimeType: String?

    val folder: File?

    val trackName: String?

    /**
     * Returns the audio session ID.
     */
    val audioSessionId: Int


    /**
     * Appends a list of tracks to the current playlist.
     * If nothing is playing currently, playback will be started at
     * the first track.
     * If the action is NOW, playback will switch to the first of
     * the new tracks immediately.
     *
     * @param list   The list of tracks to append.
     * @param action NOW, NEXT or LAST
     */
    fun enqueue(list: LongArray, action: Int)

    fun interleave(newList: LongArray, currentCount: Int, newCount: Int)

    /**
     * Replaces the current playlist with a new list,
     * and prepares for starting playback at the specified
     * position in the list.
     *
     * @param list The new list of tracks.
     */
    fun load(list: LongArray, position: Int)

    /**
     * Moves the item at index1 to index2.
     */
    fun moveQueueItem(index1: Int, index2: Int)

    /**
     * Starts playback of a previously opened file.
     */
    fun play()

    /**
     * Pauses playback (call play() to resume)
     */
    fun pause()

    fun previousOrRestartCurrent()

    fun previous()

    fun next()

    /**
     * Removes the range of tracks specified from the play list. If a file within the range is
     * the file currently being played, playback will move to the next file after the
     * range.
     *
     * @param first The first file to be removed
     * @param last  The last file to be removed
     * @return the number of tracks deleted
     */
    fun removeTracks(first: Int, last: Int): Int

    /**
     * Removes all instances of the track with the given id
     * from the playlist.
     *
     * @param id The id to be removed
     * @return how many instances of the track were removed
     */
    fun removeTrack(id: Long): Int

    fun doShuffle()

    fun uniqueify()

    /**
     * Returns the duration of the file in milliseconds.
     * Currently this method returns -1 for the duration of MIDI files.
     */
    fun duration(): Long

    /**
     * Returns the current playback position in milliseconds
     */
    fun position(): Long

    /**
     * Seeks to the position specified.
     *
     * @param position The position to seek to, in milliseconds
     */
    fun seek(position: Long)
}
