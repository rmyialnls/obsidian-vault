package com.tracksnatcher.app

import com.tracksnatcher.app.domain.model.MusicService

/** Shared OAuth constants and per-service endpoint configuration. */
object OAuth {
    /** Custom scheme registered in the manifest for every OAuth redirect. */
    const val SCHEME = "tracksnatcher"

    object Spotify {
        const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"
        const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
        val SCOPES = listOf(
            "playlist-modify-public",
            "playlist-modify-private",
            "playlist-read-private",
        )
    }

    object Google {
        const val AUTHORIZE_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        val SCOPES = listOf(
            "https://www.googleapis.com/auth/youtube",
        )
    }
}

/** Amazon Music fallback — no API append, so we deep-link into search. */
object AmazonMusic {
    const val PACKAGE = "com.amazon.mp3"
    /** amzn://music/search/<url-encoded query> */
    const val SEARCH_URI_TEMPLATE = "amzn://music/search/%s"
}

/** Base URLs keyed by service for the streaming REST APIs. */
object ApiHosts {
    const val SPOTIFY = "https://api.spotify.com/"
    const val YOUTUBE = "https://www.googleapis.com/"
}

fun MusicService.displayName(): String = when (this) {
    MusicService.SPOTIFY -> "Spotify"
    MusicService.YOUTUBE_MUSIC -> "YouTube Music"
    MusicService.AMAZON_MUSIC -> "Amazon Music"
}
