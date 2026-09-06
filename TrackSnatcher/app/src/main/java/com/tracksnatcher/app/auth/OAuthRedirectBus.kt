package com.tracksnatcher.app.auth

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the OAuth redirect (delivered to [com.tracksnatcher.app.MainActivity] as a
 * `tracksnatcher://…` deep link) back to whichever auth manager is awaiting it.
 *
 * A replay of 1 covers the case where the redirect arrives fractionally before the
 * suspended collector subscribes (Custom Tab returns very fast on some devices).
 */
@Singleton
class OAuthRedirectBus @Inject constructor() {

    private val _redirects = MutableSharedFlow<Uri>(replay = 1, extraBufferCapacity = 1)
    val redirects: SharedFlow<Uri> = _redirects.asSharedFlow()

    fun publish(uri: Uri) {
        _redirects.tryEmit(uri)
    }

    fun clear() {
        _redirects.resetReplayCache()
    }
}
