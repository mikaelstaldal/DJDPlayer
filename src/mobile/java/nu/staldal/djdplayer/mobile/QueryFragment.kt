/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.SearchManager
import android.content.*
import android.database.Cursor
import android.database.DatabaseUtils
import android.net.Uri
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.MediaStore
import android.view.ContextMenu
import android.view.View
import android.view.ViewGroup
import android.widget.*
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.provider.MusicContract

private const val LOGTAG = "QueryFragment"

class QueryFragment : TrackFragment() {

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfoIn: ContextMenu.ContextMenuInfo?) {
        if (menuInfoIn == null) return
        val mi = menuInfoIn as AdapterView.AdapterContextMenuInfo
        selectedPosition = mi.position
        adapter!!.cursor.moveToPosition(selectedPosition)
        val mimeType = adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE))
        if (isSong(mimeType)) {
            super.onCreateContextMenu(menu, view, menuInfoIn)
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        selectedPosition = position
        adapter!!.cursor.moveToPosition(selectedPosition)
        val mimeType = adapter!!.cursor.getString(adapter!!.cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE))
        if ("artist" == mimeType) {
            viewCategory(MusicContract.Artist.getMembersUri(id))
        } else if ("album" == mimeType) {
            viewCategory(MusicContract.Album.getMembersUri(id))
        } else if (isSong(mimeType)) {
            super.onListItemClick(l, v, position, id)
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val intent = activity.intent

        var mFilterString: String? = intent.getStringExtra(SearchManager.QUERY)
        if (MediaStore.INTENT_ACTION_MEDIA_SEARCH == intent.action) {
            val focus = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
            val artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
            val album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
            val title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
            if (focus != null) {
                if (focus.startsWith("audio/") && title != null) {
                    mFilterString = title
                } else if (focus == MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE) {
                    if (album != null) {
                        mFilterString = album
                        if (artist != null) {
                            mFilterString = mFilterString + " " + artist
                        }
                    }
                } else if (focus == MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE) {
                    if (artist != null) {
                        mFilterString = artist
                    }
                }
            }
        }
        if (mFilterString == null) mFilterString = ""

        val ccols = arrayOf(BaseColumns._ID, // this will be the artist, album or track ID
                MediaStore.Audio.AudioColumns.MIME_TYPE, // mimetype of audio file, or "artist" or "album"
                MediaStore.Audio.Artists.ARTIST, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.AudioColumns.TITLE, "data1", "data2")

        val search = Uri.parse("content://media/external/audio/search/fancy/" + Uri.encode(mFilterString))

        return CursorLoader(activity, search, ccols, null, null, null)
    }

    override fun createListAdapter(): CursorAdapter =
        QueryListAdapter(
            context = activity,
            layout = R.layout.track_list_item,
            cursor = null,
            from = arrayOf(),
            to = intArrayOf())

    internal class QueryListAdapter(context: Context, layout: Int, cursor: Cursor?, from: Array<String>, to: IntArray) :
            SimpleCursorAdapterWithContextMenu(context, layout, cursor, from, to, 0) {

        override fun bindView(view: View, context: Context, cursor: Cursor) {
            val tv1 = view.findViewById(R.id.line1) as TextView
            val tv2 = view.findViewById(R.id.line2) as TextView
            val iv = view.findViewById(R.id.icon) as ImageView
            val p = iv.layoutParams
            if (p == null) {
                // seen this happen, not sure why
                DatabaseUtils.dumpCursor(cursor)
                return
            }
            p.width = ViewGroup.LayoutParams.WRAP_CONTENT
            p.height = ViewGroup.LayoutParams.WRAP_CONTENT

            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE))
            if ("artist" == mimeType) {
                iv.setImageResource(R.drawable.ic_mp_artist_list)
                val name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Artists.ARTIST))
                var displayname = name
                var isunknown = false
                if (name == null || name == MediaStore.UNKNOWN_STRING) {
                    displayname = context.getString(R.string.unknown_artist_name)
                    isunknown = true
                }
                tv1.text = displayname

                val numalbums = cursor.getInt(cursor.getColumnIndexOrThrow("data1"))
                val numsongs = cursor.getInt(cursor.getColumnIndexOrThrow("data2"))

                val songs_albums = MusicUtils.makeAlbumsSongsLabel(context,
                        numalbums, numsongs, isunknown)

                tv2.text = songs_albums
            } else if ("album" == mimeType) {
                iv.setImageResource(R.drawable.albumart_mp_unknown_list)
                var name: String? = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Albums.ALBUM))
                var displayname = name
                if (name == null || name == MediaStore.UNKNOWN_STRING) {
                    displayname = context.getString(R.string.unknown_album_name)
                }
                tv1.text = displayname

                name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Artists.ARTIST))
                displayname = name
                if (name == null || name == MediaStore.UNKNOWN_STRING) {
                    displayname = context.getString(R.string.unknown_artist_name)
                }
                tv2.text = displayname
            } else if (isSong(mimeType)) {
                iv.setImageResource(R.drawable.ic_mp_song_list)
                var name: String? = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.AudioColumns.TITLE))
                tv1.text = name

                var displayname: String? = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Artists.ARTIST))
                if (displayname == null || displayname == MediaStore.UNKNOWN_STRING) {
                    displayname = context.getString(R.string.unknown_artist_name)
                }
                name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Albums.ALBUM))
                if (name == null || name == MediaStore.UNKNOWN_STRING) {
                    name = context.getString(R.string.unknown_album_name)
                }
                tv2.text = displayname + " - " + name
            }
        }
    }

    companion object {
        private fun isSong(mimeType: String?) =
            mimeType == null ||
                mimeType.startsWith("audio/") ||
                mimeType == "application/ogg" ||
                mimeType == "application/x-ogg"
    }

}
