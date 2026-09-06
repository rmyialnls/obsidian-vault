package com.tracksnatcher.app.data.vibe

import com.tracksnatcher.app.domain.model.DefaultVibeRules
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.VibeRule
import javax.inject.Inject

/**
 * Pure genre→playlist routing. Given a track's genre tags and the user's playlists, it finds
 * the first [VibeRule] whose genre keywords match, then resolves that bucket to a real
 * playlist by name hint (falling back to the rule label).
 */
class VibeMatcher @Inject constructor() {

    private val rules: List<VibeRule> = DefaultVibeRules.RULES

    fun match(genres: List<String>, playlists: List<Playlist>): Playlist? {
        val normalizedGenres = genres.map { it.lowercase() }
        val rule = rules.firstOrNull { rule ->
            rule.genreKeywords.any { keyword -> normalizedGenres.any { it.contains(keyword) } }
        } ?: return null

        return playlists.firstOrNull { playlist ->
            val name = playlist.name.lowercase()
            rule.playlistNameHints.any { name.contains(it) } || name.contains(rule.label.lowercase())
        }
    }
}
