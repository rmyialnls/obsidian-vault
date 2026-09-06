package com.tracksnatcher.app.session

import com.tracksnatcher.app.domain.model.Track

/** Session flavor — only default rules + copy differ; the object is the same. */
enum class SessionTemplate(val label: String) {
    TRIP("Trip"),
    PARTY("Party"),
    WEDDING("Wedding"),
    CAMPFIRE("Campfire"),
    CUSTOM("Custom"),
}

/** How new tape adds are handled. WEDDING/DJ default to APPROVE; others AUTO_ADD. */
enum class ApprovalMode { AUTO_ADD, APPROVE }

/** Where a tape item came from. */
enum class TapeSource { AMBIENT, FROM_LIBRARY }

/**
 * A QR-addressable room with one collaborative playlist (the "tape"). The official
 * streaming playlist survives after the session ends.
 */
data class Session(
    val id: String,
    val code: String,                 // short human code, also encoded in the QR
    val name: String,                 // e.g. "Calgary → Fernie · Sep 5"
    val template: SessionTemplate,
    val hostUserId: String,
    val hostName: String,
    val collabPlaylistName: String,
    val approvalMode: ApprovalMode,
    val addsPerPersonCap: Int? = null,
    val locked: Boolean = false,
    val active: Boolean = true,
)

data class SessionMember(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isHost: Boolean = false,
)

/** One entry on the tape, with the credits that power the recap. */
data class TapeItem(
    val id: String,
    val track: Track,
    val addedByUserId: String,
    val addedByName: String,
    val addedAtEpochMs: Long,
    val source: TapeSource,
    val coarsePlace: String? = null,
    val snatches: List<SnatchEvent> = emptyList(),
    val pendingApproval: Boolean = false,
)

/** Recorded when someone copies a tape/library track onto their own playlist. */
data class SnatchEvent(
    val id: String,
    val trackId: String,
    val byUserId: String,
    val byName: String,
    val destinationPlaylistId: String,
    val destinationPlaylistName: String,
    val atEpochMs: Long,
    val coarsePlace: String? = null,
)
