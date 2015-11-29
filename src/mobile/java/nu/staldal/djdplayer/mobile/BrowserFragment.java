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

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CursorAdapter;
import nu.staldal.djdplayer.MediaPlaybackService;

public abstract class BrowserFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    protected CursorAdapter adapter;

    protected abstract CursorAdapter createListAdapter();

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapter = createListAdapter();
        setListAdapter(adapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in. (The framework will take care of closing the old cursor once we return.)
        adapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no longer using it.
        adapter.swapCursor(null);
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

    public boolean isPicking() {
        return Intent.ACTION_PICK.equals(getActivity().getIntent().getAction())
                || Intent.ACTION_GET_CONTENT.equals(getActivity().getIntent().getAction());
    }

    protected void viewCategory(Uri uri) {
        ((MusicBrowserActivity)getActivity()).onNewIntent(new Intent(
                isPicking() ? Intent.ACTION_PICK : Intent.ACTION_VIEW,
                uri));
    }
}
