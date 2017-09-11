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
package nu.staldal.djdplayer.mobile

import android.app.ListFragment
import android.app.LoaderManager
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.CursorAdapter
import nu.staldal.djdplayer.MediaPlaybackService

abstract class BrowserFragment : ListFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    companion object {
        const val CURRENT_COUNT = "currentCount"
        const val NEW_COUNT = "newCount"
    }

    protected var adapter: CursorAdapter? = null

    protected abstract fun createListAdapter(): CursorAdapter

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        adapter = createListAdapter()
        setListAdapter(adapter)

        loaderManager.initLoader(0, null, this)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        // Swap the new cursor in. (The framework will take care of closing the old cursor once we return.)
        adapter!!.swapCursor(data)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no longer using it.
        adapter!!.swapCursor(null)
    }

    override fun onResume() {
        super.onResume()

        val f = IntentFilter()
        f.addAction(MediaPlaybackService.META_CHANGED)
        f.addAction(MediaPlaybackService.QUEUE_CHANGED)
        activity.registerReceiver(statusListener, f)
    }

    override fun onPause() {
        activity.unregisterReceiver(statusListener)
        super.onPause()
    }

    private val statusListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loaderManager.restartLoader(0, null, this@BrowserFragment)
        }
    }

    protected fun viewCategory(uri: Uri) {
        (activity as MusicBrowserActivity).onNewIntent(Intent(
                if (isPicking()) Intent.ACTION_PICK else Intent.ACTION_VIEW,
                uri))
    }

    protected fun isPicking() =
            Intent.ACTION_PICK == activity.intent.action || Intent.ACTION_GET_CONTENT == activity.intent.action

}
