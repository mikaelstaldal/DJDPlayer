/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012-2014 Mikael Ståldal
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

import android.app.SearchManager;
import android.content.*;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;
import nu.staldal.djdplayer.provider.MusicContract;

public class QueryFragment extends TrackFragment {
    @SuppressWarnings("unused")
    private static final String LOGTAG = "QueryFragment";

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfoIn) {
        if (menuInfoIn == null) return;
        AdapterView.AdapterContextMenuInfo mi = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        selectedPosition = mi.position;
        adapter.getCursor().moveToPosition(selectedPosition);
        String mimeType = adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE));
        if (isSong(mimeType)) {
            super.onCreateContextMenu(menu, view, menuInfoIn);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        selectedPosition = position;
        adapter.getCursor().moveToPosition(selectedPosition);
        String mimeType = adapter.getCursor().getString(adapter.getCursor().getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE));
        if ("artist".equals(mimeType)) {
            viewCategory(MusicContract.Artist.getMembersUri(id));
        } else if ("album".equals(mimeType)) {
            viewCategory(MusicContract.Album.getMembersUri(id));
        } else if (isSong(mimeType)) {
            super.onListItemClick(l, v, position, id);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Intent intent = getActivity().getIntent();

        String mFilterString = intent.getStringExtra(SearchManager.QUERY);
        if (MediaStore.INTENT_ACTION_MEDIA_SEARCH.equals(intent.getAction())) {
            String focus = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS);
            String artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST);
            String album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM);
            String title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE);
            if (focus != null) {
                if (focus.startsWith("audio/") && title != null) {
                    mFilterString = title;
                } else if (focus.equals(MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE)) {
                    if (album != null) {
                        mFilterString = album;
                        if (artist != null) {
                            mFilterString = mFilterString + " " + artist;
                        }
                    }
                } else if (focus.equals(MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE)) {
                    if (artist != null) {
                        mFilterString = artist;
                    }
                }
            }
        }
        if (mFilterString == null) mFilterString = "";

        String[] ccols = new String[]{
                BaseColumns._ID,   // this will be the artist, album or track ID
                MediaStore.Audio.AudioColumns.MIME_TYPE, // mimetype of audio file, or "artist" or "album"
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.AudioColumns.TITLE,
                "data1",
                "data2"
        };

        Uri search = Uri.parse("content://media/external/audio/search/fancy/" + Uri.encode(mFilterString));

        return new CursorLoader(getActivity(), search, ccols, null, null, null);
    }

    @Override
    protected CursorAdapter createListAdapter() {
        return new QueryListAdapter(
                getActivity(),
                R.layout.track_list_item,
                null, // cursor
                new String[]{},
                new int[]{});
    }

    static class QueryListAdapter extends SimpleCursorAdapterWithContextMenu {
        QueryListAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to) {
            super(context, layout, cursor, from, to, 0);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView tv1 = (TextView) view.findViewById(R.id.line1);
            TextView tv2 = (TextView) view.findViewById(R.id.line2);
            ImageView iv = (ImageView) view.findViewById(R.id.icon);
            ViewGroup.LayoutParams p = iv.getLayoutParams();
            if (p == null) {
                // seen this happen, not sure why
                DatabaseUtils.dumpCursor(cursor);
                return;
            }
            p.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;

            String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE));
            if ("artist".equals(mimeType)) {
                iv.setImageResource(R.drawable.ic_mp_artist_list);
                String name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Artists.ARTIST));
                String displayname = name;
                boolean isunknown = false;
                if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                    displayname = context.getString(R.string.unknown_artist_name);
                    isunknown = true;
                }
                tv1.setText(displayname);

                int numalbums = cursor.getInt(cursor.getColumnIndexOrThrow("data1"));
                int numsongs = cursor.getInt(cursor.getColumnIndexOrThrow("data2"));

                String songs_albums = MusicUtils.makeAlbumsSongsLabel(context,
                        numalbums, numsongs, isunknown);

                tv2.setText(songs_albums);
            } else if ("album".equals(mimeType)) {
                iv.setImageResource(R.drawable.albumart_mp_unknown_list);
                String name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Albums.ALBUM));
                String displayname = name;
                if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                    displayname = context.getString(R.string.unknown_album_name);
                }
                tv1.setText(displayname);

                name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Artists.ARTIST));
                displayname = name;
                if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                    displayname = context.getString(R.string.unknown_artist_name);
                }
                tv2.setText(displayname);
            } else if (isSong(mimeType)) {
                iv.setImageResource(R.drawable.ic_mp_song_list);
                String name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.AudioColumns.TITLE));
                tv1.setText(name);

                String displayname = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Artists.ARTIST));
                if (displayname == null || displayname.equals(MediaStore.UNKNOWN_STRING)) {
                    displayname = context.getString(R.string.unknown_artist_name);
                }
                name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Albums.ALBUM));
                if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                    name = context.getString(R.string.unknown_album_name);
                }
                tv2.setText(displayname + " - " + name);
            }
        }
    }

    private static boolean isSong(String mimeType) {
        return mimeType == null ||
                mimeType.startsWith("audio/") ||
                mimeType.equals("application/ogg") ||
                mimeType.equals("application/x-ogg");
    }
}
