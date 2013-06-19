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
package nu.staldal.djdplayer;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TrackInfoActivity extends Activity {
    private static final String TAG = "TrackInfoActivity";

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private final static String[] COLUMNS = new String[] {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        getActionBar().setHomeButtonEnabled(true);

        setContentView(R.layout.track_info);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Cursor cursor = getContentResolver().query(getIntent().getData(), COLUMNS, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                bindView(cursor);
            }
        }
    }

    private void bindView(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
        File file = new File(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)));
        String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
        String album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
        String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
        String composer = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER));
        int duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
        String year = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR));
        Date dateAdded = new Date(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED))*1000);

        setTitle(title);
        ((TextView)findViewById(R.id.artist)).setText(artist);
        ((TextView)findViewById(R.id.composer)).setText(composer);
        ((TextView)findViewById(R.id.album)).setText(album);
        IdAndName genre = MusicUtils.fetchGenre(this, id);
        if (genre != null) {
            ((TextView)findViewById(R.id.genre)).setText(genre.name);
        }
        ((TextView)findViewById(R.id.year)).setText(year);
        ((TextView)findViewById(R.id.duration)).setText(MusicUtils.formatDuration(this, duration));
        ((TextView)findViewById(R.id.folder)).setText(file.getParent());
        ((TextView)findViewById(R.id.filename)).setText(file.getName());
        ((TextView)findViewById(R.id.filesize)).setText(formatFileSize(file.length()));
        ((TextView)findViewById(R.id.mimetype)).setText(mimeType);
        ((TextView)findViewById(R.id.date_added)).setText(formatDate(dateAdded));
        ((TextView)findViewById(R.id.id)).setText(String.valueOf(id));
    }

    private String formatFileSize(long size) {
        return (size/1024) + " KB";
    }

    private String formatDate(Date timestamp) {
        return dateFormat.format(timestamp);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, MusicBrowserActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;

        }
        return super.onOptionsItemSelected(item);
    }
}