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

import android.annotation.TargetApi;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.os.Build;
import android.os.Bundle;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.Log;
import android.widget.Toast;
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.R;

import java.util.List;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BrowserFragment extends BrowseFragment implements FragmentServiceConnection,
        LoaderManager.LoaderCallbacks<Map<String,List<CategoryItem>>>, OnItemViewClickedListener {

    private static final String TAG = BrowserFragment.class.getSimpleName();

    private MediaPlayback service;
    private ArrayObjectAdapter adapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.i(TAG, "onActivityCreated");

        getLoaderManager().initLoader(0, null, this);

        setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.app_banner, null));
        setTitle(getString(R.string.applabel));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        setOnItemViewClickedListener(this);
    }

    @Override
    public Loader<Map<String,List<CategoryItem>>> onCreateLoader(int id, Bundle args) {
        return new CategoryLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Map<String,List<CategoryItem>>> loader, Map<String,List<CategoryItem>> data) {
        adapter = new ArrayObjectAdapter(new ListRowPresenter());
        CategoryPresenter presenter = new CategoryPresenter();

        int index = 0;

        if (null != data && !data.isEmpty()) {
            for (Map.Entry<String, List<CategoryItem>> entry : data.entrySet()) {
                ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(presenter);

                for (CategoryItem item : entry.getValue()) {
                    listRowAdapter.add(item);
                }

                HeaderItem header = new HeaderItem(index, entry.getKey());
                index++;
                adapter.add(new ListRow(header, listRowAdapter));
            }
        } else {
            Log.e(TAG, "An error occurred fetching music");
            Toast.makeText(getActivity(), R.string.error_fetching_music, Toast.LENGTH_LONG).show();
        }

        setAdapter(adapter);
    }

    @Override
    public void onLoaderReset(Loader<Map<String,List<CategoryItem>>> loader) {
        adapter.clear();
    }

    @Override
    public void onServiceConnected(MediaPlayback s) {
        Log.i(TAG, "onServiceConnected");
        service = s;
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        getActivity().registerReceiver(statusListener, f);
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object o,
                              RowPresenter.ViewHolder rowViewHolder, Row row) {
        if (o instanceof CategoryItem) {
            CategoryItem item = (CategoryItem) o;
            Log.d(TAG, "onItemClicked: row=" + row.getId() + " " + item.toString());
        }
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(statusListener);
        super.onPause();
    }

    private final BroadcastReceiver statusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getLoaderManager().restartLoader(0, null, BrowserFragment.this);
        }
    };

    @Override
    public void onServiceDisconnected() {
        Log.i(TAG, "onServiceDisconnected");
        service = null;
    }

}
