package com.tracksnatcher.app.data.remote.dto

import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from our recognition backend (which fronts AcoustID / MusicBrainz and resolves
 * per-service ids). [status] == "no_match" when the audio could not be identified.
 */
@Serializable
data class RecognitionResponseDto(
    val status: String,
    val match: MatchDto? = null,
) {
    val isMatch: Boolean get() = status == "ok" && match != null
}

@Serializable
data class MatchDto(
    @SerialName("recognition_id") val recognitionId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    val isrc: String? = null,
    /** Service key ("spotify","youtube_music","amazon_music") -> track id/uri. */
    @SerialName("provider_ids") val providerIds: Map<String, String> = emptyMap(),
) {
    fun toDomain(): Track = Track(
        recognitionId = recognitionId,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        isrc = isrc,
        providerIds = providerIds.mapNotNull { (key, value) ->
            key.toMusicServiceOrNull()?.let { it to value }
        }.toMap(),
    )

    private fun String.toMusicServiceOrNull(): MusicService? = when (this) {
        "spotify" -> MusicService.SPOTIFY
        "youtube_music", "youtube" -> MusicService.YOUTUBE_MUSIC
        "amazon_music", "amazon" -> MusicService.AMAZON_MUSIC
        else -> null
    }
}
