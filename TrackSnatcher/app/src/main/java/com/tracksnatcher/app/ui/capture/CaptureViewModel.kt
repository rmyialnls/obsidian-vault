package com.tracksnatcher.app.ui.capture

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tracksnatcher.app.billing.EntitlementStore
import com.tracksnatcher.app.billing.Entitlements
import com.tracksnatcher.app.data.audio.AudioRecorder
import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.repository.PlaylistRepository
import com.tracksnatcher.app.domain.usecase.AddTrackToPlaylistUseCase
import com.tracksnatcher.app.domain.usecase.GetPinnedPlaylistsUseCase
import com.tracksnatcher.app.domain.usecase.IdentifyTrackUseCase
import com.tracksnatcher.app.domain.usecase.RecordSonicMemoryUseCase
import com.tracksnatcher.app.domain.usecase.ResolveVibeTargetUseCase
import com.tracksnatcher.app.people.UserPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Orchestrates the capture flow and every gate around it: the free-tier snatch cap, the
 * tier-limited quick-target grid, duplicate interception, Pro auto-vibe routing, and
 * recording a shareable Sonic Memory on success.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val identifyTrack: IdentifyTrackUseCase,
    private val getPinnedPlaylists: GetPinnedPlaylistsUseCase,
    private val addTrackToPlaylist: AddTrackToPlaylistUseCase,
    private val playlistRepository: PlaylistRepository,
    private val resolveVibeTarget: ResolveVibeTargetUseCase,
    private val recordSonicMemory: RecordSonicMemoryUseCase,
    private val entitlementStore: EntitlementStore,
    private val userPrefsStore: UserPrefsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    val entitlements: StateFlow<Entitlements> = entitlementStore.entitlements
        .stateIn(viewModelScope, SharingStarted.Eagerly, Entitlements())

    private var pendingTargetPlaylistName: String? = null

    fun setTargetPlaylist(name: String?) {
        pendingTargetPlaylistName = name
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startCapture() {
        if (_state.value is CaptureUiState.Listening || _state.value is CaptureUiState.Identifying) return

        viewModelScope.launch {
            // Gate 1: free-tier monthly cap.
            if (!entitlementStore.current().canSnatch) {
                _state.value = CaptureUiState.LimitReached
                return@launch
            }
            _state.value = CaptureUiState.Listening

            val audio = audioRecorder.record().getOrElse {
                _state.value = CaptureUiState.Error(it.asAppError()); return@launch
            }
            _state.value = CaptureUiState.Identifying
            val track = identifyTrack(audio).getOrElse {
                _state.value = CaptureUiState.Error(it.asAppError()); return@launch
            }

            // Sticky destination: the last-used playlist leads the grid (and is the primary tile).
            val lastUsedId = userPrefsStore.prefs.first().lastDestinationId
            val allPlaylists = getPinnedPlaylists().first().sortedByDescending { it.id == lastUsedId }
            val ent = entitlementStore.current()
            val grid = allPlaylists.take(ent.quickPlaylistLimit)

            // Route directly when a target is implied by voice/widget or Pro auto-vibe.
            val autoTarget = resolveAutoTarget(track, allPlaylists, ent)
            if (autoTarget != null) {
                chooseTarget(track, autoTarget, grid)
            } else {
                _state.value = CaptureUiState.Matched(track, grid)
            }
        }
    }

    /** Step 2: user tapped a playlist. Runs the duplicate check before appending. */
    fun chooseTarget(track: Track, playlist: Playlist, gridPlaylists: List<Playlist>) {
        viewModelScope.launch {
            val duplicate = playlistRepository.findDuplicate(playlist, track).getOrNull()
            if (duplicate != null) {
                _state.value = CaptureUiState.DuplicateWarning(track, playlist, duplicate, gridPlaylists)
            } else {
                performAdd(track, playlist)
            }
        }
    }

    fun addAnyway(track: Track, playlist: Playlist) = performAdd(track, playlist)

    fun chooseAnother(track: Track, gridPlaylists: List<Playlist>) {
        _state.value = CaptureUiState.Matched(track, gridPlaylists)
    }

    private fun performAdd(track: Track, playlist: Playlist) {
        _state.value = CaptureUiState.Adding(track, playlist)
        viewModelScope.launch {
            addTrackToPlaylist(playlist, track)
                .onSuccess {
                    entitlementStore.recordSnatch()
                    userPrefsStore.setLastDestination(playlist.id)
                    val memory = runCatching {
                        recordSonicMemory(track, playlist.name, includeLocation = true)
                    }.getOrNull()
                    _state.value = CaptureUiState.Added(track, playlist, memory)
                }
                .onFailure { _state.value = CaptureUiState.Error(it.asAppError(), track = track) }
        }
    }

    /** One-tap Undo after a wrong add: remove the track and refund the snatch against the cap. */
    fun undo(track: Track, playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlist, track)
            entitlementStore.refundSnatch()
        }
    }

    fun mikePermissionDenied() {
        _state.value = CaptureUiState.Error(AppError.MicPermissionDenied)
    }

    fun retry() {
        _state.value = CaptureUiState.Idle
        // The screen re-invokes startCapture() on Idle (permission already granted).
    }

    private suspend fun resolveAutoTarget(
        track: Track,
        playlists: List<Playlist>,
        ent: Entitlements,
    ): Playlist? {
        // Explicit voice/widget target wins over auto-vibe.
        pendingTargetPlaylistName?.let { name ->
            playlists.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        }
        return if (ent.autoVibeActive) resolveVibeTarget(track, playlists) else null
    }

    private fun Throwable.asAppError(): AppError = this as? AppError ?: AppError.Unknown(this)
}
