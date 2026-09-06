package com.tracksnatcher.app.domain.model

/** Which streaming service a playlist / account belongs to. */
enum class MusicService { SPOTIFY, YOUTUBE_MUSIC, AMAZON_MUSIC }

/**
 * A recognised track. [providerIds] maps a service to the id/uri needed to append the
 * track on that service (e.g. Spotify URI, YouTube videoId). Populated by the backend
 * recognition response; may be partial when a service has no match.
 */
data class Track(
    val recognitionId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val isrc: String? = null,
    val providerIds: Map<MusicService, String> = emptyMap(),
) {
    fun providerId(service: MusicService): String? = providerIds[service]
}

/** A user playlist on a linked service. [pinned] surfaces it in the quick-target grid. */
data class Playlist(
    val id: String,
    val name: String,
    val service: MusicService,
    val trackCount: Int = 0,
    val imageUrl: String? = null,
    val pinned: Boolean = false,
)
