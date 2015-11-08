/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012-2015 Mikael Ståldal
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v17.leanback.database.CursorMapper;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridView;
import android.util.Log;
import android.widget.TextView;
import nu.staldal.djdplayer.R;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CategoryDetailsActivity extends Activity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemViewClickedListener {

    private static final String TAG = CategoryDetailsActivity.class.getSimpleName();

    public static final String TITLE = "title";

    private Uri uri;
    private String title;

    private CursorObjectAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate - " + getIntent());

        uri = getIntent().getData();
        title = getIntent().getStringExtra(TITLE);

        buildUI();

        getLoaderManager().initLoader(0, null, this);
    }

    private void buildUI() {
        setContentView(R.layout.tv_category_details_activity);

        TextView categoryTitle = (TextView)findViewById(R.id.category_title);
        categoryTitle.setText(title);

        /*
        ArrayObjectAdapter actionsAdapter = new ArrayObjectAdapter();
        actionsAdapter.add(new Action(R.id.play_all_now_action, getActivity().getString(R.string.play_all_now)));
        actionsAdapter.add(new Action(R.id.play_all_next_action, getActivity().getString(R.string.play_all_next)));
        actionsAdapter.add(new Action(R.id.queue_all_last_action, getActivity().getString(R.string.queue_all)));
        detailsOverview.setActionsAdapter(actionsAdapter);
        adapter.add(detailsOverview);
        */

        VerticalGridView list = (VerticalGridView) findViewById(android.R.id.list);
        adapter = new CursorObjectAdapter(new SongRowPresenter());
        list.setAdapter(new ClickableItemBridgeAdapter(adapter, this));
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, uri, null, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        adapter.setMapper(new CursorMapper() {
            private int durationColumnIndex;
            private int artistColumnIndex;
            private int titleColumnIndex;
            private int idColumnIndex;

            @Override
            protected void bindColumns(Cursor cursor) {
                idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID);
                titleColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE);
                artistColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST);
                durationColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION);
            }

            @Override
            protected Object bind(Cursor cursor) {
                return new SongItem(
                        cursor.getLong(idColumnIndex),
                        cursor.getString(titleColumnIndex),
                        cursor.getString(artistColumnIndex),
                        cursor.getInt(durationColumnIndex));
            }
        });
        adapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        adapter.changeCursor(null);
    }

    /*
    @Override
    public void onActionClicked(Action action) {
        switch ((int)action.getId()) {
            case R.id.play_all_now_action:
                Log.d(TAG, "play all now");
                break;

            case R.id.play_all_next_action:
                Log.d(TAG, "play all next");
                break;

            case R.id.queue_all_last_action:
                Log.d(TAG, "queue all last");
                break;
        }
    }
    */

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                              RowPresenter.ViewHolder rowViewHolder, Row row) {
        Log.i(TAG, "item: " + item.toString());

    }
}
