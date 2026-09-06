package com.tracksnatcher.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A captured moment: what was heard, when, where, and where it was filed. Persisted locally
 * and used to render the shareable "Sonic Memory" card. Location fields are null when the
 * user hasn't granted location or it couldn't be resolved.
 */
@Serializable
data class SonicMemory(
    val id: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val timestampMs: Long,
    val playlistName: String,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** Coarse place resolved from a location fix. */
data class PlaceInfo(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)
