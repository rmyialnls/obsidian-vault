package com.tracksnatcher.app.ui.capture

import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track

/**
 * The 2-tap flow as an explicit state machine:
 * Idle → Listening → Matched → (Adding → Added) | Error.
 */
sealed interface CaptureUiState {

    /** Before the mic starts (e.g. awaiting the permission grant). */
    data object Idle : CaptureUiState

    /** Mic is capturing / audio is uploading for recognition. */
    data object Listening : CaptureUiState

    /** Match returned — show the quick-target grid of pinned playlists. */
    data class Matched(
        val track: Track,
        val playlists: List<Playlist>,
    ) : CaptureUiState

    /** A playlist was tapped; the append request is in flight. */
    data class Adding(
        val track: Track,
        val playlist: Playlist,
    ) : CaptureUiState

    /** Success checkmark before auto-dismiss. */
    data class Added(
        val track: Track,
        val playlist: Playlist,
    ) : CaptureUiState

    /** Recoverable failure with the specific [error] for a tailored message + retry. */
    data class Error(
        val error: AppError,
        val track: Track? = null,
    ) : CaptureUiState
}
