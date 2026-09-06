package com.tracksnatcher.app.ui.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManualUiState(
    val query: String = "",
    val searchResults: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedTrack: Track? = null,
    val isSearching: Boolean = false,
    val message: String? = null,
)

/** Backs the in-app manual add flow: search a track, pick a playlist, add. */
@HiltViewModel
class ManualViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    // Single-service scaffold; a later iteration lets the user switch linked services.
    private val service = MusicService.SPOTIFY

    private val _state = MutableStateFlow(ManualUiState())
    val state: StateFlow<ManualUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getPlaylists(service)
                .onSuccess { list -> _state.update { it.copy(playlists = list) } }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(isSearching = true, message = null) }
        viewModelScope.launch {
            playlistRepository.searchTracks(service, query)
                .onSuccess { results -> _state.update { it.copy(searchResults = results, isSearching = false) } }
                .onFailure { _state.update { it.copy(isSearching = false, message = "Search failed") } }
        }
    }

    fun selectTrack(track: Track) {
        _state.update { it.copy(selectedTrack = track) }
    }

    fun addSelectedTo(playlist: Playlist) {
        val track = _state.value.selectedTrack ?: return
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlist, track)
                .onSuccess { _state.update { it.copy(message = "Added to ${playlist.name}") } }
                .onFailure { _state.update { it.copy(message = "Couldn't add to ${playlist.name}") } }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
