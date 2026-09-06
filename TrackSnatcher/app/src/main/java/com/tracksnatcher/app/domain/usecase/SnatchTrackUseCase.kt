package com.tracksnatcher.app.domain.usecase

import com.tracksnatcher.app.billing.EntitlementStore
import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.DuplicateMatch
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.repository.PlaylistRepository
import javax.inject.Inject

/** Result of a snatch attempt (from the tape or someone's library). */
sealed interface SnatchOutcome {
    data class Added(val track: Track, val destination: Playlist) : SnatchOutcome
    /** The track is already in the destination — the "you already have this" gate. */
    data class AlreadyThere(val duplicate: DuplicateMatch) : SnatchOutcome
    data object LimitReached : SnatchOutcome
    data class Failed(val error: AppError) : SnatchOutcome
}

/**
 * Copies a track onto one of the user's own playlists, enforcing the free cap and the
 * already-have gate. Trip/session adds must not auto-snatch — callers invoke this explicitly.
 */
class SnatchTrackUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val entitlementStore: EntitlementStore,
) {
    suspend operator fun invoke(track: Track, destination: Playlist, force: Boolean = false): SnatchOutcome {
        if (!entitlementStore.current().canSnatch) return SnatchOutcome.LimitReached

        if (!force) {
            playlistRepository.findDuplicate(destination, track).getOrNull()?.let {
                return SnatchOutcome.AlreadyThere(it)
            }
        }
        return playlistRepository.addTrackToPlaylist(destination, track).fold(
            onSuccess = {
                entitlementStore.recordSnatch()
                SnatchOutcome.Added(track, destination)
            },
            onFailure = { SnatchOutcome.Failed(it as? AppError ?: AppError.Unknown(it)) },
        )
    }
}
