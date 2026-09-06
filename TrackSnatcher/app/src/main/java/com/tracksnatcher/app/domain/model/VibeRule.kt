package com.tracksnatcher.app.domain.model

/**
 * A routing rule mapping a set of genre keywords to a playlist "bucket". [playlistNameHints]
 * are matched (case-insensitively, as substrings) against the user's real playlist names so a
 * bucket can resolve to whatever the user actually called it.
 */
data class VibeRule(
    val label: String,
    val playlistNameHints: List<String>,
    val genreKeywords: List<String>,
)

/**
 * Sensible default routing. Spotify's `/v1/audio-features` is deprecated for new apps, so
 * this routes on artist genres / tags (from Spotify artist or MusicBrainz endpoints) instead
 * of audio analysis. Users can override these buckets in a later iteration.
 */
object DefaultVibeRules {
    val RULES = listOf(
        VibeRule(
            label = "Country Road",
            playlistNameHints = listOf("country", "americana", "road"),
            genreKeywords = listOf("country", "americana", "bluegrass", "honky", "folk rock"),
        ),
        VibeRule(
            label = "Gym",
            playlistNameHints = listOf("gym", "workout", "hype", "pump"),
            genreKeywords = listOf("hip hop", "hip-hop", "rap", "trap", "edm", "house", "dubstep", "electronic", "drill"),
        ),
        VibeRule(
            label = "Chill",
            playlistNameHints = listOf("chill", "relax", "focus", "calm"),
            genreKeywords = listOf("indie", "folk", "acoustic", "singer-songwriter", "ambient", "lo-fi", "lofi", "dream pop"),
        ),
    )
}
