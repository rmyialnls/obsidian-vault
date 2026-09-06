package com.tracksnatcher.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tracksnatcher.app.domain.model.MusicService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** OAuth tokens for a single service. [expiresAtEpochMs] is absolute wall-clock time. */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long,
) {
    /** Treat as expired 60s early to avoid racing the clock during a request. */
    fun isExpired(nowEpochMs: Long): Boolean = nowEpochMs >= (expiresAtEpochMs - 60_000)
}

interface TokenStore {
    suspend fun save(service: MusicService, tokens: OAuthTokens)
    suspend fun load(service: MusicService): OAuthTokens?
    suspend fun clear(service: MusicService)
}

/**
 * Tokens live in [EncryptedSharedPreferences] backed by a hardware-backed master key,
 * so refresh tokens never sit in plaintext on disk.
 */
@Singleton
class EncryptedTokenStore @Inject constructor(
    @ApplicationContext context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : TokenStore {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ts_auth_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun save(service: MusicService, tokens: OAuthTokens) = withContext(ioDispatcher) {
        prefs.edit()
            .putString(key(service, ACCESS), tokens.accessToken)
            .putString(key(service, REFRESH), tokens.refreshToken)
            .putLong(key(service, EXPIRES), tokens.expiresAtEpochMs)
            .apply()
    }

    override suspend fun load(service: MusicService): OAuthTokens? = withContext(ioDispatcher) {
        val access = prefs.getString(key(service, ACCESS), null) ?: return@withContext null
        OAuthTokens(
            accessToken = access,
            refreshToken = prefs.getString(key(service, REFRESH), null),
            expiresAtEpochMs = prefs.getLong(key(service, EXPIRES), 0L),
        )
    }

    override suspend fun clear(service: MusicService) = withContext(ioDispatcher) {
        prefs.edit()
            .remove(key(service, ACCESS))
            .remove(key(service, REFRESH))
            .remove(key(service, EXPIRES))
            .apply()
    }

    private fun key(service: MusicService, field: String) = "${service.name}_$field"

    private companion object {
        const val ACCESS = "access"
        const val REFRESH = "refresh"
        const val EXPIRES = "expires"
    }
}
