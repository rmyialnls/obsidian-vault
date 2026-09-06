package com.tracksnatcher.app.domain.repository

import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import kotlinx.coroutines.flow.Flow

/** Captures ambient audio and resolves it to a [Track] via the backend recognition service. */
interface RecognitionRepository {
    /** @param pcmWav raw little-endian 16-bit PCM/WAV captured from the mic (5–7s). */
    suspend fun identify(pcmWav: ByteArray): Result<Track>
}

/** Reads the user's playlists and appends tracks on the linked service(s). */
interface PlaylistRepository {
    /** Pinned/favourite playlists shown in the quick-target grid (typically 3–4). */
    fun observePinnedPlaylists(): Flow<List<Playlist>>

    /** All of the user's playlists on [service], for manual mode. */
    suspend fun getPlaylists(service: MusicService): Result<List<Playlist>>

    /** Free-text track search used by manual mode. */
    suspend fun searchTracks(service: MusicService, query: String): Result<List<Track>>

    /** Append [track] to [playlist]. Idempotent where the underlying API allows it. */
    suspend fun addTrackToPlaylist(playlist: Playlist, track: Track): Result<Unit>
}
