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
import android.media.audiofx.AudioEffect;
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
import android.support.v17.leanback.widget.SinglePresenterSelector;
import android.util.Log;
import android.widget.Toast;
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.MediaPlaybackService;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.SettingsActivity;
import nu.staldal.djdplayer.provider.MusicContract;

import java.util.List;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BrowserFragment extends BrowseFragment implements FragmentServiceConnection,
        LoaderManager.LoaderCallbacks<List<ListRow>>, OnItemViewClickedListener {

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
    public Loader<List<ListRow>> onCreateLoader(int id, Bundle args) {
        return new CategoryLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<List<ListRow>> loader, List<ListRow> data) {
        adapter = new ArrayObjectAdapter(new ListRowPresenter());

        if (null != data && !data.isEmpty()) {
            CategoryCardPresenter presenter = new CategoryCardPresenter();
            for (ListRow row : data) {
                row.getAdapter().setPresenterSelector(new SinglePresenterSelector(presenter));
                adapter.add(row);
            }
        } else {
            Log.e(TAG, "An error occurred fetching music");
            Toast.makeText(getActivity(), R.string.error_fetching_music, Toast.LENGTH_LONG).show();
        }

        ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(new ActionCardPresenter());
        rowAdapter.add(new SettingsItem(R.id.settings_action, getString(R.string.settings)));
        if (getActivity().getPackageManager().resolveActivity(new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL), 0) != null) {
            rowAdapter.add(new SettingsItem(R.id.effectspanel_action, getString(R.string.effectspanel)));
        }
        adapter.add(new ListRow(new HeaderItem(R.id.settings_section, getString(R.string.settings)), rowAdapter));

        setAdapter(adapter);
    }

    @Override
    public void onLoaderReset(Loader<List<ListRow>> loader) {
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
            Intent intent = new Intent(Intent.ACTION_VIEW);
            switch ((int)row.getId()) {
                case R.id.artists_section:
                    Log.d(TAG, "artist: " + item.toString());
                    intent.setData(MusicContract.Artist.getMembersUri(item.id));
                    break;

                case R.id.albums_section:
                    Log.d(TAG, "album: " + item.toString());
                    intent.setData(MusicContract.Album.getMembersUri(item.id));
                    break;

                case R.id.genres_sections:
                    Log.d(TAG, "genre: " + item.toString());
                    intent.setData(MusicContract.Genre.getMembersUri(item.id));
                    break;

                case R.id.folders_section:
                    Log.d(TAG, "folder: " + item.toString());
                    intent.setData(MusicContract.Folder.getMembersUri(((FolderItem)item).path));
                    break;

                case R.id.playlists_section:
                    Log.d(TAG, "playlist: " + item.toString());
                    intent.setData(MusicContract.Playlist.getMembersUri(item.id));
                    break;
            }
            intent.putExtra(CategoryDetailsActivity.TITLE, item.name);
            intent.setClass(getActivity(), CategoryDetailsActivity.class);
            startActivity(intent);
        } else if (o instanceof SettingsItem) {
            SettingsItem item = (SettingsItem) o;
            switch ((int)item.id) {
                case R.id.settings_action:
                    startActivity(new Intent(getActivity(), SettingsActivity.class));
                    break;

                case R.id.effectspanel_action:
                    Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                    intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, service.getAudioSessionId());
                    startActivityForResult(intent, 0);
                    break;
            }
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
