package com.tracksnatcher.app.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tracksnatcher.app.MainActivity
import com.tracksnatcher.app.R
import com.tracksnatcher.app.di.ApplicationScope
import com.tracksnatcher.app.domain.repository.PlaylistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the user's top pinned playlists as dynamic shortcuts so Google Assistant can
 * fulfil "Hey Google, ask TrackSnatcher to add to my <Playlist>". Each shortcut launches the
 * capture flow pre-targeted at that playlist (see [MainActivity.EXTRA_PLAYLIST_NAME]).
 */
@Singleton
class PlaylistShortcutPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val playlistRepository: PlaylistRepository,
) {
    fun refresh() {
        scope.launch {
            val playlists = playlistRepository.observePinnedPlaylists().first()
            val shortcuts = playlists.take(MAX_SHORTCUTS).map { playlist ->
                ShortcutInfoCompat.Builder(context, "add_to_${playlist.id}")
                    .setShortLabel(playlist.name)
                    .setLongLabel("Add to ${playlist.name}")
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(
                        Intent(MainActivity.ACTION_CAPTURE).apply {
                            setClassName(context, MainActivity::class.java.name)
                            putExtra(MainActivity.EXTRA_PLAYLIST_NAME, playlist.name)
                        },
                    )
                    .build()
            }
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            if (shortcuts.isNotEmpty()) {
                ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
            }
        }
    }

    private companion object {
        const val MAX_SHORTCUTS = 4
    }
}
