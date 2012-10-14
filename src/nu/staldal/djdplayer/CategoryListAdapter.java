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

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class CategoryListAdapter extends SimpleCursorAdapter implements SectionIndexer {
    protected final Drawable mNowPlayingOverlay;
    protected final Resources mResources;
    protected final String mUnknown;
    protected AlphabetIndexer mIndexer;
    protected CategoryBrowserActivity mActivity;
    protected AsyncQueryHandler mQueryHandler;
    protected String mConstraint = null;
    protected boolean mConstraintIsValid = false;

    protected static class ViewHolder {
        TextView line1;
        TextView line2;
        ImageView play_indicator;
    }

    protected class QueryHandler extends AsyncQueryHandler {
        QueryHandler(ContentResolver res) {
            super(res);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            //Log.i("@@@", "query complete");
            mActivity.init(cursor);
        }
    }

    public CategoryListAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to,
                               CategoryBrowserActivity currentActivity) {
        super(context, layout, cursor, from, to);
        this.mActivity = currentActivity;
        this.mUnknown = context.getString(mActivity.getUnknownStringId());
        mQueryHandler = new QueryHandler(context.getContentResolver());

        Resources r = context.getResources();
        mNowPlayingOverlay = r.getDrawable(R.drawable.indicator_ic_mp_playing_list);

        getIndexer(cursor);
        mResources = context.getResources();
    }

    public void setActivity(CategoryBrowserActivity newActivity) {
        mActivity = newActivity;
    }

    public AsyncQueryHandler getQueryHandler() {
        return mQueryHandler;
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

        long id = mActivity.fetchCategoryId(cursor);

        int numSongs = mActivity.fetchNumberOfSongsForCategory(cursor, id);
        if (numSongs > 0) {
            vh.line2.setText(mResources.getQuantityString(R.plurals.Nsongs, numSongs, numSongs));
        }

        long currentId = mActivity.fetchCurrentlyPlayingCategoryId();
        ImageView iv = vh.play_indicator;
        if (currentId == id) {
            iv.setImageDrawable(mNowPlayingOverlay);
        } else {
            iv.setImageDrawable(null);
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

    @Override
    public void changeCursor(Cursor cursor) {
        if (mActivity.isFinishing() && cursor != null) {
            cursor.close();
            cursor = null;
        }
        if (cursor != mActivity.mCursor) {
            mActivity.mCursor = cursor;
            getIndexer(cursor);
            super.changeCursor(cursor);
        }
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        String s = constraint.toString();
        if (mConstraintIsValid && (
                (s == null && mConstraint == null) ||
                (s != null && s.equals(mConstraint)))) {
            return getCursor();
        }
        Cursor c = mActivity.getCursor(null, s);
        mConstraint = s;
        mConstraintIsValid = true;
        return c;
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
