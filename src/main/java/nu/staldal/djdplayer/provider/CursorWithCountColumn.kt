/*
 * Copyright (C) 2013-2017 Mikael Ståldal
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
package nu.staldal.djdplayer.provider

import android.database.Cursor
import android.database.CursorWrapper
import android.provider.BaseColumns

class CursorWithCountColumn(cursor: Cursor, internal val counts: IntArray) : CursorWrapper(cursor) {

    override fun getColumnCount(): Int {
        return super.getColumnCount() + 1
    }

    override fun getColumnIndex(columnName: String): Int {
        if (columnName == BaseColumns._COUNT)
            return super.getColumnCount()
        else
            return super.getColumnIndex(columnName)
    }

    override fun getColumnIndexOrThrow(columnName: String): Int {
        if (columnName == BaseColumns._COUNT)
            return super.getColumnCount()
        else
            return super.getColumnIndexOrThrow(columnName)
    }

    override fun getColumnName(columnIndex: Int): String {
        if (columnIndex == super.getColumnCount())
            return BaseColumns._COUNT
        else
            return super.getColumnName(columnIndex)
    }

    override fun getColumnNames(): Array<String> {
        val originalColumnNames = super.getColumnNames()
        val ret = arrayOfNulls<String>(originalColumnNames.size + 1)
        System.arraycopy(originalColumnNames, 0, ret, 0, originalColumnNames.size)
        ret[ret.size - 1] = BaseColumns._COUNT
        return ret as Array<String>
    }

    override fun getInt(columnIndex: Int): Int {
        if (columnIndex == super.getColumnCount())
            return counts[position]
        else
            return super.getInt(columnIndex)
    }
}
