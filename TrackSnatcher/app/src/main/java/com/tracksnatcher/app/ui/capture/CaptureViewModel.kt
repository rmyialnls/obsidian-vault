package com.tracksnatcher.app.ui.capture

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tracksnatcher.app.data.audio.AudioRecorder
import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.usecase.AddTrackToPlaylistUseCase
import com.tracksnatcher.app.domain.usecase.GetPinnedPlaylistsUseCase
import com.tracksnatcher.app.domain.usecase.IdentifyTrackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the ambient-capture flow. Kept UI-framework-free: it exposes [CaptureUiState] and
 * a handful of intents; the screen owns permission prompts and auto-dismiss timing.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val identifyTrack: IdentifyTrackUseCase,
    private val getPinnedPlaylists: GetPinnedPlaylistsUseCase,
    private val addTrackToPlaylist: AddTrackToPlaylistUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    /** When launched from a widget/App Action targeting a named playlist, auto-add on match. */
    private var pendingTargetPlaylistName: String? = null

    fun setTargetPlaylist(name: String?) {
        pendingTargetPlaylistName = name
    }

    /** Begin listening. Caller must hold RECORD_AUDIO (the screen guarantees this). */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startCapture() {
        if (_state.value is CaptureUiState.Listening) return
        _state.value = CaptureUiState.Listening

        viewModelScope.launch {
            val audio = audioRecorder.record().getOrElse {
                _state.value = CaptureUiState.Error(it.asAppError())
                return@launch
            }

            val track = identifyTrack(audio).getOrElse {
                _state.value = CaptureUiState.Error(it.asAppError())
                return@launch
            }

            val playlists = getPinnedPlaylists().first()
            val autoTarget = pendingTargetPlaylistName
                ?.let { name -> playlists.firstOrNull { it.name.equals(name, ignoreCase = true) } }

            if (autoTarget != null) {
                addToPlaylist(track, autoTarget) // voice / widget targeted a specific playlist
            } else {
                _state.value = CaptureUiState.Matched(track = track, playlists = playlists)
            }
        }
    }

    /** Step 2 action: append the matched track to the tapped playlist. */
    fun addToPlaylist(track: Track, playlist: Playlist) {
        _state.value = CaptureUiState.Adding(track, playlist)
        viewModelScope.launch {
            addTrackToPlaylist(playlist, track)
                .onSuccess { _state.value = CaptureUiState.Added(track, playlist) }
                .onFailure { _state.value = CaptureUiState.Error(it.asAppError(), track = track) }
        }
    }

    fun mikePermissionDenied() {
        _state.value = CaptureUiState.Error(AppError.MicPermissionDenied)
    }

    /** Retry from an error: re-list playlists if we already have a track, else re-listen. */
    fun retry() {
        val current = _state.value
        _state.value = CaptureUiState.Idle
        if (current is CaptureUiState.Error && current.track != null) {
            viewModelScope.launch {
                _state.value = CaptureUiState.Matched(current.track, getPinnedPlaylists().first())
            }
        }
        // else: the screen will re-invoke startCapture() once Idle (permission already granted).
    }

    private fun Throwable.asAppError(): AppError = this as? AppError ?: AppError.Unknown(this)
}
