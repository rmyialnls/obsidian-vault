package com.tracksnatcher.app.auth.google

import com.tracksnatcher.app.BuildConfig
import com.tracksnatcher.app.OAuth
import com.tracksnatcher.app.auth.OAuthManager
import com.tracksnatcher.app.auth.OAuthRedirectBus
import com.tracksnatcher.app.auth.OAuthServiceConfig
import com.tracksnatcher.app.auth.TokenStore
import com.tracksnatcher.app.auth.remote.OAuthTokenApi
import com.tracksnatcher.app.domain.model.MusicService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google / YouTube Music OAuth 2.0 with PKCE, scope youtube.
 *
 * `access_type=offline` requests a refresh token; `prompt=consent` forces the consent
 * screen so the refresh token is re-issued even after the first grant. Uses the installed-
 * app "custom URI scheme" client type, matching [OAuth.Google.AUTHORIZE_ENDPOINT].
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    tokenApi: OAuthTokenApi,
    tokenStore: TokenStore,
    redirectBus: OAuthRedirectBus,
) : OAuthManager(
    config = OAuthServiceConfig(
        service = MusicService.YOUTUBE_MUSIC,
        clientId = BuildConfig.GOOGLE_CLIENT_ID,
        redirectUri = BuildConfig.GOOGLE_REDIRECT_URI,
        authorizeEndpoint = OAuth.Google.AUTHORIZE_ENDPOINT,
        tokenEndpoint = OAuth.Google.TOKEN_ENDPOINT,
        scopes = OAuth.Google.SCOPES,
        extraAuthParams = mapOf(
            "access_type" to "offline",
            "prompt" to "consent",
            "include_granted_scopes" to "true",
        ),
    ),
    tokenApi = tokenApi,
    tokenStore = tokenStore,
    redirectBus = redirectBus,
)
