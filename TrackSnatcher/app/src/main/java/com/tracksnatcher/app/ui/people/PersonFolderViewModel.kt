package com.tracksnatcher.app.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tracksnatcher.app.domain.usecase.GetPinnedPlaylistsUseCase
import com.tracksnatcher.app.domain.usecase.SnatchOutcome
import com.tracksnatcher.app.domain.usecase.SnatchTrackUseCase
import com.tracksnatcher.app.people.PeopleRepository
import com.tracksnatcher.app.people.PersonLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonFolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val peopleRepository: PeopleRepository,
    private val getPinnedPlaylists: GetPinnedPlaylistsUseCase,
    private val snatchTrack: SnatchTrackUseCase,
) : ViewModel() {

    private val userId: String = savedStateHandle.get<String>("userId").orEmpty()

    private val _library = MutableStateFlow<PersonLibrary?>(null)
    val library: StateFlow<PersonLibrary?> = _library.asStateFlow()

    /** Selected track ids (recognitionId). Already-owned tracks can't be selected. */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val paywallChannel = Channel<Unit>(Channel.BUFFERED)
    val paywallRequests: Flow<Unit> = paywallChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            peopleRepository.getPersonLibrary(userId)
                .onSuccess { _library.value = it }
                .onFailure { _message.value = "Couldn't open library" }
        }
    }

    fun toggle(trackId: String) {
        _selected.update { if (trackId in it) it - trackId else it + trackId }
    }

    fun snatchSelected() {
        val lib = _library.value ?: return
        val selectedIds = _selected.value
        val tracks = lib.shareablePlaylists
            .flatMap { it.tracks }
            .filter { !it.alreadyOwned && it.track.recognitionId in selectedIds }
            .map { it.track }
        if (tracks.isEmpty()) {
            _message.value = "Nothing selected"
            return
        }
        viewModelScope.launch {
            val destination = getPinnedPlaylists().first().firstOrNull()
            if (destination == null) {
                _message.value = "Pick a quick-target playlist first"
                return@launch
            }
            var added = 0
            for (track in tracks) {
                when (snatchTrack(track, destination, force = false)) {
                    is SnatchOutcome.Added -> added++
                    SnatchOutcome.LimitReached -> {
                        paywallChannel.trySend(Unit)
                        break
                    }
                    else -> Unit // AlreadyThere / Failed: skip, keep going
                }
            }
            if (added > 0) {
                _selected.value = emptySet()
                _message.value = "Snatched $added to ${destination.name}"
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}
