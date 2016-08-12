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

package nu.staldal.djdplayer.mobile;

import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ListView;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.provider.ID3Utils;
import nu.staldal.djdplayer.provider.MusicContract;

public class GenreFragment extends MetadataCategoryFragment {

    @Override
    protected String getSelectedCategoryId() {
        return "selectedgenre";
    }

    @Override
    protected int getUnknownStringId() {
        return R.string.unknown_genre_name;
    }

    @Override
    protected int getDeleteDescStringId() {
        return R.string.delete_genre_desc;
    }

    @Override
    protected long fetchCurrentlyPlayingCategoryId() {
        return (MusicUtils.sService != null)
                ? MusicUtils.sService.getGenreId()
                : -1;
    }

    @Override
    protected String getEntryContentType() {
        return MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] cols = new String[] {
                MusicContract.Genre._ID,
                MusicContract.Genre.NAME,
                MusicContract.Genre._COUNT,
        };

        return new CursorLoader(getActivity(), MusicContract.Genre.CONTENT_URI, cols, null, null, null);
    }

    @Override
    protected String fetchCategoryName(Cursor cursor) {
        return ID3Utils.decodeGenre(cursor.getString(getNameColumnIndex(cursor)));
    }

    @Override
    protected int getNameColumnIndex(Cursor cursor) {
        return cursor.getColumnIndexOrThrow(MusicContract.Genre.NAME);
    }

    @Override
    protected String getNumberOfSongsColumnName() {
        return MusicContract.Genre._COUNT;
    }

    @Override
    protected long[] fetchSongList(long id) {
        Cursor cursor = MusicUtils.query(getActivity(),
                MusicContract.Genre.getMembersUri(id),
                new String[] { MediaStore.Audio.AudioColumns._ID },
                null,
                null,
                null);

        if (cursor != null) {
            long [] list = MusicUtils.getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return MusicUtils.sEmptyList;
    }

    @Override
    protected boolean shuffleSongs() {
        return true;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        viewCategory(MusicContract.Genre.getMembersUri(id));
    }

    @Override
    protected void pupulareContextMenu(ContextMenu menu) {
        menu.add(0, R.id.genre_play_all_now, 0, R.string.play_all_now);
        menu.add(0, R.id.genre_play_all_next, 0, R.string.play_all_next);
        menu.add(0, R.id.genre_queue_all, 0, R.string.queue_all);
        SubMenu interleave = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.interleave_all);
        for (int i = 1; i<=5; i++) {
            for (int j = 1; j<=5; j++) {
                Intent intent = new Intent();
                intent.putExtra(CURRENT_COUNT, i);
                intent.putExtra(NEW_COUNT, j);
                interleave.add(2, R.id.genre_interleave_all, 0, getResources().getString(R.string.interleaveNNN, i, j)).setIntent(intent);
            }
        }

        SubMenu sub = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.add_all_to_playlist);
        MusicUtils.makePlaylistMenu(getActivity(), sub, R.id.genre_new_playlist, R.id.genre_selected_playlist);

        menu.add(0, R.id.genre_delete_all, 0, R.string.delete_all);

        if (!mIsUnknown) {
            menu.add(0, R.id.genre_search_for_category, 0, R.string.search_for);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.genre_play_all_now:
                playAllNow();
                return true;

            case R.id.genre_play_all_next:
                playAllNext();
                return true;

            case R.id.genre_queue_all:
                queueAll();
                return true;

            case R.id.genre_interleave_all:
                interleaveAll(item);
                return true;

            case R.id.genre_new_playlist:
                newPlaylist();
                return true;

            case R.id.genre_selected_playlist:
                selectedPlaylist(item);
                return true;

            case R.id.genre_delete_all:
                deleteAll();
                return true;

            case R.id.genre_search_for_category:
                searchForCategory();
                return true;
        }
        return super.onContextItemSelected(item);
    }
}
