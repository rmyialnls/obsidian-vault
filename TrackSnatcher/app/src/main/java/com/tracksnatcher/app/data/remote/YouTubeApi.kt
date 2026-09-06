package com.tracksnatcher.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * YouTube Data API v3 (YouTube Music playlists). Bearer token attached by interceptor.
 * Base URL: https://www.googleapis.com/
 */
interface YouTubeApi {

    @GET("youtube/v3/playlists")
    suspend fun getMyPlaylists(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 50,
    ): YouTubePlaylistListDto

    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("q") query: String,
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20,
    ): YouTubeSearchListDto

    /** Existing items in a playlist — used for duplicate detection. */
    @GET("youtube/v3/playlistItems")
    suspend fun getPlaylistItems(
        @Query("playlistId") playlistId: String,
        @Query("part") part: String = "snippet",
        @Query("maxResults") maxResults: Int = 50,
    ): YouTubePlaylistItemsListDto

    /** POST https://www.googleapis.com/youtube/v3/playlistItems?part=snippet */
    @POST("youtube/v3/playlistItems")
    suspend fun insertPlaylistItem(
        @Query("part") part: String = "snippet",
        @Body body: YouTubePlaylistItemDto,
    ): YouTubePlaylistItemDto
}

@Serializable
data class YouTubePlaylistItemsListDto(val items: List<YouTubePlaylistItemDto> = emptyList())

@Serializable
data class YouTubePlaylistListDto(val items: List<YouTubePlaylistDto> = emptyList())

@Serializable
data class YouTubePlaylistDto(
    val id: String,
    val snippet: YouTubeSnippetDto = YouTubeSnippetDto(),
    val contentDetails: YouTubeContentDetailsDto = YouTubeContentDetailsDto(),
)

@Serializable
data class YouTubeContentDetailsDto(@SerialName("itemCount") val itemCount: Int = 0)

@Serializable
data class YouTubeSearchListDto(val items: List<YouTubeSearchItemDto> = emptyList())

@Serializable
data class YouTubeSearchItemDto(val id: YouTubeVideoIdDto, val snippet: YouTubeSnippetDto = YouTubeSnippetDto())

@Serializable
data class YouTubeVideoIdDto(@SerialName("videoId") val videoId: String? = null)

@Serializable
data class YouTubeSnippetDto(
    val title: String = "",
    val channelTitle: String? = null,
    val playlistId: String? = null,
    val resourceId: YouTubeResourceIdDto? = null,
    val thumbnails: YouTubeThumbnailsDto? = null,
)

@Serializable
data class YouTubeResourceIdDto(val kind: String = "youtube#video", val videoId: String)

@Serializable
data class YouTubeThumbnailsDto(val default: YouTubeThumbnailDto? = null)

@Serializable
data class YouTubeThumbnailDto(val url: String)

/** Body for playlistItems.insert — appends [snippet.resourceId.videoId] to a playlist. */
@Serializable
data class YouTubePlaylistItemDto(val snippet: YouTubeSnippetDto)
