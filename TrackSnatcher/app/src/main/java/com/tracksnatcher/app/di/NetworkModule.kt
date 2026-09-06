package com.tracksnatcher.app.di

import com.tracksnatcher.app.ApiHosts
import com.tracksnatcher.app.BuildConfig
import com.tracksnatcher.app.auth.google.GoogleAuthManager
import com.tracksnatcher.app.auth.remote.OAuthTokenApi
import com.tracksnatcher.app.auth.spotify.SpotifyAuthManager
import com.tracksnatcher.app.data.remote.BearerAuthInterceptor
import com.tracksnatcher.app.data.remote.RecognitionApi
import com.tracksnatcher.app.data.remote.SpotifyApi
import com.tracksnatcher.app.data.remote.YouTubeApi
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RecognitionRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SpotifyRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class YouTubeRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

    /** Base client (timeouts + logging); per-service clients add their auth interceptor. */
    @Provides
    @Singleton
    fun provideBaseOkHttp(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

    private fun Retrofit.Builder.withJson(json: Json): Retrofit.Builder =
        addConverterFactory(json.asConverterFactory("application/json".toMediaType()))

    // --- Recognition backend (no auth header; backend uses its own API key server-side) ---

    @Provides
    @Singleton
    @RecognitionRetrofit
    fun provideRecognitionRetrofit(base: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.RECOGNITION_BASE_URL)
            .client(base)
            .withJson(json)
            .build()

    @Provides
    @Singleton
    fun provideRecognitionApi(@RecognitionRetrofit retrofit: Retrofit): RecognitionApi =
        retrofit.create(RecognitionApi::class.java)

    // --- OAuth token endpoints (bare client, absolute @Url per call) ---

    @Provides
    @Singleton
    @AuthRetrofit
    fun provideAuthRetrofit(base: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://oauth.invalid/") // never used; every call passes an absolute @Url
            .client(base)
            .withJson(json)
            .build()

    @Provides
    @Singleton
    fun provideOAuthTokenApi(@AuthRetrofit retrofit: Retrofit): OAuthTokenApi =
        retrofit.create(OAuthTokenApi::class.java)

    // --- Spotify ---

    @Provides
    @Singleton
    @SpotifyRetrofit
    fun provideSpotifyRetrofit(
        base: OkHttpClient,
        json: Json,
        spotifyAuthManager: SpotifyAuthManager,
    ): Retrofit {
        val client = base.newBuilder()
            .addInterceptor(BearerAuthInterceptor { spotifyAuthManager.getFreshAccessToken() })
            .build()
        return Retrofit.Builder()
            .baseUrl(ApiHosts.SPOTIFY)
            .client(client)
            .withJson(json)
            .build()
    }

    @Provides
    @Singleton
    fun provideSpotifyApi(@SpotifyRetrofit retrofit: Retrofit): SpotifyApi =
        retrofit.create(SpotifyApi::class.java)

    // --- YouTube ---

    @Provides
    @Singleton
    @YouTubeRetrofit
    fun provideYouTubeRetrofit(
        base: OkHttpClient,
        json: Json,
        googleAuthManager: GoogleAuthManager,
    ): Retrofit {
        val client = base.newBuilder()
            .addInterceptor(BearerAuthInterceptor { googleAuthManager.getFreshAccessToken() })
            .build()
        return Retrofit.Builder()
            .baseUrl(ApiHosts.YOUTUBE)
            .client(client)
            .withJson(json)
            .build()
    }

    @Provides
    @Singleton
    fun provideYouTubeApi(@YouTubeRetrofit retrofit: Retrofit): YouTubeApi =
        retrofit.create(YouTubeApi::class.java)
}
