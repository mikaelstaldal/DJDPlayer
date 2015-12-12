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

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v17.leanback.database.CursorMapper;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.TitleView;
import android.support.v17.leanback.widget.VerticalGridView;
import android.util.Log;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;

public class CategoryDetailsActivity extends Activity implements
        LoaderManager.LoaderCallbacks<Cursor>, ServiceConnection, OnItemViewClickedListener {

    private static final String TAG = CategoryDetailsActivity.class.getSimpleName();

    public static final String TITLE = "title";

    private MusicUtils.ServiceToken token = null;
    private MediaPlayback service = null;

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

        token = MusicUtils.bindToService(this, this, TvMediaPlaybackService.class);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((MediaPlaybackService.LocalBinder) binder).getService();

        getLoaderManager().initLoader(0, null, this);
    }

    private void buildUI() {
        setContentView(R.layout.tv_category_details_activity);

        TitleView categoryTitle = (TitleView)findViewById(R.id.category_title);
        categoryTitle.setTitle(title);

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
        SongItem song = (SongItem)item;

        MusicUtils.playSong(this, song.id);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;

        finish();
    }

    @Override
    public void onDestroy() {
        if (token != null) MusicUtils.unbindFromService(token);
        service = null;

        super.onDestroy();
    }

}
