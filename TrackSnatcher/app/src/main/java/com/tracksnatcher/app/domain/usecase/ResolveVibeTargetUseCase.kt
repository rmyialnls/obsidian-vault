package com.tracksnatcher.app.domain.usecase

import com.tracksnatcher.app.data.vibe.GenreSource
import com.tracksnatcher.app.data.vibe.VibeMatcher
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import javax.inject.Inject

/**
 * Resolves the best-matching playlist for a track by genre. Returns null when no rule matches
 * or the user has no playlist for the matched bucket, in which case the caller should fall
 * back to the manual quick-target grid.
 */
class ResolveVibeTargetUseCase @Inject constructor(
    private val genreSource: GenreSource,
    private val vibeMatcher: VibeMatcher,
) {
    suspend operator fun invoke(track: Track, playlists: List<Playlist>): Playlist? {
        val genres = genreSource.genresFor(track)
        return vibeMatcher.match(genres, playlists)
    }
}
