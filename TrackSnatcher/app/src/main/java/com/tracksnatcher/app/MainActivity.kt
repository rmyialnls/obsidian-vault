package com.tracksnatcher.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tracksnatcher.app.auth.OAuthRedirectBus
import com.tracksnatcher.app.ui.TrackSnatcherAppRoot
import com.tracksnatcher.app.ui.navigation.CaptureLaunch
import com.tracksnatcher.app.ui.theme.TrackSnatcherTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host. It receives three kinds of entry:
 *  - normal launcher tap  -> lands on the capture flow
 *  - widget / App Action  -> [ACTION_CAPTURE], optionally carrying a target playlist name
 *  - OAuth redirect        -> tracksnatcher:// deep link handed off to [OAuthRedirectBus]
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var oAuthRedirectBus: OAuthRedirectBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            TrackSnatcherTheme {
                var launch by remember { mutableStateOf(intent.toCaptureLaunch()) }
                TrackSnatcherAppRoot(initialLaunch = launch)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Route OAuth redirects into the auth bus; everything else is handled by Compose. */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == OAuth.SCHEME) {
            oAuthRedirectBus.publish(data)
        }
    }

    private fun Intent?.toCaptureLaunch(): CaptureLaunch = when {
        this?.action == ACTION_CAPTURE ->
            CaptureLaunch.Immediate(targetPlaylistName = getStringExtra(EXTRA_PLAYLIST_NAME))
        else -> CaptureLaunch.None
    }

    companion object {
        const val ACTION_CAPTURE = "com.tracksnatcher.app.action.CAPTURE"
        const val EXTRA_PLAYLIST_NAME = "com.tracksnatcher.app.extra.PLAYLIST_NAME"
    }
}
