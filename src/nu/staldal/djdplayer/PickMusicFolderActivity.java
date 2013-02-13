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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;

public class PickMusicFolderActivity extends Activity {
    public static final int PICK_DIRECTORY = 1;

    private EditText editor;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        String currentFolder = PreferenceManager.getDefaultSharedPreferences(this).getString(SettingsActivity.MUSIC_FOLDER,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());

        Intent intent = new Intent("org.openintents.action.PICK_DIRECTORY");
        if (getPackageManager().resolveActivity(intent, 0) != null) {
            intent.setData(Uri.fromFile(new File(currentFolder)));
            intent.putExtra("org.openintents.extra.TITLE", getResources().getString(R.string.music_folder));
            intent.putExtra("org.openintents.extra.BUTTON_TEXT", getResources().getString(R.string.pick_music_folder));
            startActivityForResult(intent, PICK_DIRECTORY);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setContentView(R.layout.create_playlist);
            getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                                  WindowManager.LayoutParams.WRAP_CONTENT);

            TextView mPrompt = (TextView) findViewById(R.id.prompt);
            editor = (EditText) findViewById(R.id.playlist);
            Button mSaveButton = (Button) findViewById(R.id.create);
            mSaveButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String value = editor.getText().toString();
                    if (value != null && value.length() > 0) {
                        setMusicFolder(value);
                        finish();
                    }
                }
            });

            findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    finish();
                }
            });

            mPrompt.setText(R.string.select_music_folder);
            mSaveButton.setText(R.string.pick_music_folder);
            editor.setText(currentFolder);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PICK_DIRECTORY:
                if (data != null) {
                    Uri folder = data.getData();
                    if (folder != null) {
                        setMusicFolder(folder.getPath());
                    }
                }
                finish();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setMusicFolder(String folder) {
        if (new File(folder).isDirectory()) {
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putString(SettingsActivity.MUSIC_FOLDER, folder);
            editor.commit();
        }
    }
}
