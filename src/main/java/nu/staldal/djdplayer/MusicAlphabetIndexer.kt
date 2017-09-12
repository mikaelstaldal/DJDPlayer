/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2017 Mikael Ståldal
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

import android.database.Cursor
import android.provider.MediaStore
import android.widget.AlphabetIndexer

/**
 * Handles comparisons in a different way because the Album, Song and Artist name
 * are stripped of some prefixes such as "a", "an", "the" and some symbols.
 */
class MusicAlphabetIndexer(cursor: Cursor, sortedColumnIndex: Int, alphabet: CharSequence)
        : AlphabetIndexer(cursor, sortedColumnIndex, alphabet) {

    override fun compare(word: String, letter: String): Int {
        val wordKey = MediaStore.Audio.keyFor(word)
        val letterKey = MediaStore.Audio.keyFor(letter)
        return if (wordKey.startsWith(letter)) {
            0
        } else {
            wordKey.compareTo(letterKey)
        }
    }
}
