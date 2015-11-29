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

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import nu.staldal.djdplayer.IdAndName;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TrackInfoFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public static void showMe(Activity activity, Uri uri) {
        TrackInfoFragment trackInfoFragment = new TrackInfoFragment();
        Bundle bundle = new Bundle();
        bundle.putString("uri", uri.toString());
        trackInfoFragment.setArguments(bundle);
        trackInfoFragment.show(activity.getFragmentManager(), "TrackInfo");
    }

    private final static String[] COLUMNS = new String[] {
            MediaStore.Audio.AudioColumns._ID,
            MediaStore.Audio.AudioColumns.DATA,
            MediaStore.Audio.AudioColumns.MIME_TYPE,
            MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.COMPOSER,
            MediaStore.Audio.AudioColumns.DURATION,
            MediaStore.Audio.AudioColumns.YEAR,
            MediaStore.Audio.AudioColumns.DATE_ADDED
    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getLoaderManager().initLoader(0, getArguments(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.track_info, container, false);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // The only reason you might override this method when using onCreateView() is
        // to modify any dialog characteristics. For example, the dialog includes a
        // title by default, but your custom layout might not need it. So here you can
        // remove the dialog title, but you must call the superclass to get the Dialog.
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(), Uri.parse(args.getString("uri")), COLUMNS, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in. (The framework will take care of closing the old cursor once we return.)
        if (data != null) {
            if (data.moveToFirst()) {
                bindView(data);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no longer using it.
    }

    private void bindView(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID));
        File file = new File(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA)));
        String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE));
        String album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM));
        String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST));
        String composer = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.COMPOSER));
        int duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION));
        String year = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.YEAR));
        Date dateAdded = new Date(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATE_ADDED))*1000);

        ((TextView)getView().findViewById(R.id.title)).setText(title);
        ((TextView)getView().findViewById(R.id.artist)).setText(artist);
        ((TextView)getView().findViewById(R.id.composer)).setText(composer);
        ((TextView)getView().findViewById(R.id.album)).setText(album);
        IdAndName genre = MusicUtils.fetchGenre(getActivity(), id);
        if (genre != null) {
            ((TextView)getView().findViewById(R.id.genre)).setText(genre.name);
        }
        ((TextView)getView().findViewById(R.id.year)).setText(year);
        ((TextView)getView().findViewById(R.id.duration)).setText(MusicUtils.formatDuration(getActivity(), duration));
        ((TextView)getView().findViewById(R.id.folder)).setText(file.getParent());
        ((TextView)getView().findViewById(R.id.filename)).setText(file.getName());
        ((TextView)getView().findViewById(R.id.filesize)).setText(formatFileSize(file.length()));
        ((TextView)getView().findViewById(R.id.mimetype)).setText(mimeType);
        ((TextView)getView().findViewById(R.id.date_added)).setText(formatDate(dateAdded));
        ((TextView)getView().findViewById(R.id.id)).setText(String.valueOf(id));
    }

    private String formatFileSize(long size) {
        return (size/1024) + " KB";
    }

    private String formatDate(Date timestamp) {
        return dateFormat.format(timestamp);
    }
}