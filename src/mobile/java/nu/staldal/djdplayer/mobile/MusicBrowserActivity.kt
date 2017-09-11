/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.ActionBar
import android.app.Activity
import android.app.Fragment
import android.app.FragmentManager
import android.app.FragmentTransaction
import android.app.SearchManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import nu.staldal.djdplayer.FragmentServiceConnection
import nu.staldal.djdplayer.ImportPlaylistTask
import nu.staldal.djdplayer.MediaPlayback
import nu.staldal.djdplayer.MediaPlaybackService
import nu.staldal.djdplayer.MusicUtils
import nu.staldal.djdplayer.R
import nu.staldal.djdplayer.SettingsActivity
import nu.staldal.djdplayer.provider.MusicContract
import nu.staldal.djdplayer.provider.MusicProvider
import nu.staldal.ui.WithSectionMenu
import java.util.ArrayList

import kotlinx.android.synthetic.mobile.music_browser_activity.*

private const val LOGTAG = "MusicBrowserActivity"

class MusicBrowserActivity : Activity(), ServiceConnection, SharedPreferences.OnSharedPreferenceChangeListener {

    private var token: MusicUtils.ServiceToken? = null
    private var service: MediaPlayback? = null

    private var invalidateTabs = false
    private var songToPlay: Long = -1

    private var backStack: ArrayList<Intent>? = null

    private var uri: Uri? = null
    private var title: String? = null
    private var searchResult: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(LOGTAG, "onCreate - $intent")

        volumeControlStream = AudioManager.STREAM_MUSIC
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        val myActionBar = actionBar

        if (MusicUtils.android44OrLater() || !MusicUtils.hasMenuKey(this)
                || resources.getBoolean(R.bool.tablet_layout)) {
            disableStackedActionBar(myActionBar)
        }

        setContentView(R.layout.music_browser_activity)

        viewPager.offscreenPageLimit = 5
        viewPager.addOnPageChangeListener(
                object : ViewPager.SimpleOnPageChangeListener() {
                    override fun onPageSelected(position: Int) {
                        // When swiping between pages, select the corresponding tab
                        val actionBar = getActionBar()
                        if (actionBar.navigationMode == ActionBar.NAVIGATION_MODE_TABS) {
                            actionBar.setSelectedNavigationItem(position)
                        }
                    }
                })
        setupTabs(myActionBar)

        val playQueueButton = findViewById(R.id.playqueue_button) as Button?
        playQueueButton?.setOnClickListener { v -> startActivity(Intent(this, MediaPlaybackActivity::class.java)) }

        if (savedInstanceState != null) {
            backStack = savedInstanceState.getParcelableArrayList<Intent>("backStack")
        } else {
            backStack = ArrayList<Intent>(8)
        }

        parseIntent(intent, true)

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
        token = MusicUtils.bindToService(this, this, MobileMediaPlaybackService::class.java)
    }

    /**
     * Workaround to avoid stacked Action Bar.
     *
     * http://developer.android.com/guide/topics/ui/actionbar.html#Tabs
     */
    private fun disableStackedActionBar(actionBar: ActionBar) {
        try {
            val setHasEmbeddedTabsMethod = actionBar.javaClass
                    .getDeclaredMethod("setHasEmbeddedTabs", Boolean::class.javaPrimitiveType)
            setHasEmbeddedTabsMethod.isAccessible = true
            setHasEmbeddedTabsMethod.invoke(actionBar, true)
        } catch (e: Exception) {
            Log.w(LOGTAG, "Unable to configure ActionBar tabs", e)
        }
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as MediaPlaybackService.LocalBinder).service

        notifyFragmentConnected(R.id.player_header, service!!)
        notifyFragmentConnected(R.id.playqueue, service!!)
        notifyFragmentConnected(R.id.player_footer, service!!)
        notifyFragmentConnected(R.id.nowplaying, service!!)

        if (songToPlay > -1) {
            MusicUtils.playSong(this, songToPlay)
            songToPlay = -1
        }

        invalidateOptionsMenu()
    }

    override public fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(LOGTAG, "onNewIntent - $intent")
        val addToBackStack = parseIntent(intent, false)
        if (songToPlay > -1) {
            if (service != null) {
                MusicUtils.playSong(this, songToPlay)
                songToPlay = -1
            }
        }
        if (addToBackStack) {
            backStack!!.add(getIntent())
            setIntent(intent)
        }
    }

    private fun parseIntent(intent: Intent, onCreate: Boolean): Boolean {
        if (Intent.ACTION_VIEW == intent.action
                && intent.data != null
                && intent.type != null && intent.type.startsWith(MusicUtils.AUDIO_X_MPEGURL)) {
            ImportPlaylistTask(applicationContext).execute(intent.data)
            songToPlay = -1
            uri = null
            title = null
            searchResult = false
            MusicUtils.setStringPref(this, SettingsActivity.ACTIVE_TAB, PlaylistFragment::class.java.canonicalName)
        } else if (Intent.ACTION_VIEW == intent.action
                && intent.data != null
                && intent.data.toString().startsWith(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString())
                && MusicUtils.isLong(intent.data.lastPathSegment)) {
            songToPlay = ContentUris.parseId(intent.data)
            if (!onCreate) return false
            uri = null
            title = null
            searchResult = false
        } else if (Intent.ACTION_VIEW == intent.action
                && intent.data != null
                && intent.data.scheme == "file") {
            songToPlay = fetchSongIdFromPath(intent.data.path)
            if (!onCreate) return false
            uri = null
            title = null
            searchResult = false
        } else if ((Intent.ACTION_VIEW == intent.action || Intent.ACTION_PICK == intent.action) && intent.data != null) {
            songToPlay = -1
            uri = fixUri(intent.data)
            title = MusicProvider.calcTitle(this, uri!!)
            searchResult = false
        } else if (Intent.ACTION_SEARCH == intent.action || MediaStore.INTENT_ACTION_MEDIA_SEARCH == intent.action) {
            songToPlay = -1
            uri = null
            title = getString(R.string.search_results, intent.getStringExtra(SearchManager.QUERY))
            searchResult = true
        } else {
            songToPlay = -1
            uri = null
            title = null
            searchResult = false
        }

        if (title == null) {
            enterCategoryMode()
        } else {
            enterSongsMode()
        }

        return true
    }

    private fun fetchSongIdFromPath(path: String): Long {
        val cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                MediaStore.Audio.Media.DATA + "=?",
                arrayOf(path), null)
        if (cursor == null) {
            Log.w(LOGTAG, "Unable to fetch Song Id (cursor is null) for $path")
            return -1
        }
        cursor.use { cur ->
            if (cur.moveToFirst()) {
                return cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            } else {
                Log.w(LOGTAG, "Unable to fetch Song Id (cursor is empty) for $path")
                return -1
            }
        }
    }

    private fun fixUri(uri: Uri): Uri {
        try {
            val id = uri.lastPathSegment.toLong()
            if (uri.toString().startsWith(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Album.getMembersUri(id)
            } else if (uri.toString().startsWith(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Artist.getMembersUri(id)
            } else if (uri.toString().startsWith(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Genre.getMembersUri(id)
            } else if (uri.toString().startsWith(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI.toString())) {
                return MusicContract.Playlist.getMembersUri(id)
            } else {
                return uri
            }
        } catch (e: NumberFormatException) {
            return uri
        }

    }

    private fun enterCategoryMode() {
        uri = null
        title = null

        val oldFragment = fragmentManager.findFragmentByTag(TrackFragment::class.java.canonicalName)
        if (oldFragment != null) {
            val ft = fragmentManager.beginTransaction()
            ft.remove(oldFragment)
            ft.commit()
        }
        mainView.visibility = View.GONE

        val actionBar = actionBar
        actionBar.navigationMode = ActionBar.NAVIGATION_MODE_TABS
        actionBar.setDisplayShowTitleEnabled(false)
        actionBar.setDisplayShowHomeEnabled(false)
        actionBar.setHomeButtonEnabled(false)
        setTitle(null)
        actionBar.customView = null
        actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_CUSTOM)

        viewPager.visibility = View.VISIBLE

        restoreActiveTab(actionBar)
    }

    private fun enterSongsMode() {
        viewPager.visibility = View.GONE

        val actionBar = actionBar
        saveActiveTab(actionBar)
        actionBar.navigationMode = ActionBar.NAVIGATION_MODE_STANDARD
        actionBar.setDisplayShowTitleEnabled(true)
        actionBar.setDisplayShowHomeEnabled(true)
        actionBar.setHomeButtonEnabled(true)
        setTitle(title)

        mainView.visibility = View.VISIBLE

        val fragment: Fragment
        if (searchResult) {
            fragment = Fragment.instantiate(this, QueryFragment::class.java.name)
            actionBar.customView = null
            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_CUSTOM)
        } else {
            val bundle = Bundle()
            bundle.putString(TrackFragment.URI, uri!!.toString())
            fragment = Fragment.instantiate(this, TrackFragment::class.java.name, bundle)
            val trackFragment = fragment as WithSectionMenu

            val categoryMenuView = ImageView(this)
            categoryMenuView.setImageResource(R.drawable.ic_section_menu)
            categoryMenuView.setOnClickListener { v -> trackFragment.onCreateSectionMenu(categoryMenuView) }
            actionBar.customView = categoryMenuView
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM)
        }
        val ft = fragmentManager.beginTransaction()
        ft.replace(R.id.mainView, fragment, TrackFragment::class.java.canonicalName)
        ft.commit()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == SettingsActivity.SHOW_ARTISTS_TAB
                || key == SettingsActivity.SHOW_ALBUMS_TAB
                || key == SettingsActivity.SHOW_GENRES_TAB
                || key == SettingsActivity.SHOW_FOLDERS_TAB
                || key == SettingsActivity.SHOW_PLAYLISTS_TAB) {
            invalidateTabs = true
        }
    }

    override fun onResume() {
        super.onResume()

        if (title == null) {
            val actionBar = actionBar

            if (invalidateTabs) {
                setupTabs(actionBar)
                invalidateTabs = false
            }

            restoreActiveTab(actionBar)
        }
    }

    override fun onPause() {
        super.onPause()

        saveActiveTab(actionBar)
    }

    private fun restoreActiveTab(actionBar: ActionBar) {
        val activeTab = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                .getString(SettingsActivity.ACTIVE_TAB, null)
        for (i in 0..actionBar.tabCount - 1) {
            if (actionBar.getTabAt(i).tag == activeTab) {
                actionBar.setSelectedNavigationItem(i)
                viewPager.currentItem = i
                break
            }
        }
    }

    private fun saveActiveTab(actionBar: ActionBar) {
        val selectedTab = actionBar.selectedTab
        if (selectedTab != null) {
            MusicUtils.setStringPref(this, SettingsActivity.ACTIVE_TAB, selectedTab.tag as String)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null

        notifyFragmentDisconnected(R.id.player_header)
        notifyFragmentDisconnected(R.id.playqueue)
        notifyFragmentDisconnected(R.id.player_footer)
        notifyFragmentDisconnected(R.id.nowplaying)

        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelableArrayList("backStack", backStack)
    }

    override fun onDestroy() {
        if (token != null) MusicUtils.unbindFromService(token)
        service = null
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)

        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.browser_menu, menu)

        if (resources.getBoolean(R.bool.tablet_layout)) {
            val sub = menu.addSubMenu(Menu.NONE, Menu.NONE, 16, R.string.add_all_to_playlist)
            MusicUtils.makePlaylistMenu(this, sub, R.id.new_playlist, R.id.selected_playlist)
        }

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        updatePlaylistItems(menu)

        updateSoundEffectItem(menu)

        updateRepeatItem(menu)

        updatePlayingItems(menu)

        return true
    }

    private fun updatePlaylistItems(menu: Menu) {
        val item = menu.findItem(R.id.create_new_playlist)
        if (item != null) {
            val selectedTab = actionBar.selectedTab
            item.isVisible = selectedTab != null && selectedTab.tag == PlaylistFragment::class.java.name
        }
    }

    private fun updateSoundEffectItem(menu: Menu) {
        val item = menu.findItem(R.id.effect_panel)
        if (item != null) {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            item.isVisible = packageManager.resolveActivity(intent, 0) != null
        }
    }

    private fun updateRepeatItem(menu: Menu) {
        val item = menu.findItem(R.id.repeat)
        if (item != null) {
            if (service != null) {
                when (service!!.repeatMode) {
                    MediaPlayback.REPEAT_ALL -> item.setIcon(R.drawable.ic_mp_repeat_all_btn)
                    MediaPlayback.REPEAT_CURRENT -> item.setIcon(R.drawable.ic_mp_repeat_once_btn)
                    MediaPlayback.REPEAT_STOPAFTER -> item.setIcon(R.drawable.ic_mp_repeat_stopafter_btn)
                    else -> item.setIcon(R.drawable.ic_mp_repeat_off_btn)
                }
            } else {
                item.setIcon(R.drawable.ic_mp_repeat_off_btn)
            }
        }
    }

    private fun updatePlayingItems(menu: Menu) {
        menu.setGroupVisible(R.id.playing_items, service != null && !service!!.isPlaying)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                backStack!!.clear()
                val intent = Intent(Intent.ACTION_MAIN)
                parseIntent(intent, false)
                setIntent(intent)
                return true
            }

            R.id.create_new_playlist -> {
                CreatePlaylist.showMe(this, null)
                return true
            }

            R.id.repeat -> {
                cycleRepeat()
                return true
            }

            R.id.shuffle -> {
                if (service != null) service!!.doShuffle()
                return true
            }

            R.id.uniqueify -> {
                if (service != null) service!!.uniqueify()
                return true
            }

            R.id.clear_queue -> {
                if (service != null) service!!.removeTracks(0, Integer.MAX_VALUE)
                return true
            }

            R.id.new_playlist -> {
                if (service != null) CreatePlaylist.showMe(this, service!!.queue)
                return true
            }

            R.id.selected_playlist -> {
                if (service != null) {
                    val playlist = item.intent.getLongExtra("playlist", 0)
                    MusicUtils.addToPlaylist(this, service!!.queue, playlist)
                }
                return true
            }

            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }

            R.id.search -> return onSearchRequested()

            R.id.effect_panel -> {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, service!!.audioSessionId)
                startActivityForResult(intent, 0)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun cycleRepeat() {
        if (service == null) {
            return
        }
        val mode = service!!.repeatMode
        if (mode == MediaPlayback.REPEAT_NONE) {
            service!!.repeatMode = MediaPlayback.REPEAT_ALL
            Toast.makeText(this, R.string.repeat_all_notif, Toast.LENGTH_SHORT).show()
        } else if (mode == MediaPlayback.REPEAT_ALL) {
            service!!.repeatMode = MediaPlayback.REPEAT_CURRENT
            Toast.makeText(this, R.string.repeat_current_notif, Toast.LENGTH_SHORT).show()
        } else if (mode == MediaPlayback.REPEAT_CURRENT) {
            service!!.repeatMode = MediaPlayback.REPEAT_STOPAFTER
            Toast.makeText(this, R.string.repeat_stopafter_notif, Toast.LENGTH_SHORT).show()
        } else {
            service!!.repeatMode = MediaPlayback.REPEAT_NONE
            Toast.makeText(this, R.string.repeat_off_notif, Toast.LENGTH_SHORT).show()
        }
        invalidateOptionsMenu()
    }

    override fun onBackPressed() {
        if (!backStack!!.isEmpty()) {
            val intent = backStack!!.removeAt(backStack!!.size - 1)
            parseIntent(intent, false)
            setIntent(intent)
        } else {
            super.onBackPressed()
        }
    }

    private fun notifyFragmentConnected(id: Int, service: MediaPlayback) {
        val fragment = fragmentManager.findFragmentById(id)
        if (fragment != null && fragment.isInLayout) {
            (fragment as FragmentServiceConnection).onServiceConnected(service)
        }
    }

    private fun notifyFragmentDisconnected(id: Int) {
        val fragment = fragmentManager.findFragmentById(id)
        if (fragment != null && fragment.isInLayout) {
            (fragment as FragmentServiceConnection).onServiceDisconnected()
        }
    }

    private fun setupTabs(actionBar: ActionBar) {
        viewPager.adapter = null

        actionBar.removeAllTabs()

        setupTab(actionBar, SettingsActivity.SHOW_ARTISTS_TAB, R.string.artists_menu, ArtistFragment::class.java)
        setupTab(actionBar, SettingsActivity.SHOW_ALBUMS_TAB, R.string.albums_menu, AlbumFragment::class.java)
        setupTab(actionBar, SettingsActivity.SHOW_GENRES_TAB, R.string.genres_menu, GenreFragment::class.java)
        setupTab(actionBar, SettingsActivity.SHOW_FOLDERS_TAB, R.string.folders_menu, FolderFragment::class.java)
        setupTab(actionBar, SettingsActivity.SHOW_PLAYLISTS_TAB, R.string.playlists_menu, PlaylistFragment::class.java)

        viewPager.adapter = CategoryPageAdapter(fragmentManager)
    }

    private fun setupTab(actionBar: ActionBar, preferenceKey: String, titleResId: Int,
                         fragmentClass: Class<out Fragment>) {
        val fragment = this@MusicBrowserActivity.fragmentManager.findFragmentByTag(fragmentClass.canonicalName)
        if (fragment != null && !fragment.isDetached) {
            val ft = this@MusicBrowserActivity.fragmentManager.beginTransaction()
            ft.detach(fragment)
            ft.commit()
        }

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(preferenceKey, true)) {
            actionBar.addTab(actionBar.newTab()
                    .setText(titleResId)
                    .setTag(fragmentClass.canonicalName)
                    .setTabListener(tabListener))
        }
    }

    private val tabListener = object : ActionBar.TabListener {
        override fun onTabSelected(tab: ActionBar.Tab, ft: FragmentTransaction) {
            viewPager.setCurrentItem(tab.position, false)
        }

        override fun onTabUnselected(tab: ActionBar.Tab, ft: FragmentTransaction) {}

        override fun onTabReselected(tab: ActionBar.Tab, ft: FragmentTransaction) {}
    }

    private inner class CategoryPageAdapter constructor(private val fragmentManager: FragmentManager) : PagerAdapter() {

        private var currentTransaction: FragmentTransaction? = null

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            if (currentTransaction == null) {
                currentTransaction = fragmentManager.beginTransaction()
            }

            val tab = this@MusicBrowserActivity.actionBar.getTabAt(position)

            var fragment: Fragment? = fragmentManager.findFragmentByTag(tab.tag as String)
            // Check if the fragment is already initialized
            if (fragment != null) {
                // If it exists, simply attach it in order to show it
                currentTransaction!!.attach(fragment)
            } else {
                // If not, instantiate and add it to the activity
                fragment = instantiateFragment(tab)
                currentTransaction!!.add(container.id, fragment, tab.tag as String)
            }
            return fragment
        }

        internal fun instantiateFragment(tab: ActionBar.Tab): Fragment {
            return Fragment.instantiate(this@MusicBrowserActivity, tab.tag as String)
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            if (currentTransaction == null) {
                currentTransaction = fragmentManager.beginTransaction()
            }
            // Detach the fragment, because another one is being attached
            currentTransaction!!.detach(obj as Fragment)
        }

        override fun finishUpdate(container: ViewGroup) {
            if (currentTransaction != null) {
                currentTransaction!!.commit()
                currentTransaction = null
            }
        }

        override fun getCount(): Int {
            return this@MusicBrowserActivity.actionBar.tabCount
        }

        override fun isViewFromObject(view: View, obj: Any): Boolean {
            return (obj as Fragment).view === view
        }
    }
}
