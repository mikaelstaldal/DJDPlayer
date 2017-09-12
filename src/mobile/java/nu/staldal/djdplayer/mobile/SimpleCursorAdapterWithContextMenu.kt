/*
 * Copyright (C) 2014-2017 Mikael Ståldal
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
import android.view.View
import android.view.ViewGroup
import android.widget.SimpleCursorAdapter
import nu.staldal.djdplayer.R

open class SimpleCursorAdapterWithContextMenu(context: Context, layout: Int, c: Cursor?,
                                              from: Array<String>, to: IntArray, flags: Int)
    : SimpleCursorAdapter(context, layout, c, from, to, flags) {

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        val view = super.newView(context, cursor, parent)
        view.findViewById(R.id.context_menu).setOnClickListener { it.showContextMenu() }
        return view
    }
}
