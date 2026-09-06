package com.tracksnatcher.app.people

import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.session.SnatchEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovery + library-raid surface. Nearby is a list of visible libraries (not a live map);
 * a person view is a folder of their shareable playlists you can multi-snatch from. No
 * messaging, comments, likes, or follower graph.
 */
interface PeopleRepository {
    /** Visible libraries around the user (24/7 while their Nearby toggle is on). */
    fun observeNearby(): Flow<List<NearbyUser>>

    /** The folder view for one person: shareable playlists + recent shareable snatches. */
    suspend fun getPersonLibrary(userId: String): Result<PersonLibrary>
}

/** Seeded fake so Nearby + the raid view demo without a presence backend. */
@Singleton
class FakePeopleRepository @Inject constructor() : PeopleRepository {

    private val nearby = MutableStateFlow(
        listOf(
            NearbyUser("u_jess", "Jess", lastSnatchLabel = "Something in the Orange → Country"),
            NearbyUser("u_marcus", "Marcus", lastSnatchLabel = "The Chain → Road Trip"),
            NearbyUser("u_dana", "Dana", lastSnatchLabel = "Redbone → Chill"),
        ),
    )

    override fun observeNearby(): Flow<List<NearbyUser>> = nearby.asStateFlow()

    override suspend fun getPersonLibrary(userId: String): Result<PersonLibrary> {
        val name = nearby.value.firstOrNull { it.userId == userId }?.displayName ?: "Someone"
        val country = Playlist("their_country", "Country", MusicService.SPOTIFY, trackCount = 3, pinned = false)
        val roadTrip = Playlist("their_road", "Road Trip", MusicService.SPOTIFY, trackCount = 2)
        return Result.success(
            PersonLibrary(
                userId = userId,
                displayName = name,
                spotifyProfileUrl = "https://open.spotify.com/user/$userId",
                shareablePlaylists = listOf(
                    ShareablePlaylistView(
                        playlist = country,
                        tracks = listOf(
                            PersonTrack(Track("s1", "Something in the Orange", "Zach Bryan")),
                            PersonTrack(Track("s3", "Fast Car", "Luke Combs"), alreadyOwned = true),
                            PersonTrack(Track("s4", "The Bones", "Maren Morris")),
                        ),
                    ),
                    ShareablePlaylistView(
                        playlist = roadTrip,
                        tracks = listOf(
                            PersonTrack(Track("s2", "The Chain", "Fleetwood Mac")),
                            PersonTrack(Track("s5", "Take It Easy", "Eagles")),
                        ),
                    ),
                ),
                recentShareableSnatches = listOf(
                    SnatchEvent("se1", "s1", userId, name, "their_country", "Country", System.currentTimeMillis() - 3_600_000),
                ),
            ),
        )
    }
}
