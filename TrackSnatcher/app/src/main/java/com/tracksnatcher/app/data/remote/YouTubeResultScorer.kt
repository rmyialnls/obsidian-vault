package com.tracksnatcher.app.data.remote

/**
 * Lessons carried over from SnatchTrack's YouTubeResultScorer:
 *  - NEVER use the channel/uploader as the artist ("*VEVO", "* - Topic", brand channels).
 *  - Prefer official/studio uploads over live / cover / bootleg / remix.
 * Titles are parsed as "Artist - Title (Official Video)" where possible.
 */
object YouTubeResultScorer {

    data class Parsed(val title: String, val artist: String)

    private val separators = listOf(" - ", " – ", " — ", " | ")
    private val noiseTags = Regex("""[\(\[][^\)\]]*(official|video|audio|lyric|hd|4k|visualizer|music video)[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE)
    private val badWords = listOf("live", "cover", "bootleg", "remix", "karaoke", "reaction", "nightcore", "sped up", "slowed", "8d", "tribute", "fan made")
    private val goodWords = listOf("official audio", "official video", "official music video", "provided to youtube")

    fun parse(videoTitle: String, channelTitle: String?): Parsed {
        val clean = noiseTags.replace(videoTitle, "").trim().trim('-', '–', '|').trim()
        separators.forEach { sep ->
            val idx = clean.indexOf(sep)
            if (idx > 0) {
                return Parsed(title = clean.substring(idx + sep.length).trim(), artist = clean.substring(0, idx).trim())
            }
        }
        // No separator in the title. A "- Topic" channel is YouTube's auto-generated artist
        // channel, so its name (minus the suffix) IS the artist; a "*VEVO" channel is a label
        // brand, not an artist name, and is never trusted. Any other channel is usually just
        // the artist's own upload channel, so it's a reasonable fallback.
        val artist = when {
            channelTitle == null -> ""
            channelTitle.endsWith(" - Topic") -> channelTitle.removeSuffix(" - Topic")
            channelTitle.contains("VEVO", ignoreCase = true) -> ""
            else -> channelTitle
        }
        return Parsed(title = clean, artist = artist)
    }

    fun score(videoTitle: String, channelTitle: String?): Int {
        val t = videoTitle.lowercase()
        val c = channelTitle.orEmpty()
        var score = 0
        if (c.endsWith(" - Topic") || c.contains("VEVO", ignoreCase = true)) score += 3
        if (goodWords.any { t.contains(it) }) score += 2
        if (separators.any { videoTitle.contains(it) }) score += 1
        score -= 3 * badWords.count { t.contains(it) }
        return score
    }
}
