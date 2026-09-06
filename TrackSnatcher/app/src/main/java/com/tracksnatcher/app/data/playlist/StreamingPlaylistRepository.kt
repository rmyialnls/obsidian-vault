package com.tracksnatcher.app.data.playlist

import com.tracksnatcher.app.auth.google.GoogleAuthManager
import com.tracksnatcher.app.auth.spotify.SpotifyAuthManager
import com.tracksnatcher.app.data.remote.SpotifyAddTracksRequest
import com.tracksnatcher.app.data.remote.SpotifyApi
import com.tracksnatcher.app.data.remote.SpotifyRemoveTracksRequest
import com.tracksnatcher.app.data.remote.SpotifyTrackUriRef
import com.tracksnatcher.app.data.remote.YouTubeApi
import com.tracksnatcher.app.data.remote.YouTubePlaylistItemDto
import com.tracksnatcher.app.data.remote.YouTubeResourceIdDto
import com.tracksnatcher.app.data.remote.YouTubeSnippetDto
import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.DuplicateMatch
import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.repository.PlaylistRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [PlaylistRepository] backed by the Spotify and YouTube Data APIs. Auth tokens
 * are resolved lazily through the OAuth managers (attached as bearer headers by the network
 * layer). The first few playlists returned by the linked service seed the quick-target grid;
 * a later iteration can persist explicit pin choices.
 *
 * Not bound by default — see [com.tracksnatcher.app.di.RepositoryModule].
 */
@Singleton
class StreamingPlaylistRepository @Inject constructor(
    private val spotifyApi: SpotifyApi,
    private val youTubeApi: YouTubeApi,
    private val spotifyAuthManager: SpotifyAuthManager,
    private val googleAuthManager: GoogleAuthManager,
) : PlaylistRepository {

    private val pinnedCache = MutableStateFlow<List<Playlist>>(emptyList())

    override fun observePinnedPlaylists(): Flow<List<Playlist>> = pinnedCache.asStateFlow()

    /** Refresh the pinned grid from whichever service is currently linked. */
    suspend fun refreshPinned() {
        val service = linkedService() ?: return
        getPlaylists(service).onSuccess { playlists ->
            pinnedCache.value = playlists.take(PINNED_LIMIT).map { it.copy(pinned = true) }
        }
    }

    override suspend fun getPlaylists(service: MusicService): Result<List<Playlist>> = safeCall(service) {
        when (service) {
            MusicService.SPOTIFY -> spotifyApi.getMyPlaylists().items.map {
                Playlist(
                    id = it.id,
                    name = it.name,
                    service = MusicService.SPOTIFY,
                    trackCount = it.tracks.total,
                    imageUrl = it.images.firstOrNull()?.url,
                )
            }
            MusicService.YOUTUBE_MUSIC -> youTubeApi.getMyPlaylists().items.map {
                Playlist(
                    id = it.id,
                    name = it.snippet.title,
                    service = MusicService.YOUTUBE_MUSIC,
                    trackCount = it.contentDetails.itemCount,
                    imageUrl = it.snippet.thumbnails?.default?.url,
                )
            }
            MusicService.AMAZON_MUSIC -> emptyList() // Amazon has no list API; deep-link only.
        }
    }

    override suspend fun searchTracks(service: MusicService, query: String): Result<List<Track>> =
        safeCall(service) {
            when (service) {
                MusicService.SPOTIFY -> spotifyApi.searchTracks(query).tracks.items.map { dto ->
                    Track(
                        recognitionId = dto.id,
                        title = dto.name,
                        artist = dto.artists.joinToString(", ") { it.name },
                        album = dto.album?.name,
                        artworkUrl = dto.album?.images?.firstOrNull()?.url,
                        providerIds = mapOf(MusicService.SPOTIFY to dto.uri),
                    )
                }
                MusicService.YOUTUBE_MUSIC -> youTubeApi.searchVideos(query).items.mapNotNull { item ->
                    val videoId = item.id.videoId ?: return@mapNotNull null
                    Track(
                        recognitionId = videoId,
                        title = item.snippet.title,
                        artist = item.snippet.channelTitle.orEmpty(),
                        artworkUrl = item.snippet.thumbnails?.default?.url,
                        providerIds = mapOf(MusicService.YOUTUBE_MUSIC to videoId),
                    )
                }
                MusicService.AMAZON_MUSIC -> emptyList()
            }
        }

    override suspend fun findDuplicate(playlist: Playlist, track: Track): Result<DuplicateMatch?> =
        safeCall(playlist.service) {
            val providerId = track.providerId(playlist.service)
            when (playlist.service) {
                MusicService.SPOTIFY -> spotifyApi.getPlaylistTracks(playlist.id).items
                    .firstOrNull { item ->
                        val t = item.track ?: return@firstOrNull false
                        (providerId != null && t.uri == providerId) ||
                            (t.name.equals(track.title, ignoreCase = true) &&
                                t.artists.any { it.name.equals(track.artist, ignoreCase = true) })
                    }
                    ?.let { DuplicateMatch(playlist.name, it.addedAt?.toEpochMsOrNull()) }

                MusicService.YOUTUBE_MUSIC -> youTubeApi.getPlaylistItems(playlist.id).items
                    .firstOrNull { item ->
                        (providerId != null && item.snippet.resourceId?.videoId == providerId) ||
                            item.snippet.title.equals("${track.title}", ignoreCase = true)
                    }
                    ?.let { DuplicateMatch(playlist.name) }

                MusicService.AMAZON_MUSIC -> null
            }
        }

    override suspend fun addTrackToPlaylist(playlist: Playlist, track: Track): Result<Unit> =
        safeCall(playlist.service) {
            val providerId = track.providerId(playlist.service)
                ?: throw AppError.NoMatch // no id for this service on the matched track
            when (playlist.service) {
                MusicService.SPOTIFY -> spotifyApi.addTracks(
                    playlistId = playlist.id,
                    body = SpotifyAddTracksRequest(uris = listOf(providerId)),
                )
                MusicService.YOUTUBE_MUSIC -> youTubeApi.insertPlaylistItem(
                    body = YouTubePlaylistItemDto(
                        snippet = YouTubeSnippetDto(
                            playlistId = playlist.id,
                            resourceId = YouTubeResourceIdDto(videoId = providerId),
                        ),
                    ),
                )
                MusicService.AMAZON_MUSIC -> throw AppError.Unknown() // handled via deep link, not here
            }
        }

    override suspend fun removeTrackFromPlaylist(playlist: Playlist, track: Track): Result<Unit> =
        safeCall(playlist.service) {
            val providerId = track.providerId(playlist.service) ?: throw AppError.NoMatch
            when (playlist.service) {
                MusicService.SPOTIFY -> spotifyApi.removeTracks(
                    playlistId = playlist.id,
                    body = SpotifyRemoveTracksRequest(listOf(SpotifyTrackUriRef(providerId))),
                )
                // YouTube removal needs the playlistItem id (a lookup); Amazon has no API.
                else -> throw AppError.Unknown()
            }
        }

    private suspend fun linkedService(): MusicService? = when {
        spotifyAuthManager.isAuthorized() -> MusicService.SPOTIFY
        googleAuthManager.isAuthorized() -> MusicService.YOUTUBE_MUSIC
        else -> null
    }

    /** Wrap a call, mapping transport/auth failures onto the domain error taxonomy. */
    private inline fun <T> safeCall(service: MusicService, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: AppError) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(AppError.Network(e))
    } catch (t: Throwable) {
        // A 401 surfaces here via Retrofit's HttpException; treat as needing re-auth.
        Result.failure(AppError.NotAuthorized(service))
    }

    private fun String.toEpochMsOrNull(): Long? =
        runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

    private companion object {
        const val PINNED_LIMIT = 4
    }
}
