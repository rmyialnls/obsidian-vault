package com.tracksnatcher.app.data.remote.dto

import com.tracksnatcher.app.domain.model.MusicService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchDtoTest {

    @Test
    fun `maps provider ids onto known services and drops unknown ones`() {
        val dto = MatchDto(
            recognitionId = "rec_1",
            title = "Midnight City",
            artist = "M83",
            providerIds = mapOf(
                "spotify" to "spotify:track:abc",
                "youtube" to "vidXYZ",
                "tidal" to "should-be-dropped",
            ),
        )

        val track = dto.toDomain()

        assertEquals("Midnight City", track.title)
        assertEquals("spotify:track:abc", track.providerId(MusicService.SPOTIFY))
        assertEquals("vidXYZ", track.providerId(MusicService.YOUTUBE_MUSIC))
        assertNull(track.providerId(MusicService.AMAZON_MUSIC))
        assertEquals(2, track.providerIds.size)
    }

    @Test
    fun `isMatch is true only for ok status with a match`() {
        assertTrue(RecognitionResponseDto(status = "ok", match = sampleMatch()).isMatch)
        assertEquals(false, RecognitionResponseDto(status = "no_match", match = null).isMatch)
        assertEquals(false, RecognitionResponseDto(status = "ok", match = null).isMatch)
    }

    private fun sampleMatch() = MatchDto(recognitionId = "r", title = "t", artist = "a")
}
