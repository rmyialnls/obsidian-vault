package com.tracksnatcher.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tracksnatcher.app.LocalIdentity
import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.usecase.GetPinnedPlaylistsUseCase
import com.tracksnatcher.app.domain.usecase.SnatchOutcome
import com.tracksnatcher.app.domain.usecase.SnatchTrackUseCase
import com.tracksnatcher.app.session.ApprovalMode
import com.tracksnatcher.app.session.Session
import com.tracksnatcher.app.session.SessionMember
import com.tracksnatcher.app.session.SessionRepository
import com.tracksnatcher.app.session.SessionTemplate
import com.tracksnatcher.app.session.SnatchEvent
import com.tracksnatcher.app.session.TapeItem
import com.tracksnatcher.app.session.TapeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val getPinnedPlaylists: GetPinnedPlaylistsUseCase,
    private val snatchTrack: SnatchTrackUseCase,
) : ViewModel() {

    val session: StateFlow<Session?> = sessionRepository.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tape: StateFlow<List<TapeItem>> = session
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else sessionRepository.observeTape(s.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val members: StateFlow<List<SessionMember>> = session
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else sessionRepository.observeMembers(s.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val paywallChannel = Channel<Unit>(Channel.BUFFERED)
    val paywallRequests: Flow<Unit> = paywallChannel.receiveAsFlow()

    val isHost: Boolean get() = session.value?.hostUserId == LocalIdentity.ID

    fun createSession(template: SessionTemplate, name: String) {
        viewModelScope.launch {
            val playlistName = name.ifBlank { "${template.label} tape" }
            sessionRepository.createSession(template, name.ifBlank { defaultName(template) }, playlistName)
                .onFailure { _message.value = "Couldn't start session" }
        }
    }

    fun joinSession(code: String) {
        viewModelScope.launch {
            sessionRepository.joinSession(code.trim())
                .onFailure { _message.value = "Couldn't join — check the code" }
        }
    }

    /** Contribute a demo track to the tape (stands in for ambient/library add in the scaffold). */
    fun addSampleToTape() {
        val s = session.value ?: return
        viewModelScope.launch {
            val track = Track(
                recognitionId = "sample_${UUID.randomUUID()}",
                title = "Take It Easy",
                artist = "Eagles",
                providerIds = mapOf(MusicService.SPOTIFY to "spotify:track:sample"),
            )
            sessionRepository.addToTape(s.id, track, TapeSource.FROM_LIBRARY)
        }
    }

    fun snatch(item: TapeItem, force: Boolean = false) {
        val s = session.value ?: return
        viewModelScope.launch {
            val destination = getPinnedPlaylists().first().firstOrNull()
            if (destination == null) {
                _message.value = "Pick a quick-target playlist first"
                return@launch
            }
            when (val outcome = snatchTrack(item.track, destination, force)) {
                is SnatchOutcome.Added -> {
                    sessionRepository.recordSnatch(
                        sessionId = s.id,
                        tapeItemId = item.id,
                        event = SnatchEvent(
                            id = UUID.randomUUID().toString(),
                            trackId = item.track.recognitionId,
                            byUserId = LocalIdentity.ID,
                            byName = LocalIdentity.NAME,
                            destinationPlaylistId = destination.id,
                            destinationPlaylistName = destination.name,
                            atEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    _message.value = "Snatched to ${destination.name}"
                }
                is SnatchOutcome.AlreadyThere -> _message.value = "Already in ${outcome.duplicate.playlistName}"
                SnatchOutcome.LimitReached -> paywallChannel.trySend(Unit)
                is SnatchOutcome.Failed -> _message.value = "Couldn't snatch"
            }
        }
    }

    fun toggleApproval() {
        val s = session.value ?: return
        val next = if (s.approvalMode == ApprovalMode.APPROVE) ApprovalMode.AUTO_ADD else ApprovalMode.APPROVE
        viewModelScope.launch { sessionRepository.setApprovalMode(s.id, next) }
    }

    fun toggleLock() {
        val s = session.value ?: return
        viewModelScope.launch { sessionRepository.setLocked(s.id, !s.locked) }
    }

    fun endOrLeave() {
        val s = session.value ?: return
        viewModelScope.launch {
            if (isHost) sessionRepository.endSession(s.id) else sessionRepository.leaveSession(s.id)
        }
    }

    fun consumeMessage() { _message.value = null }

    private fun defaultName(template: SessionTemplate): String = "${template.label} · now"
}
