package com.tracksnatcher.app.domain.usecase

import com.tracksnatcher.app.domain.model.SonicMemory
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.repository.SonicMemoryRepository
import com.tracksnatcher.app.location.LocationProvider
import java.util.UUID
import javax.inject.Inject

/**
 * Records a captured moment after a successful add. When [includeLocation] is true and the
 * permission is granted, it tags the memory with a coarse place; otherwise the memory is
 * saved without location. Returns the stored memory so the UI can offer the share card.
 */
class RecordSonicMemoryUseCase @Inject constructor(
    private val locationProvider: LocationProvider,
    private val memoryRepository: SonicMemoryRepository,
) {
    suspend operator fun invoke(
        track: Track,
        playlistName: String,
        includeLocation: Boolean,
    ): SonicMemory {
        val place = if (includeLocation) locationProvider.currentPlace() else null
        val memory = SonicMemory(
            id = UUID.randomUUID().toString(),
            trackId = track.recognitionId,
            title = track.title,
            artist = track.artist,
            artworkUrl = track.artworkUrl,
            timestampMs = System.currentTimeMillis(),
            playlistName = playlistName,
            locationName = place?.name,
            latitude = place?.latitude,
            longitude = place?.longitude,
        )
        memoryRepository.save(memory)
        return memory
    }
}
