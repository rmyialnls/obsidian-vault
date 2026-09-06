package com.tracksnatcher.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Spotify Web API. The OAuth bearer token is attached by an auth interceptor, so these
 * signatures stay clean. Base URL: https://api.spotify.com/
 */
interface SpotifyApi {

    @GET("v1/me/playlists")
    suspend fun getMyPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SpotifyPagingDto<SpotifyPlaylistDto>

    @GET("v1/search")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 20,
    ): SpotifySearchResponseDto

    /** Artist genres feed the Vibe Match router (audio-features is deprecated for new apps). */
    @GET("v1/artists/{id}")
    suspend fun getArtist(@Path("id") artistId: String): SpotifyArtistDetailDto

    @GET("v1/search")
    suspend fun searchArtists(
        @Query("q") query: String,
        @Query("type") type: String = "artist",
        @Query("limit") limit: Int = 1,
    ): SpotifyArtistSearchResponseDto

    /** Existing tracks in a playlist — used for duplicate detection. */
    @GET("v1/playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("playlist_id") playlistId: String,
        @Query("fields") fields: String = "items(added_at,track(uri,name,artists(name)))",
        @Query("limit") limit: Int = 100,
    ): SpotifyPlaylistTracksDto

    /** POST https://api.spotify.com/v1/playlists/{playlist_id}/tracks */
    @POST("v1/playlists/{playlist_id}/tracks")
    suspend fun addTracks(
        @Path("playlist_id") playlistId: String,
        @Body body: SpotifyAddTracksRequest,
    ): SpotifySnapshotDto

    /** DELETE https://api.spotify.com/v1/playlists/{playlist_id}/tracks — backs Undo. */
    @HTTP(method = "DELETE", path = "v1/playlists/{playlist_id}/tracks", hasBody = true)
    suspend fun removeTracks(
        @Path("playlist_id") playlistId: String,
        @Body body: SpotifyRemoveTracksRequest,
    ): SpotifySnapshotDto
}

@Serializable
data class SpotifyRemoveTracksRequest(val tracks: List<SpotifyTrackUriRef>)

@Serializable
data class SpotifyTrackUriRef(val uri: String)

@Serializable
data class SpotifyPlaylistTracksDto(val items: List<SpotifyPlaylistItemDto> = emptyList())

@Serializable
data class SpotifyPlaylistItemDto(
    @SerialName("added_at") val addedAt: String? = null,
    val track: SpotifyTrackDto? = null,
)

@Serializable
data class SpotifyPagingDto<T>(val items: List<T> = emptyList(), val total: Int = 0)

@Serializable
data class SpotifyPlaylistDto(
    val id: String,
    val name: String,
    val tracks: SpotifyTracksRefDto = SpotifyTracksRefDto(),
    val images: List<SpotifyImageDto> = emptyList(),
)

@Serializable
data class SpotifyTracksRefDto(val total: Int = 0)

@Serializable
data class SpotifyImageDto(val url: String)

@Serializable
data class SpotifySearchResponseDto(val tracks: SpotifyPagingDto<SpotifyTrackDto> = SpotifyPagingDto())

@Serializable
data class SpotifyTrackDto(
    val id: String,
    val uri: String,
    val name: String,
    val artists: List<SpotifyArtistDto> = emptyList(),
    val album: SpotifyAlbumDto? = null,
)

@Serializable
data class SpotifyArtistDto(val id: String? = null, val name: String)

@Serializable
data class SpotifyArtistDetailDto(
    val id: String,
    val name: String,
    val genres: List<String> = emptyList(),
)

@Serializable
data class SpotifyArtistSearchResponseDto(
    val artists: SpotifyPagingDto<SpotifyArtistDetailDto> = SpotifyPagingDto(),
)

@Serializable
data class SpotifyAlbumDto(val name: String? = null, val images: List<SpotifyImageDto> = emptyList())

@Serializable
data class SpotifyAddTracksRequest(
    val uris: List<String>,
    val position: Int? = null,
)

@Serializable
data class SpotifySnapshotDto(@SerialName("snapshot_id") val snapshotId: String)
