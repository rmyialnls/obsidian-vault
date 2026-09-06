package com.tracksnatcher.app.data.playlist

import app.cash.turbine.test
import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePlaylistRepositoryTest {

    private val repository = FakePlaylistRepository()

    @Test
    fun `exposes four pinned playlists for the quick-target grid`() = runTest {
        repository.observePinnedPlaylists().test {
            val pinned = awaitItem()
            assertEquals(4, pinned.size)
            assertTrue(pinned.all { it.pinned })
            assertEquals("Favorites", pinned.first().name)
        }
    }

    @Test
    fun `search filters by title or artist`() = runTest {
        val results = repository.searchTracks(MusicService.SPOTIFY, "fleetwood").getOrThrow()
        assertTrue(results.any { it.artist.contains("Fleetwood", ignoreCase = true) })
    }

    @Test
    fun `adding a track to a playlist succeeds`() = runTest {
        val playlist = repository.observePinnedPlaylists().test {
            awaitItem().first().also { cancelAndIgnoreRemainingEvents() }
        }
        val result = repository.addTrackToPlaylist(playlist, Track("r", "Song", "Artist"))
        assertTrue(result.isSuccess)
    }
}
