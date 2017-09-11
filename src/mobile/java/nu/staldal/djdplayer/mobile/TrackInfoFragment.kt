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
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import java.io.File
import java.text.DateFormat
import java.util.Date

import kotlinx.android.synthetic.mobile.track_info.*

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
        val file = File(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA)))

        title.text = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE))
        artist.text = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST))
        composer.text = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.COMPOSER))
        album.text = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM))
        genre.text = MusicUtils.fetchGenre(activity,
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID)))?.name
        year.text = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.YEAR))
        duration.text = MusicUtils.formatDuration(activity,
                cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION)).toLong())
        folder.text = file.parent
        filename.text = file.name
        filesize.text = formatFileSize(file.length())
        mimetype.text = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE))
        date_added.text = formatDate(Date(cursor.getLong(
                cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATE_ADDED)) * 1000))
        id_view.text = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID)).toString()
    }

    private fun formatFileSize(size: Long): String {
        return (size / 1024).toString() + " KiB"
    }

    private fun formatDate(timestamp: Date): String {
        return dateFormat.format(timestamp)
    }

}