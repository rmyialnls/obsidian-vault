package com.tracksnatcher.app.session

import com.tracksnatcher.app.domain.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Owns the shared "tape" for the currently active session. In production this is backed by a
 * realtime service (the members and tape sync across devices); the scaffold uses an in-memory
 * fake seeded with a demo road-trip session.
 */
interface SessionRepository {

    /** The device's single active session, or null when not in one. */
    fun observeActiveSession(): Flow<Session?>

    fun observeTape(sessionId: String): Flow<List<TapeItem>>

    fun observeMembers(sessionId: String): Flow<List<SessionMember>>

    /** Host action: create a session from a template and attach a collaborative playlist. */
    suspend fun createSession(template: SessionTemplate, name: String, collabPlaylistName: String): Result<Session>

    /** Guest action: join by short code (from a scanned QR or typed in). */
    suspend fun joinSession(code: String): Result<Session>

    /** Contribute a track to the tape ("play this next"). */
    suspend fun addToTape(sessionId: String, track: Track, source: TapeSource): Result<Unit>

    /** Record that [tapeItemId] was snatched onto a personal playlist (for the recap credits). */
    suspend fun recordSnatch(sessionId: String, tapeItemId: String, event: SnatchEvent): Result<Unit>

    // Host controls.
    suspend fun setApprovalMode(sessionId: String, mode: ApprovalMode): Result<Unit>
    suspend fun setLocked(sessionId: String, locked: Boolean): Result<Unit>
    suspend fun endSession(sessionId: String): Result<Unit>

    /** Leave without ending (guest). */
    suspend fun leaveSession(sessionId: String): Result<Unit>
}
