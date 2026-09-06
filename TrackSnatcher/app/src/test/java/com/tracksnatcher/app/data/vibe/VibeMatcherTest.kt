package com.tracksnatcher.app.data.vibe

import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VibeMatcherTest {

    private val matcher = VibeMatcher()
    private val playlists = listOf(
        Playlist("1", "Country Road", MusicService.SPOTIFY),
        Playlist("2", "Gym", MusicService.SPOTIFY),
        Playlist("3", "Chill", MusicService.SPOTIFY),
    )

    @Test
    fun `routes hip hop to the gym playlist`() {
        val target = matcher.match(listOf("hip hop", "trap"), playlists)
        assertEquals("Gym", target?.name)
    }

    @Test
    fun `routes americana to the country playlist by name hint`() {
        val target = matcher.match(listOf("alt-country", "americana"), playlists)
        assertEquals("Country Road", target?.name)
    }

    @Test
    fun `returns null when no genre keyword matches`() {
        assertNull(matcher.match(listOf("polka"), playlists))
    }

    @Test
    fun `returns null when the matched bucket has no user playlist`() {
        val onlyGym = listOf(Playlist("2", "Gym", MusicService.SPOTIFY))
        assertNull(matcher.match(listOf("folk", "acoustic"), onlyGym))
    }
}
