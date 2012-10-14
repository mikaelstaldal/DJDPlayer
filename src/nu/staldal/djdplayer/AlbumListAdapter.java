/*
 * Copyright (C) 2012 Mikael Ståldal
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

package nu.staldal.djdplayer;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;

class AlbumListAdapter extends CategoryListAdapter {
    AlbumListAdapter(Context context, AlbumBrowserActivity currentActivity,
                     int layout, Cursor cursor, String[] from, int[] to) {
        super(context, layout, cursor, from, to, currentActivity, context.getString(R.string.unknown_album_name));
    }

    @Override
    protected void getColumnIndices(Cursor cursor) {
        if (cursor != null) {
            mIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID);
            mNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM);
        }
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder vh = (ViewHolder) view.getTag();

        String name = cursor.getString(mNameIndex);
        String displayname = name;
        boolean unknown = name == null || name.equals(MediaStore.UNKNOWN_STRING);
        if (unknown) {
            displayname = mUnknown;
        }
        vh.line1.setText(displayname);

        long id = cursor.getLong(mIdIndex);
        long currentId = MusicUtils.getCurrentAlbumId();
        ImageView iv = vh.play_indicator;
        if (currentId == id) {
            iv.setImageDrawable(mNowPlayingOverlay);
        } else {
            iv.setImageDrawable(null);
        }
    }
}
