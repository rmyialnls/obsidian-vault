package com.tracksnatcher.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeResultScorerTest {

    @Test
    fun `splits Artist - Title and strips noise tags`() {
        val parsed = YouTubeResultScorer.parse(
            videoTitle = "Fleetwood Mac - The Chain (Official Audio)",
            channelTitle = "Fleetwood Mac",
        )
        assertEquals("Fleetwood Mac", parsed.artist)
        assertEquals("The Chain", parsed.title)
    }

    @Test
    fun `strips the Topic suffix to recover the artist when the title has no separator`() {
        val parsed = YouTubeResultScorer.parse(videoTitle = "The Chain", channelTitle = "Fleetwood Mac - Topic")
        assertEquals("Fleetwood Mac", parsed.artist)
    }

    @Test
    fun `never trusts a VEVO channel as the artist`() {
        val parsed = YouTubeResultScorer.parse(videoTitle = "The Chain", channelTitle = "FleetwoodMacVEVO")
        assertEquals("", parsed.artist)
    }

    @Test
    fun `falls back to the channel name when it is not a Topic or VEVO channel`() {
        val parsed = YouTubeResultScorer.parse(videoTitle = "The Chain", channelTitle = "Fleetwood Mac")
        assertEquals("Fleetwood Mac", parsed.artist)
    }

    @Test
    fun `scores official studio uploads above live covers and bootlegs`() {
        val official = YouTubeResultScorer.score("Fleetwood Mac - The Chain (Official Audio)", "Fleetwood Mac")
        val live = YouTubeResultScorer.score("The Chain (Live Cover)", "Some Random Channel")
        assertTrue(official > live)
    }

    @Test
    fun `scores Topic and VEVO channels higher as authoritative sources`() {
        val topic = YouTubeResultScorer.score("The Chain", "Fleetwood Mac - Topic")
        val random = YouTubeResultScorer.score("The Chain", "Random Uploads")
        assertTrue(topic > random)
    }
}
