package com.tracksnatcher.app.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.tracksnatcher.app.auth.remote.OAuthTokenApi
import com.tracksnatcher.app.auth.remote.TokenResponseDto
import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.MusicService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Everything an [OAuthManager] needs to run one service's PKCE authorization-code flow.
 */
data class OAuthServiceConfig(
    val service: MusicService,
    val clientId: String,
    val redirectUri: String,
    val authorizeEndpoint: String,
    val tokenEndpoint: String,
    val scopes: List<String>,
    /** Extra query params appended to the authorize request (e.g. Google's access_type). */
    val extraAuthParams: Map<String, String> = emptyMap(),
)

/**
 * Base OAuth 2.0 Authorization Code + PKCE manager. Concrete managers (Spotify, Google)
 * only supply an [OAuthServiceConfig]; the whole browser round-trip, code exchange, token
 * persistence and silent refresh live here.
 */
abstract class OAuthManager(
    private val config: OAuthServiceConfig,
    private val tokenApi: OAuthTokenApi,
    private val tokenStore: TokenStore,
    private val redirectBus: OAuthRedirectBus,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val service: MusicService get() = config.service

    /** True once we hold a token (possibly expired but refreshable). */
    suspend fun isAuthorized(): Boolean = tokenStore.load(service) != null

    /**
     * Launch the browser consent screen and suspend until the redirect returns. Returns
     * [Result.success] once tokens are stored, or an [AppError] on cancel/failure.
     */
    suspend fun authorize(context: Context): Result<Unit> {
        val pkce = PkceGenerator.generate()
        redirectBus.clear()

        val authUri = buildAuthorizeUri(pkce)
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, authUri)

        val redirect = withTimeoutOrNull(AUTH_TIMEOUT_MS) {
            redirectBus.redirects
                .map { it }
                .first { it.isForThisService(pkce.state) }
        } ?: return Result.failure(AppError.NotAuthorized(service))

        redirect.getQueryParameter("error")?.let {
            return Result.failure(AppError.NotAuthorized(service))
        }
        val code = redirect.getQueryParameter("code")
            ?: return Result.failure(AppError.NotAuthorized(service))

        return runCatching {
            val response = tokenApi.exchangeAuthCode(
                endpoint = config.tokenEndpoint,
                params = buildMap {
                    put("grant_type", "authorization_code")
                    put("code", code)
                    put("redirect_uri", config.redirectUri)
                    put("client_id", config.clientId)
                    put("code_verifier", pkce.codeVerifier)
                },
            )
            tokenStore.save(service, response.toTokens())
        }.recoverToAuthFailure()
    }

    /**
     * Return a valid access token, refreshing silently if it has expired. Callers use this
     * on every API request. Null means the user must re-authorize.
     */
    suspend fun getFreshAccessToken(): String? {
        val stored = tokenStore.load(service) ?: return null
        if (!stored.isExpired(clock())) return stored.accessToken

        val refreshToken = stored.refreshToken ?: return null
        return runCatching {
            val response = tokenApi.refreshToken(
                endpoint = config.tokenEndpoint,
                refreshToken = refreshToken,
                clientId = config.clientId,
            )
            // Google omits a new refresh_token on refresh — keep the existing one.
            val tokens = response.toTokens(fallbackRefreshToken = refreshToken)
            tokenStore.save(service, tokens)
            tokens.accessToken
        }.getOrNull()
    }

    suspend fun signOut() = tokenStore.clear(service)

    private fun buildAuthorizeUri(pkce: PkceChallenge): Uri =
        Uri.parse(config.authorizeEndpoint).buildUpon().apply {
            appendQueryParameter("response_type", "code")
            appendQueryParameter("client_id", config.clientId)
            appendQueryParameter("redirect_uri", config.redirectUri)
            appendQueryParameter("scope", config.scopes.joinToString(" "))
            appendQueryParameter("code_challenge", pkce.codeChallenge)
            appendQueryParameter("code_challenge_method", pkce.codeChallengeMethod)
            appendQueryParameter("state", pkce.state)
            config.extraAuthParams.forEach { (k, v) -> appendQueryParameter(k, v) }
        }.build()

    private fun Uri.isForThisService(expectedState: String): Boolean =
        scheme == com.tracksnatcher.app.OAuth.SCHEME &&
            getQueryParameter("state") == expectedState

    private fun TokenResponseDto.toTokens(fallbackRefreshToken: String? = null) = OAuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken ?: fallbackRefreshToken,
        expiresAtEpochMs = clock() + (expiresIn * 1000L),
    )

    private fun Result<Unit>.recoverToAuthFailure(): Result<Unit> =
        fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(AppError.NotAuthorized(service)) })

    private companion object {
        const val AUTH_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
