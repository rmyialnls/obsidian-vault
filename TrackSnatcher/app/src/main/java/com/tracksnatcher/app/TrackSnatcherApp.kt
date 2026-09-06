package com.tracksnatcher.app

import android.app.Application
import com.tracksnatcher.app.shortcuts.PlaylistShortcutPublisher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt uses this as the DI root; on startup we refresh the
 * dynamic Assistant shortcuts so "Hey Google, ask TrackSnatcher to add to my <Playlist>"
 * always reflects the user's current pinned playlists.
 */
@HiltAndroidApp
class TrackSnatcherApp : Application() {

    @Inject
    lateinit var shortcutPublisher: PlaylistShortcutPublisher

    override fun onCreate() {
        super.onCreate()
        shortcutPublisher.refresh()
    }
}
