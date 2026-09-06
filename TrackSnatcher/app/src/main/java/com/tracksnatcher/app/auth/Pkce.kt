package com.tracksnatcher.app.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * One PKCE challenge/verifier pair plus a CSRF [state], per RFC 7636.
 * Verifier is 32 random bytes base64url-encoded; challenge is the S256 of the verifier.
 */
data class PkceChallenge(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
) {
    val codeChallengeMethod: String = "S256"
}

object PkceGenerator {

    private val secureRandom = SecureRandom()
    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    fun generate(): PkceChallenge {
        val verifier = randomBase64(bytes = 32)
        val state = randomBase64(bytes = 16)
        val challenge = sha256Base64(verifier)
        return PkceChallenge(codeVerifier = verifier, codeChallenge = challenge, state = state)
    }

    private fun randomBase64(bytes: Int): String {
        val buffer = ByteArray(bytes)
        secureRandom.nextBytes(buffer)
        return Base64.encodeToString(buffer, BASE64_FLAGS)
    }

    private fun sha256Base64(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, BASE64_FLAGS)
    }
}
