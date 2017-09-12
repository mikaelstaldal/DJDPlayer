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

import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.*
import nu.staldal.djdplayer.MusicAlphabetIndexer
import nu.staldal.djdplayer.R

class MetadataCategoryListAdapter(context: Context, layout: Int, cursor: Cursor?, from: Array<String>, to: IntArray,
                                  currentFragment: MetadataCategoryFragment)
        : SimpleCursorAdapterWithContextMenu(context, layout, cursor, from, to, 0), SectionIndexer {

    private val mResources = context.resources
    private val mFragment = currentFragment
    private val mUnknownString = context.getString(currentFragment.getUnknownStringId())

    private var mIndexer: AlphabetIndexer? = null

    internal data class ViewHolder(val line1: TextView, val line2: TextView, val play_indicator: ImageView)

    init {
        if (cursor != null) getIndexer(cursor)
    }

    override fun swapCursor(cursor: Cursor?): Cursor? {
        val res = super.swapCursor(cursor)
        if (cursor != null) getIndexer(cursor)
        return res
    }

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        val view = super.newView(context, cursor, parent)
        view.tag = ViewHolder(
                    view.findViewById(R.id.line1) as TextView,
                    view.findViewById(R.id.line2) as TextView,
                    view.findViewById(R.id.play_indicator) as ImageView)
        return view
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val viewHolder = view.tag as ViewHolder

        val name = mFragment.fetchCategoryName(cursor)
        var displayName = name
        val unknown = name == null || name == MediaStore.UNKNOWN_STRING
        if (unknown) {
            displayName = mUnknownString
        }
        viewHolder.line1.text = displayName

        val id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID))

        val numSongs = cursor.getInt(cursor.getColumnIndexOrThrow(mFragment.getNumberOfSongsColumnName()))
        viewHolder.line2.text = mResources.getQuantityString(R.plurals.Nsongs, numSongs, numSongs)

        if (mFragment.fetchCurrentlyPlayingCategoryId() == id) {
            viewHolder.play_indicator.visibility = View.VISIBLE
        } else {
            viewHolder.play_indicator.visibility = View.INVISIBLE
        }
    }

    private fun getIndexer(cursor: Cursor) {
        if (mIndexer != null) {
            mIndexer!!.setCursor(cursor)
        } else {
            mIndexer = MusicAlphabetIndexer(cursor, mFragment.getNameColumnIndex(cursor),
                    mResources.getString(R.string.fast_scroll_alphabet))
        }
    }

    override fun getSections(): Array<Any> = mIndexer!!.sections

    override fun getPositionForSection(section: Int): Int = mIndexer!!.getPositionForSection(section)

    override fun getSectionForPosition(position: Int): Int = 0

}
