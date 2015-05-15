/*
 * Copyright (C) 2013 Mikael Ståldal
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
package nu.staldal.djdplayer.provider;

import android.database.Cursor;
import android.database.CursorWrapper;
import android.provider.BaseColumns;

public class CursorWithCountColumn extends CursorWrapper {
    final int[] counts;

    public CursorWithCountColumn(Cursor cursor, int[] counts) {
        super(cursor);
        this.counts = counts;
    }

    @Override
    public int getColumnCount() {
        return super.getColumnCount()+1;
    }

    @Override
    public int getColumnIndex(String columnName) {
        if (columnName.equals(BaseColumns._COUNT))
            return super.getColumnCount();
        else
            return super.getColumnIndex(columnName);
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) {
        if (columnName.equals(BaseColumns._COUNT))
            return super.getColumnCount();
        else
            return super.getColumnIndexOrThrow(columnName);
    }

    @Override
    public String getColumnName(int columnIndex) {
        if (columnIndex == super.getColumnCount())
            return BaseColumns._COUNT;
        else
            return super.getColumnName(columnIndex);
    }

    @Override
    public String[] getColumnNames() {
        String[] originalColumnNames = super.getColumnNames();
        String[] ret = new String[originalColumnNames.length+1];
        System.arraycopy(originalColumnNames, 0, ret, 0, originalColumnNames.length);
        ret[ret.length-1] = BaseColumns._COUNT;
        return ret;
    }

    @Override
    public int getInt(int columnIndex) {
        if (columnIndex == super.getColumnCount())
            return counts[getPosition()];
        else
            return super.getInt(columnIndex);
    }
}
