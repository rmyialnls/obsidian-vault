package com.tracksnatcher.app.data.vibe

import com.tracksnatcher.app.data.remote.SpotifyApi
import com.tracksnatcher.app.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies genre tags for a track, used by [VibeMatcher]. */
interface GenreSource {
    suspend fun genresFor(track: Track): List<String>
}

/**
 * Dummy genre source so Vibe Match is demoable without live credentials. Bound by default in
 * [com.tracksnatcher.app.di.RepositoryModule]; swap to [SpotifyGenreSource] for production.
 */
@Singleton
class FakeGenreSource @Inject constructor() : GenreSource {
    override suspend fun genresFor(track: Track): List<String> {
        val haystack = "${track.artist} ${track.title} ${track.album.orEmpty()}".lowercase()
        return SAMPLE_GENRES.firstNotNullOfOrNull { (needle, genres) ->
            genres.takeIf { haystack.contains(needle) }
        } ?: listOf("indie", "acoustic") // default vibe → Chill
    }

    private companion object {
        val SAMPLE_GENRES = linkedMapOf(
            "fleetwood mac" to listOf("classic rock", "folk rock", "americana"),
            "childish gambino" to listOf("hip hop", "funk", "r&b"),
            "m83" to listOf("indie", "dream pop", "electronic"),
        )
    }
}

/**
 * Production genre source: resolves the artist via Spotify search, then reads its genres.
 * A future revision can enrich this with MusicBrainz tags for artists Spotify tags sparsely.
 * Not bound by default.
 */
@Singleton
class SpotifyGenreSource @Inject constructor(
    private val spotifyApi: SpotifyApi,
) : GenreSource {
    override suspend fun genresFor(track: Track): List<String> = runCatching {
        val artist = spotifyApi.searchArtists(query = track.artist).artists.items.firstOrNull()
            ?: return emptyList()
        // The search result already carries genres; getArtist(id) is available if it doesn't.
        artist.genres.ifEmpty { spotifyApi.getArtist(artist.id).genres }
    }.getOrDefault(emptyList())
}
