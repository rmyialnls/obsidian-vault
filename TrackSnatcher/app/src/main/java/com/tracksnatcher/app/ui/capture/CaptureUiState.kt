package com.tracksnatcher.app.ui.capture

import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.DuplicateMatch
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.SonicMemory
import com.tracksnatcher.app.domain.model.Track

/**
 * The 2-tap flow as an explicit state machine:
 * Idle → Listening → Matched → (DuplicateWarning?) → Adding → Added, plus LimitReached and
 * Error branches. Auto-vibe and widget/voice targeting can skip straight from Listening into
 * the duplicate check / Adding.
 */
sealed interface CaptureUiState {

    data object Idle : CaptureUiState

    data object Listening : CaptureUiState

    /** Audio captured; waiting on the recognition service. */
    data object Identifying : CaptureUiState

    /** Free monthly snatch cap hit — prompt the paywall. */
    data object LimitReached : CaptureUiState

    /** Match returned — show the quick-target grid (already limited by tier). */
    data class Matched(
        val track: Track,
        val playlists: List<Playlist>,
    ) : CaptureUiState

    /** Chosen playlist already contains the track; confirm intent. */
    data class DuplicateWarning(
        val track: Track,
        val playlist: Playlist,
        val duplicate: DuplicateMatch,
        val gridPlaylists: List<Playlist>,
    ) : CaptureUiState

    data class Adding(
        val track: Track,
        val playlist: Playlist,
    ) : CaptureUiState

    /** Success — [memory] is present when a Sonic Memory was recorded (share-ready). */
    data class Added(
        val track: Track,
        val playlist: Playlist,
        val memory: SonicMemory? = null,
    ) : CaptureUiState

    data class Error(
        val error: AppError,
        val track: Track? = null,
    ) : CaptureUiState
}
