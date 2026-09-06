package com.tracksnatcher.app.di

import com.tracksnatcher.app.auth.EncryptedTokenStore
import com.tracksnatcher.app.auth.TokenStore
import com.tracksnatcher.app.data.memory.SonicMemoryStore
import com.tracksnatcher.app.data.playlist.FakePlaylistRepository
import com.tracksnatcher.app.data.vibe.FakeGenreSource
import com.tracksnatcher.app.data.vibe.GenreSource
import com.tracksnatcher.app.data.recognition.RecognitionRepositoryImpl
import com.tracksnatcher.app.domain.repository.PlaylistRepository
import com.tracksnatcher.app.domain.repository.RecognitionRepository
import com.tracksnatcher.app.domain.repository.SonicMemoryRepository
import com.tracksnatcher.app.location.FusedLocationProvider
import com.tracksnatcher.app.location.LocationProvider
import com.tracksnatcher.app.people.FakePeopleRepository
import com.tracksnatcher.app.people.PeopleRepository
import com.tracksnatcher.app.session.FakeSessionRepository
import com.tracksnatcher.app.session.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds domain interfaces to their implementations.
 *
 * The scaffold ships with [FakePlaylistRepository] (dummy data) bound to
 * [PlaylistRepository] so the UI is fully demoable before OAuth clients are configured.
 * Switch the binding to `StreamingPlaylistRepository` once real credentials are in place.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRecognitionRepository(impl: RecognitionRepositoryImpl): RecognitionRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: FakePlaylistRepository): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore

    @Binds
    @Singleton
    abstract fun bindSonicMemoryRepository(impl: SonicMemoryStore): SonicMemoryRepository

    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

    @Binds
    @Singleton
    abstract fun bindGenreSource(impl: FakeGenreSource): GenreSource

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: FakeSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun bindPeopleRepository(impl: FakePeopleRepository): PeopleRepository
}
