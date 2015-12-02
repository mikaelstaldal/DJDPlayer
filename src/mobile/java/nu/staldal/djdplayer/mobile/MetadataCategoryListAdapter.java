/*
 * Copyright (C) 2012-2013 Mikael Ståldal
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

package nu.staldal.djdplayer.mobile;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import nu.staldal.djdplayer.MusicAlphabetIndexer;
import nu.staldal.djdplayer.R;

public class MetadataCategoryListAdapter extends SimpleCursorAdapterWithContextMenu implements SectionIndexer {
    protected final Resources mResources;
    protected final String mUnknown;

    protected AlphabetIndexer mIndexer;
    protected MetadataCategoryFragment mActivity = null;

    protected static class ViewHolder {
        TextView line1;
        TextView line2;
        ImageView play_indicator;
    }

    public MetadataCategoryListAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to,
                                       MetadataCategoryFragment currentActivity) {
        super(context, layout, cursor, from, to, 0);
        this.mActivity = currentActivity;
        this.mUnknown = context.getString(mActivity.getUnknownStringId());

        getIndexer(cursor);
        mResources = context.getResources();
    }

    @Override
    public Cursor swapCursor(Cursor c) {
        Cursor res = super.swapCursor(c);
        getIndexer(c);
        return res;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
       View v = super.newView(context, cursor, parent);
       ViewHolder vh = new ViewHolder();
       vh.line1 = (TextView) v.findViewById(R.id.line1);
       vh.line2 = (TextView) v.findViewById(R.id.line2);
       vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
       v.setTag(vh);
       return v;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder vh = (ViewHolder) view.getTag();

        String name = mActivity.fetchCategoryName(cursor);
        String displayname = name;
        boolean unknown = name == null || name.equals(MediaStore.UNKNOWN_STRING);
        if (unknown) {
            displayname = mUnknown;
        }
        vh.line1.setText(displayname);

        long id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));

        int numSongs = cursor.getInt(cursor.getColumnIndexOrThrow(mActivity.getNumberOfSongsColumnName()));
        vh.line2.setText(mResources.getQuantityString(R.plurals.Nsongs, numSongs, numSongs));

        long currentId = mActivity.fetchCurrentlyPlayingCategoryId();
        ImageView iv = vh.play_indicator;
        if (currentId == id) {
            iv.setVisibility(View.VISIBLE);
        } else {
            iv.setVisibility(View.INVISIBLE);
        }
    }

    protected void getIndexer(Cursor cursor) {
        if (cursor != null) {
            if (mIndexer != null) {
                mIndexer.setCursor(cursor);
            } else {
                mIndexer = new MusicAlphabetIndexer(cursor, mActivity.getNameColumnIndex(cursor),
                        mResources.getString(R.string.fast_scroll_alphabet));
            }
        }
    }

    public Object[] getSections() {
        return mIndexer.getSections();
    }

    public int getPositionForSection(int section) {
        return mIndexer.getPositionForSection(section);
    }

    public int getSectionForPosition(int position) {
        return 0;
    }
}
