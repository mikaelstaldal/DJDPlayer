/*
 * Copyright (C) 2015 Mikael Ståldal
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

import java.io.File;

public interface MediaPlayback {
    /**
     * Used to specify whether enqueue() should start playing the new list of files right away,
     * next or once all the currently queued files have been played.
     */
    int NOW = 1;
    int NEXT = 2;
    int LAST = 3;

    int REPEAT_NONE = 0;
    int REPEAT_CURRENT = 1;
    int REPEAT_ALL = 2;
    int REPEAT_STOPAFTER = 3;


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
    void enqueue(long[] list, int action);

    void interleave(long[] newList, int currentCount, int newCount);

    /**
     * Replaces the current playlist with a new list,
     * and prepares for starting playback at the specified
     * position in the list.
     *
     * @param list The new list of tracks.
     */
    void load(long[] list, int position);

    /**
     * Moves the item at index1 to index2.
     */
    void moveQueueItem(int index1, int index2);

    /**
     * Returns the current play list.
     *
     * @return An array of integers containing the IDs of the tracks in the play list
     */
    long[] getQueue();

    int getQueueLength();

    /**
     * Starts playback of a previously opened file.
     */
    void play();

    /**
     * Pauses playback (call play() to resume)
     */
    void pause();

    /**
     * Returns whether something is currently playing.
     *
     * @return true if something is playing (or will be playing shortly, in case
     * we're currently transitioning between tracks), false if not.
     */
    boolean isPlaying();

    void previousOrRestartCurrent();

    void previous();

    void next();

    /**
     * Removes the range of tracks specified from the play list. If a file within the range is
     * the file currently being played, playback will move to the next file after the
     * range.
     *
     * @param first The first file to be removed
     * @param last  The last file to be removed
     * @return the number of tracks deleted
     */
    int removeTracks(int first, int last);

    /**
     * Removes all instances of the track with the given id
     * from the playlist.
     *
     * @param id The id to be removed
     * @return how many instances of the track were removed
     */
    int removeTrack(long id);

    void doShuffle();

    void uniqueify();

    void setRepeatMode(int repeatmode);

    int getRepeatMode();

    /**
     * Returns the rowid of the currently playing file, or -1 if
     * no file is currently playing.
     */
    long getAudioId();

    /**
     * Returns the rowid of the currently crossfading file, or -1 if
     * no file is currently crossfading.
     */
    long getCrossfadeAudioId();

    /**
     * Returns the position in the queue
     *
     * @return the position in the queue
     */
    int getQueuePosition();

    /**
     * Returns the position in the queue currently crossfading file, or -1 if
     * no file is currently crossfading.
     */
    int getCrossfadeQueuePosition();

    /**
     * Starts playing the track at the given position in the queue.
     *
     * @param pos The position in the queue of the track that will be played.
     */
    void setQueuePosition(int pos);

    String getArtistName();

    long getArtistId();

    String getAlbumName();

    long getAlbumId();

    String getGenreName();

    long getGenreId();

    String getMimeType();

    File getFolder();

    String getTrackName();

    /**
     * Returns the duration of the file in milliseconds.
     * Currently this method returns -1 for the duration of MIDI files.
     */
    long duration();

    /**
     * Returns the current playback position in milliseconds
     */
    long position();

    /**
     * Seeks to the position specified.
     *
     * @param pos The position to seek to, in milliseconds
     */
    void seek(long pos);

    /**
     * Returns the audio session ID.
     */
    int getAudioSessionId();
}
