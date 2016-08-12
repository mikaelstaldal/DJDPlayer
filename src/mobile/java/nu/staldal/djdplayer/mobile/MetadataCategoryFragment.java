/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2012-2016 Mikael Ståldal
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

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;

public abstract class MetadataCategoryFragment extends CategoryFragment {
    protected static final String CURRENT_COUNT = "currentCount";
    protected static final String NEW_COUNT = "newCount";

    public static final String CURRENT_NAME = "CURRENT_NAME";
    public static final String IS_UNKNOWN = "IS_UNKNOWN";

    protected long mCurrentId;
    protected String mCurrentName;
    protected boolean mIsUnknown;

    protected abstract String getSelectedCategoryId();

    protected abstract int getNameColumnIndex(Cursor cursor);

    protected abstract long[] fetchSongList(long id);

    protected abstract String fetchCategoryName(Cursor cursor);

    protected abstract String getNumberOfSongsColumnName();

    protected abstract int getUnknownStringId();

    protected abstract int getDeleteDescStringId();

    protected abstract long fetchCurrentlyPlayingCategoryId();

    protected abstract String getEntryContentType();

    protected abstract boolean shuffleSongs();

    protected abstract void pupulareContextMenu(ContextMenu menu);

    /**
     * Do nothing by default, can be overridden by subclasses.
     * @param i  intent
     */
    protected void addExtraSearchData(Intent i) { }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mCurrentId = icicle.getLong(getSelectedCategoryId());
            mCurrentName = icicle.getString(CURRENT_NAME);
            mIsUnknown = icicle.getBoolean(IS_UNKNOWN);
        }
    }

    @Override
    protected MetadataCategoryListAdapter createListAdapter() {
        return new MetadataCategoryListAdapter(
                getActivity(),
                R.layout.track_list_item,
                null,
                new String[] {},
                new int[] {},
                this);
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putLong(getSelectedCategoryId(), mCurrentId);
        outcicle.putString(CURRENT_NAME, mCurrentName);
        outcicle.putBoolean(IS_UNKNOWN, mIsUnknown);
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;

        AdapterView.AdapterContextMenuInfo mi = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        adapter.getCursor().moveToPosition(mi.position);
        mCurrentId = adapter.getCursor().getLong(adapter.getCursor().getColumnIndexOrThrow(BaseColumns._ID));
        mCurrentName = fetchCategoryName(adapter.getCursor());
        mIsUnknown = mCurrentName == null || mCurrentName.equals(MediaStore.UNKNOWN_STRING);
        String title = mIsUnknown ? getString(getUnknownStringId()) : mCurrentName;
        menu.setHeaderTitle(title);

        pupulareContextMenu(menu);
    }

    protected void playAllNow() {
        long[] songs = fetchSongList(mCurrentId);
        if (shuffleSongs()) MusicUtils.shuffleArray(songs);
        MusicUtils.playAll(getActivity(), songs);
    }

    protected void playAllNext() {
        long[] songs = fetchSongList(mCurrentId);
        if (shuffleSongs()) MusicUtils.shuffleArray(songs);
        MusicUtils.queueNext(getActivity(), songs);
    }

    protected void queueAll() {
        long[] songs = fetchSongList(mCurrentId);
        if (shuffleSongs()) MusicUtils.shuffleArray(songs);
        MusicUtils.queue(getActivity(), songs);
    }

    protected void interleaveAll(MenuItem item) {
        Intent intent = item.getIntent();
        int currentCount = intent.getIntExtra(CURRENT_COUNT, 0);
        int newCount = intent.getIntExtra(NEW_COUNT, 0);
        long[] songs = fetchSongList(mCurrentId);
        if (shuffleSongs()) MusicUtils.shuffleArray(songs);
        MusicUtils.interleave(getActivity(), songs, currentCount, newCount);
    }

    protected void newPlaylist() {
        long [] songs = fetchSongList(mCurrentId);
        if (shuffleSongs()) MusicUtils.shuffleArray(songs);
        CreatePlaylist.showMe(getActivity(), songs);
    }

    protected void selectedPlaylist(MenuItem item) {
        long playlist = item.getIntent().getLongExtra("playlist", 0);
        long[] songs = fetchSongList(mCurrentId);
        if (shuffleSongs()) MusicUtils.shuffleArray(songs);
        MusicUtils.addToPlaylist(getActivity(), songs, playlist);
    }

    protected void deleteAll() {
        final long[] songs = fetchSongList(mCurrentId);
        String f = getString(getDeleteDescStringId());
        String desc = String.format(f, mCurrentName);
        new AlertDialog.Builder(getActivity())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.delete_songs_title)
                .setMessage(desc)
                .setNegativeButton(R.string.cancel, (dialog, which) -> { })
                .setPositiveButton(R.string.delete_confirm_button_text, (dialog, which) ->
                        MusicUtils.deleteTracks(MetadataCategoryFragment.this.getActivity(), songs))
                .show();
    }

    protected void searchForCategory() {
        Intent intent = MusicUtils.searchForCategory(mCurrentName, getEntryContentType(), getResources());
        addExtraSearchData(intent);
        startActivity(intent);
    }
}
