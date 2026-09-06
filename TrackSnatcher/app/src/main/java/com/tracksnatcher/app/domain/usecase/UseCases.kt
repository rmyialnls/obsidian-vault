package com.tracksnatcher.app.domain.usecase

import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.repository.PlaylistRepository
import com.tracksnatcher.app.domain.repository.RecognitionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Identify the currently playing ambient track from a raw audio buffer. */
class IdentifyTrackUseCase @Inject constructor(
    private val recognitionRepository: RecognitionRepository,
) {
    suspend operator fun invoke(pcmWav: ByteArray): Result<Track> =
        recognitionRepository.identify(pcmWav)
}

/** Stream the 3–4 pinned playlists that populate the quick-target grid. */
class GetPinnedPlaylistsUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(): Flow<List<Playlist>> =
        playlistRepository.observePinnedPlaylists()
}

/** Append a recognised track to the chosen playlist. */
class AddTrackToPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlist: Playlist, track: Track): Result<Unit> =
        playlistRepository.addTrackToPlaylist(playlist, track)
}
