/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;

public class CreatePlaylist extends DialogFragment {
    @SuppressWarnings("unused")
    private static final String LOGTAG = "CreatePlaylist";

    private EditText mPlaylist;

    public static void showMe(Activity activity, long[] songs) {
        CreatePlaylist fragment = new CreatePlaylist();
        Bundle bundle = new Bundle();
        bundle.putLongArray("songs", songs);
        fragment.setArguments(bundle);
        fragment.show(activity.getFragmentManager(), "CreatePlaylist");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = getActivity().getLayoutInflater().inflate(R.layout.create_playlist, null);
        mPlaylist = (EditText) view.findViewById(R.id.playlist);

        String defaultName = savedInstanceState != null ? savedInstanceState.getString("defaultname") : makePlaylistName();

        mPlaylist.setText(defaultName);
        mPlaylist.setSelection(defaultName.length());

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.create_playlist_create_text_prompt)
                .setView(view)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CreatePlaylist.this.getDialog().cancel();
                    }
                })
                .setPositiveButton(R.string.create_playlist_create_text, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = mPlaylist.getText().toString();
                        if (name.length() > 0) {
                            if (MusicUtils.playlistExists(getActivity(), name)) {
                                Toast.makeText(getActivity(), R.string.playlist_already_exists, Toast.LENGTH_SHORT).show();
                            } else {
                                Uri uri = MusicUtils.createPlaylist(getActivity(), name);
                                long[] songs = getArguments().getLongArray("songs");
                                if (songs != null) {
                                    MusicUtils.addToPlaylist(getActivity(), songs, Integer.valueOf(uri.getLastPathSegment()));
                                }
                            }
                        }
                        CreatePlaylist.this.getDialog().dismiss();
                    }
                }).create();
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        outcicle.putString("defaultname", mPlaylist.getText().toString());
    }

    private String makePlaylistName() {
        String template = getString(R.string.new_playlist_name_template);
        int num = 1;

        String[] cols = new String[] {
                MediaStore.Audio.Playlists.NAME
        };
        Cursor c = getActivity().getContentResolver().query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                cols, MediaStore.Audio.Playlists.NAME + " != ''", null,
                MediaStore.Audio.Playlists.NAME);

        if (c == null) {
            return null;
        }

        String suggestedName = String.format(template, num++);

        // Need to loop until we've made 1 full pass through without finding a match.
        // Looping more than once shouldn't happen very often, but will happen if
        // you have playlists named "New Playlist 1"/10/2/3/4/5/6/7/8/9, where
        // making only one pass would result in "New Playlist 10" being erroneously
        // picked for the new name.
        boolean done = false;
        while (!done) {
            done = true;
            c.moveToFirst();
            while (!c.isAfterLast()) {
                String playlistName = c.getString(0);
                if (playlistName.compareToIgnoreCase(suggestedName) == 0) {
                    suggestedName = String.format(template, num++);
                    done = false;
                }
                c.moveToNext();
            }
        }
        c.close();
        return suggestedName;
    }
}
