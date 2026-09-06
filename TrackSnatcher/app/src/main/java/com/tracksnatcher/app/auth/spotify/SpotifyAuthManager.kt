package com.tracksnatcher.app.auth.spotify

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
 * Spotify OAuth 2.0 with PKCE. Scopes: playlist-modify-public / -private / read-private.
 * Spotify is a public client here, so no client secret is used — the code exchange sends
 * `client_id` + `code_verifier` in the body.
 */
@Singleton
class SpotifyAuthManager @Inject constructor(
    tokenApi: OAuthTokenApi,
    tokenStore: TokenStore,
    redirectBus: OAuthRedirectBus,
) : OAuthManager(
    config = OAuthServiceConfig(
        service = MusicService.SPOTIFY,
        clientId = BuildConfig.SPOTIFY_CLIENT_ID,
        redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
        authorizeEndpoint = OAuth.Spotify.AUTHORIZE_ENDPOINT,
        tokenEndpoint = OAuth.Spotify.TOKEN_ENDPOINT,
        scopes = OAuth.Spotify.SCOPES,
    ),
    tokenApi = tokenApi,
    tokenStore = tokenStore,
    redirectBus = redirectBus,
)
