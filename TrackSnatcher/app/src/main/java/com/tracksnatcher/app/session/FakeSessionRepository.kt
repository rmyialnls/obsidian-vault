package com.tracksnatcher.app.session

import com.tracksnatcher.app.LocalIdentity
import com.tracksnatcher.app.domain.model.MusicService
import com.tracksnatcher.app.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory session backend seeded with a demo road-trip so the whole session/tape/recap flow
 * is demoable without a realtime service. A production impl syncs [activeSession], [tape] and
 * [members] across devices.
 */
@Singleton
class FakeSessionRepository @Inject constructor() : SessionRepository {

    private val activeSession = MutableStateFlow<Session?>(seedSession)
    private val tape = MutableStateFlow(seedTape)
    private val members = MutableStateFlow(seedMembers)

    override fun observeActiveSession(): Flow<Session?> = activeSession.asStateFlow()

    override fun observeTape(sessionId: String): Flow<List<TapeItem>> =
        tape.map { items -> items.sortedByDescending { it.addedAtEpochMs } }

    override fun observeMembers(sessionId: String): Flow<List<SessionMember>> = members.asStateFlow()

    override suspend fun createSession(
        template: SessionTemplate,
        name: String,
        collabPlaylistName: String,
    ): Result<Session> {
        val session = Session(
            id = UUID.randomUUID().toString(),
            code = randomCode(),
            name = name,
            template = template,
            hostUserId = LocalIdentity.ID,
            hostName = LocalIdentity.NAME,
            collabPlaylistName = collabPlaylistName,
            approvalMode = if (template == SessionTemplate.WEDDING) ApprovalMode.APPROVE else ApprovalMode.AUTO_ADD,
        )
        activeSession.value = session
        tape.value = emptyList()
        members.value = listOf(SessionMember(LocalIdentity.ID, LocalIdentity.NAME, isHost = true))
        return Result.success(session)
    }

    override suspend fun joinSession(code: String): Result<Session> {
        // Fake join always drops the user into the seeded demo session.
        val session = seedSession.copy(code = code.uppercase())
        activeSession.value = session
        tape.value = seedTape
        members.value = seedMembers + SessionMember(LocalIdentity.ID, LocalIdentity.NAME)
        return Result.success(session)
    }

    override suspend fun addToTape(sessionId: String, track: Track, source: TapeSource): Result<Unit> {
        val session = activeSession.value ?: return Result.failure(IllegalStateException("No active session"))
        val item = TapeItem(
            id = UUID.randomUUID().toString(),
            track = track,
            addedByUserId = LocalIdentity.ID,
            addedByName = LocalIdentity.NAME,
            addedAtEpochMs = System.currentTimeMillis(),
            source = source,
            pendingApproval = session.approvalMode == ApprovalMode.APPROVE && session.hostUserId != LocalIdentity.ID,
        )
        tape.update { it + item }
        return Result.success(Unit)
    }

    override suspend fun recordSnatch(sessionId: String, tapeItemId: String, event: SnatchEvent): Result<Unit> {
        tape.update { items ->
            items.map { if (it.id == tapeItemId) it.copy(snatches = it.snatches + event) else it }
        }
        return Result.success(Unit)
    }

    override suspend fun setApprovalMode(sessionId: String, mode: ApprovalMode): Result<Unit> {
        activeSession.update { it?.copy(approvalMode = mode) }
        return Result.success(Unit)
    }

    override suspend fun setLocked(sessionId: String, locked: Boolean): Result<Unit> {
        activeSession.update { it?.copy(locked = locked) }
        return Result.success(Unit)
    }

    override suspend fun endSession(sessionId: String): Result<Unit> {
        activeSession.update { it?.copy(active = false) }
        activeSession.value = null
        return Result.success(Unit)
    }

    override suspend fun leaveSession(sessionId: String): Result<Unit> {
        activeSession.value = null
        return Result.success(Unit)
    }

    private fun randomCode(): String =
        (1..5).map { CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    private companion object {
        const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        val seedSession = Session(
            id = "sess_demo",
            code = "FERNIE",
            name = "Calgary → Fernie · Sep 5",
            template = SessionTemplate.TRIP,
            hostUserId = "u_jess",
            hostName = "Jess",
            collabPlaylistName = "Fernie Run",
            approvalMode = ApprovalMode.AUTO_ADD,
        )

        val seedMembers = listOf(
            SessionMember("u_jess", "Jess", isHost = true),
            SessionMember("u_marcus", "Marcus"),
            SessionMember("u_dana", "Dana"),
        )

        val seedTape = listOf(
            TapeItem(
                id = "t1",
                track = Track("s1", "Something in the Orange", "Zach Bryan", providerIds = mapOf(MusicService.SPOTIFY to "spotify:track:s1")),
                addedByUserId = "u_jess", addedByName = "Jess",
                addedAtEpochMs = System.currentTimeMillis() - 20 * 60_000,
                source = TapeSource.AMBIENT, coarsePlace = "Highway 3",
                snatches = listOf(
                    SnatchEvent("e1", "s1", "u_marcus", "Marcus", "pl_country", "Country", System.currentTimeMillis() - 18 * 60_000),
                ),
            ),
            TapeItem(
                id = "t2",
                track = Track("s2", "The Chain", "Fleetwood Mac", providerIds = mapOf(MusicService.SPOTIFY to "spotify:track:s2")),
                addedByUserId = "u_marcus", addedByName = "Marcus",
                addedAtEpochMs = System.currentTimeMillis() - 12 * 60_000,
                source = TapeSource.FROM_LIBRARY,
            ),
        )
    }
}
