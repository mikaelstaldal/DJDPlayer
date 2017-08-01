/*
 * Copyright (C) 2012-2017 Mikael Ståldal
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

import android.app.Activity
import android.app.Dialog
import android.app.DialogFragment
import android.app.LoaderManager
import android.content.CursorLoader
import android.content.Loader
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import java.io.File
import java.text.DateFormat
import java.util.*

class TrackInfoFragment : DialogFragment(), LoaderManager.LoaderCallbacks<Cursor> {
    companion object {
        fun showMe(activity: Activity, uri: Uri) {
            val trackInfoFragment = TrackInfoFragment()
            val bundle = Bundle()
            bundle.putString("uri", uri.toString())
            trackInfoFragment.arguments = bundle
            trackInfoFragment.show(activity.fragmentManager, "TrackInfo")
        }

        private val COLUMNS = arrayOf(
                MediaStore.Audio.AudioColumns._ID,
                MediaStore.Audio.AudioColumns.DATA,
                MediaStore.Audio.AudioColumns.MIME_TYPE,
                MediaStore.Audio.AudioColumns.TITLE,
                MediaStore.Audio.AudioColumns.ALBUM,
                MediaStore.Audio.AudioColumns.ARTIST,
                MediaStore.Audio.AudioColumns.COMPOSER,
                MediaStore.Audio.AudioColumns.DURATION,
                MediaStore.Audio.AudioColumns.YEAR,
                MediaStore.Audio.AudioColumns.DATE_ADDED
        )
    }

    private val dateFormat = DateFormat.getDateInstance()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        loaderManager.initLoader(0, arguments, this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.track_info, container, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateLoader(id: Int, args: Bundle?) =
            CursorLoader(activity, Uri.parse(args!!.getString("uri")), COLUMNS, null, null, null)

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        // Swap the new cursor in. (The framework will take care of closing the old cursor once we return.)
        if (data != null) {
            if (data.moveToFirst()) {
                bindView(data)
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {}

    private fun bindView(cursor: Cursor) {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID))
        val file = File(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA)))
        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE))
        val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM))
        val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST))
        val composer = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.COMPOSER))
        val duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION))
        val year = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.YEAR))
        val dateAdded = Date(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATE_ADDED)) * 1000)

        (view.findViewById(R.id.title) as TextView).text = title
        (view.findViewById(R.id.artist) as TextView).text = artist
        (view.findViewById(R.id.composer) as TextView).text = composer
        (view.findViewById(R.id.album) as TextView).text = album
        val genre = MusicUtils.fetchGenre(activity, id)
        if (genre != null) {
            (view.findViewById(R.id.genre) as TextView).text = genre.name
        }
        (view.findViewById(R.id.year) as TextView).text = year
        (view.findViewById(R.id.duration) as TextView).text = MusicUtils.formatDuration(activity, duration.toLong())
        (view.findViewById(R.id.folder) as TextView).text = file.parent
        (view.findViewById(R.id.filename) as TextView).text = file.name
        (view.findViewById(R.id.filesize) as TextView).text = formatFileSize(file.length())
        (view.findViewById(R.id.mimetype) as TextView).text = mimeType
        (view.findViewById(R.id.date_added) as TextView).text = formatDate(dateAdded)
        (view.findViewById(R.id.id) as TextView).text = id.toString()
    }

    private fun formatFileSize(size: Long): String {
        return (size / 1024).toString() + " KiB"
    }

    private fun formatDate(timestamp: Date): String {
        return dateFormat.format(timestamp)
    }

}