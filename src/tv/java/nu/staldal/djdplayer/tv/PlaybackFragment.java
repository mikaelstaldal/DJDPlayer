/*
 * Copyright (C) 2015-2016 Mikael Ståldal
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

import android.os.Bundle;
import android.support.v17.leanback.app.PlaybackOverlayFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ControlButtonPresenterSelector;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.util.Log;
import nu.staldal.djdplayer.FragmentServiceConnection;
import nu.staldal.djdplayer.MediaPlayback;
import nu.staldal.djdplayer.R;

public class PlaybackFragment extends PlaybackOverlayFragment implements FragmentServiceConnection {

    private static final String TAG = PlaybackFragment.class.getSimpleName();

    private MediaPlayback service;

    private PlaybackControlsRow.PlayPauseAction playPauseAction;
    private PlaybackControlsRow.SkipNextAction skipNextAction;
    private PlaybackControlsRow.SkipPreviousAction skipPreviousAction;
    private ArrayObjectAdapter primaryActionsAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.i(TAG, "onActivityCreated");

        setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.app_banner, null));
        setTitle(getString(R.string.applabel));

        PlaybackControlsRowPresenter playbackControlsRowPresenter = new PlaybackControlsRowPresenter();

        playbackControlsRowPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            public void onActionClicked(Action action) {
                if (action.getId() == playPauseAction.getId()) {
                    if (playPauseAction.getIndex() == PlaybackControlsRow.PlayPauseAction.PLAY) {
                        service.play();
                        updatePlayPauseAction(PlaybackControlsRow.PlayPauseAction.PAUSE);
                    } else if (playPauseAction.getIndex() == PlaybackControlsRow.PlayPauseAction.PAUSE) {
                        service.pause();
                        updatePlayPauseAction(PlaybackControlsRow.PlayPauseAction.PLAY);
                    }
                } else if (action.getId() == skipNextAction.getId()) {
                    service.next();
                } else if (action.getId() == skipPreviousAction.getId()) {
                    service.prev();
                }
            }
        });

        ArrayObjectAdapter adapter = new ArrayObjectAdapter(playbackControlsRowPresenter);

        PlaybackControlsRow playbackControlsRow = new PlaybackControlsRow();
        adapter.add(playbackControlsRow);

        ControlButtonPresenterSelector presenterSelector = new ControlButtonPresenterSelector();
        primaryActionsAdapter = new ArrayObjectAdapter(presenterSelector);
        playbackControlsRow.setPrimaryActionsAdapter(primaryActionsAdapter);

        playPauseAction = new PlaybackControlsRow.PlayPauseAction(getActivity());
        skipNextAction = new PlaybackControlsRow.SkipNextAction(getActivity());
        skipPreviousAction = new PlaybackControlsRow.SkipPreviousAction(getActivity());

        primaryActionsAdapter.add(skipPreviousAction);
        primaryActionsAdapter.add(playPauseAction);
        primaryActionsAdapter.add(skipNextAction);

        setAdapter(adapter);
    }

    @Override
    public void onServiceConnected(MediaPlayback s) {
        Log.i(TAG, "onServiceConnected");
        service = s;

        updatePlayPauseAction(service.isPlaying()
                ? PlaybackControlsRow.PlayPauseAction.PAUSE
                : PlaybackControlsRow.PlayPauseAction.PLAY);
    }

    protected void updatePlayPauseAction(int index) {
        playPauseAction.setIndex(index);
        primaryActionsAdapter.notifyArrayItemRangeChanged(primaryActionsAdapter.indexOf(playPauseAction), 1);
    }

    @Override
    public void onServiceDisconnected() {
        Log.i(TAG, "onServiceDisconnected");
        service = null;
    }

}
