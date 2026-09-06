package com.tracksnatcher.app.auth.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null,
)

/**
 * Generic OAuth 2.0 token endpoint client. The absolute endpoint is passed per-call via
 * [Url] so a single interface serves both Spotify and Google. Spotify additionally accepts
 * an optional Basic [authorization] header for confidential clients; public PKCE clients
 * omit it and send `client_id` in the body instead.
 */
interface OAuthTokenApi {

    @FormUrlEncoded
    @POST
    suspend fun exchangeAuthCode(
        @Url endpoint: String,
        @FieldMap params: Map<String, String>,
        @Header("Authorization") authorization: String? = null,
    ): TokenResponseDto

    @FormUrlEncoded
    @POST
    suspend fun refreshToken(
        @Url endpoint: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
        @Header("Authorization") authorization: String? = null,
    ): TokenResponseDto
}
