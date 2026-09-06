package com.tracksnatcher.app.data.playlist

import com.tracksnatcher.app.domain.model.DuplicateMatch
import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.repository.PlaylistRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dummy-data playlist repository used to build and demo the UI before the streaming
 * back-ends are wired. Bound by default in [com.tracksnatcher.app.di.RepositoryModule];
 * swap the binding to [StreamingPlaylistRepository] once OAuth clients are configured.
 */
@Singleton
class FakePlaylistRepository @Inject constructor() : PlaylistRepository {

    private val pinned = MutableStateFlow(
        listOf(
            Playlist("pl_fav", "Favorites", MusicService.SPOTIFY, trackCount = 214, pinned = true),
            Playlist("pl_drive", "Driving", MusicService.SPOTIFY, trackCount = 88, pinned = true),
            Playlist("pl_gym", "Gym", MusicService.SPOTIFY, trackCount = 132, pinned = true),
            Playlist("pl_country", "Country", MusicService.SPOTIFY, trackCount = 57, pinned = true),
        ),
    )

    override fun observePinnedPlaylists(): Flow<List<Playlist>> = pinned.asStateFlow()

    override suspend fun getPlaylists(service: MusicService): Result<List<Playlist>> {
        delay(300) // simulate network latency
        return Result.success(pinned.value + extraPlaylists)
    }

    override suspend fun searchTracks(service: MusicService, query: String): Result<List<Track>> {
        delay(300)
        val results = sampleTracks
            .filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
            .ifEmpty { sampleTracks }
        return Result.success(results)
    }

    override suspend fun findDuplicate(playlist: Playlist, track: Track): Result<DuplicateMatch?> {
        delay(150)
        val added = addedTracks[playlist.id]?.get(track.dedupeKey())
        return Result.success(added?.let { DuplicateMatch(playlist.name, addedAtEpochMs = it) })
    }

    override suspend fun addTrackToPlaylist(playlist: Playlist, track: Track): Result<Unit> {
        delay(450) // simulate the append round-trip
        addedTracks.getOrPut(playlist.id) { mutableMapOf() }[track.dedupeKey()] = System.currentTimeMillis()
        return Result.success(Unit)
    }

    override suspend fun removeTrackFromPlaylist(playlist: Playlist, track: Track): Result<Unit> {
        delay(250)
        addedTracks[playlist.id]?.remove(track.dedupeKey())
        return Result.success(Unit)
    }

    /** Playlist id -> (dedupe key -> added-at ms). Seeded so a duplicate is demoable. */
    private val addedTracks: MutableMap<String, MutableMap<String, Long>> = mutableMapOf(
        "pl_fav" to mutableMapOf(
            Track("rec_1", "Midnight City", "M83").dedupeKey() to 1_695_000_000_000, // ~Sep 2023
        ),
    )

    private fun Track.dedupeKey(): String =
        providerId(MusicService.SPOTIFY) ?: "${title.lowercase()}|${artist.lowercase()}"

    private val extraPlaylists = listOf(
        Playlist("pl_chill", "Chill", MusicService.SPOTIFY, trackCount = 41),
        Playlist("pl_focus", "Focus", MusicService.SPOTIFY, trackCount = 96),
    )

    private val sampleTracks = listOf(
        Track("rec_1", "Midnight City", "M83", album = "Hurry Up, We're Dreaming"),
        Track("rec_2", "Redbone", "Childish Gambino", album = "Awaken, My Love!"),
        Track("rec_3", "The Chain", "Fleetwood Mac", album = "Rumours"),
    )
}
