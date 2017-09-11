/*
 * Copyright (C) 2007 The Android Open Source Project
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

package nu.staldal.djdplayer.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Toast
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R

class CreatePlaylist : DialogFragment() {

    private var mPlaylist: EditText? = null

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = activity.layoutInflater.inflate(R.layout.create_playlist, null)
        mPlaylist = view.findViewById(R.id.playlist) as EditText

        val defaultName = savedInstanceState?.getString("defaultname") ?: makePlaylistName()

        mPlaylist!!.setText(defaultName)
        mPlaylist!!.setSelection(defaultName!!.length)

        return AlertDialog.Builder(activity)
                .setTitle(R.string.create_playlist_create_text_prompt)
                .setView(view)
                .setNegativeButton(R.string.cancel) { dialog, which -> this@CreatePlaylist.dialog.cancel() }
                .setPositiveButton(R.string.create_playlist_create_text) { dialog, which ->
                    val name = mPlaylist!!.text.toString()
                    if (name.length > 0) {
                        if (MusicUtils.playlistExists(activity, name)) {
                            Toast.makeText(activity, R.string.playlist_already_exists, Toast.LENGTH_SHORT).show()
                        } else {
                            val uri = MusicUtils.createPlaylist(activity, name)
                            val songs = arguments.getLongArray("songs")
                            songs?.let { MusicUtils.addToPlaylist(activity, it, Integer.valueOf(uri.lastPathSegment).toLong()) }
                        }
                    }
                    this@CreatePlaylist.dialog.dismiss()
                }.create()
    }

    override fun onSaveInstanceState(outcicle: Bundle) {
        outcicle.putString("defaultname", mPlaylist!!.text.toString())
    }

    private fun makePlaylistName(): String? {
        val template = getString(R.string.new_playlist_name_template)
        var num = 1

        val c = activity.contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Playlists.NAME),
                MediaStore.Audio.Playlists.NAME + " != ''",
                null,
                MediaStore.Audio.Playlists.NAME) ?: return null

        var suggestedName = String.format(template, num++)

        // Need to loop until we've made 1 full pass through without finding a match.
        // Looping more than once shouldn't happen very often, but will happen if
        // you have playlists named "New Playlist 1"/10/2/3/4/5/6/7/8/9, where
        // making only one pass would result in "New Playlist 10" being erroneously
        // picked for the new name.
        var done = false
        while (!done) {
            done = true
            c.moveToFirst()
            while (!c.isAfterLast) {
                val playlistName = c.getString(0)
                if (playlistName.compareTo(suggestedName, ignoreCase = true) == 0) {
                    suggestedName = String.format(template, num++)
                    done = false
                }
                c.moveToNext()
            }
        }
        c.close()
        return suggestedName
    }

    companion object {
        fun showMe(activity: Activity, songs: LongArray?) {
            val fragment = CreatePlaylist()
            val bundle = Bundle()
            songs?.let { bundle.putLongArray("songs", it) }
            fragment.arguments = bundle
            fragment.show(activity.fragmentManager, "CreatePlaylist")
        }
    }

}
