package com.tracksnatcher.app.domain.model

/**
 * Indicates the track already exists in the target playlist. [addedAtEpochMs] is the original
 * add time when the service reports it (Spotify exposes `added_at`; others may not).
 */
data class DuplicateMatch(
    val playlistName: String,
    val addedAtEpochMs: Long? = null,
)
