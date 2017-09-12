/*
* Copyright (C) 2012-2017 Mikael Ståldal
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
package nu.staldal.djdplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import android.widget.EditText
import java.io.File

private const val LOGTAG = "PickMusicFolderActivity"

private const val PICK_DIRECTORY = 1

class PickMusicFolderActivity : Activity() {

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        val currentFolder = PreferenceManager.getDefaultSharedPreferences(this).getString(SettingsActivity.MUSIC_FOLDER,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath)

        val intent = Intent("org.openintents.action.PICK_DIRECTORY")
        if (packageManager.resolveActivity(intent, 0) != null) {
            intent.data = Uri.fromFile(File(currentFolder))
            intent.putExtra("org.openintents.extra.TITLE", resources.getString(R.string.music_folder))
            intent.putExtra("org.openintents.extra.BUTTON_TEXT", resources.getString(R.string.pick_music_folder))
            startActivityForResult(intent, PICK_DIRECTORY)
        } else {
            val view = layoutInflater.inflate(R.layout.select_music_folder, null)
            val editor = view.findViewById(R.id.music_folder) as EditText
            editor.setText(currentFolder)

            AlertDialog.Builder(this)
                    .setTitle(R.string.select_music_folder)
                    .setView(view)
                    .setNegativeButton(R.string.cancel) { _, _ -> finish() }
                    .setPositiveButton(R.string.select_music_folder) { _, _ ->
                        val value = editor.text.toString()
                        if (value.isNotEmpty()) {
                            setMusicFolder(value)
                            finish()
                        }
                    }.show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PICK_DIRECTORY -> {
                data?.data?.path?.let { setMusicFolder(it) }
                finish()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun setMusicFolder(folder: String) {
        if (File(folder).isDirectory) {
            val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
            editor.putString(SettingsActivity.MUSIC_FOLDER, folder)
            editor.apply()

            if (!MusicUtils.android44OrLater()) {
                Log.i(LOGTAG, "Rescanning music")
                sendBroadcast(Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(Environment.getExternalStorageDirectory())))
            }
        }
    }

}
