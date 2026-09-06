package com.tracksnatcher.app.people

import com.tracksnatcher.app.domain.model.Playlist
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.session.SnatchEvent

/**
 * Visibility flags. Napster default: presence is ON; hiding is one toggle, not a wizard.
 * Private playlists never leak — only playlists explicitly marked shareable are exposed.
 */
data class UserPrefs(
    val visibleNearby: Boolean = true,
    val visibleInSession: Boolean = true,
    /** Ghost mode. Widget/Auto/personal adds still work while hidden. */
    val hideSnatches: Boolean = false,
    /** Off until the user connects Spotify and opts in. */
    val showSpotifyProfile: Boolean = false,
    /** Last playlist a song was filed into — shown as the largest/primary quick-target tile. */
    val lastDestinationId: String? = null,
) {
    val nearbyVisible: Boolean get() = visibleNearby && !hideSnatches
    val sessionVisible: Boolean get() = visibleInSession && !hideSnatches
}

/** A row in the Nearby list — a library, not a map pin. */
data class NearbyUser(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    /** e.g. "Something in the Orange → Country" */
    val lastSnatchLabel: String? = null,
)

/** A track inside someone's shareable playlist; [alreadyOwned] greys it out in the raid view. */
data class PersonTrack(
    val track: Track,
    val alreadyOwned: Boolean = false,
)

data class ShareablePlaylistView(
    val playlist: Playlist,
    val tracks: List<PersonTrack>,
)

/** The "folder" view of a person — shareable lists + recent snatches, no wall, no messaging. */
data class PersonLibrary(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val spotifyProfileUrl: String? = null,
    val shareablePlaylists: List<ShareablePlaylistView> = emptyList(),
    val recentShareableSnatches: List<SnatchEvent> = emptyList(),
)
