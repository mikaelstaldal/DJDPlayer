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
package nu.staldal.djdplayer.tv;

import android.database.Cursor;
import android.support.v17.leanback.database.CursorMapper;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.Row;
import nu.staldal.djdplayer.provider.MusicContract;

class CategoryMapper extends CursorMapper {
    public int countColumn;
    public int nameColumn;
    public int idColumn;

    @Override
    protected void bindColumns(Cursor cursor) {
        idColumn = cursor.getColumnIndexOrThrow(MusicContract.Artist._ID);
        nameColumn = cursor.getColumnIndexOrThrow(MusicContract.Artist.NAME);
        countColumn = cursor.getColumnIndexOrThrow(MusicContract.Artist.NAME);
    }

    @Override
    protected Object bind(Cursor cursor) {
        // return new CategoryItem(cursor.getLong(idColumn), cursor.getString(nameColumn), cursor.getInt(countColumn));
        return new Row(cursor.getLong(idColumn), new HeaderItem(cursor.getLong(idColumn), cursor.getString(nameColumn)));
    }
}
