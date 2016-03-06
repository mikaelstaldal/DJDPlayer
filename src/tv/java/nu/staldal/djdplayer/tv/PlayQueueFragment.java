/*
 * Copyright (C) 2016 Mikael Ståldal
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

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v17.leanback.database.CursorMapper;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.PlayQueueCursor;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.SettingsActivity;
import nu.staldal.leanback.ClickableItemBridgeAdapter;

public class PlayQueueFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>, FragmentServiceConnection, OnItemViewClickedListener {

    @SuppressWarnings("unused")
    private static final String TAG = PlayQueueFragment.class.getSimpleName();

    private MediaPlayback service = null;

    private CursorObjectAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tv_play_queue_fragment, container, false);

        VerticalGridView list = (VerticalGridView) view.findViewById(android.R.id.list);
        adapter = new CursorObjectAdapter(new SongRowPresenter());
        list.setAdapter(new ClickableItemBridgeAdapter(adapter, this));

        return view;
    }

    @Override
    public void onServiceConnected(MediaPlayback s) {
        service = s;
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new AsyncTaskLoader<Cursor>(getActivity()) {
            @Override
            public Cursor loadInBackground() {
                return new PlayQueueCursor(service, getActivity().getContentResolver());
            }

            @Override
            protected void onStartLoading() {
                super.onStartLoading();
                forceLoad();
            }
        };
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
                        cursor.getPosition(),
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

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                              RowPresenter.ViewHolder rowViewHolder, Row row) {
        SongItem song = (SongItem)item;

        if (service != null) {
            String clickOnSong = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(
                    SettingsActivity.CLICK_ON_SONG, SettingsActivity.PLAY_NEXT);
            if (clickOnSong.equals(SettingsActivity.PLAY_NOW)) {
                service.setQueuePosition(song.position);
            } else {
                if (!service.isPlaying()) {
                    service.setQueuePosition(song.position);
                }
            }
        }
    }

    @Override
    public void onServiceDisconnected() {
        service = null;
    }

}
