package com.tracksnatcher.app.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tracksnatcher.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.entitlementDataStore: DataStore<Preferences> by preferencesDataStore("entitlements")

/**
 * Persists tier + rolling free-cap usage in DataStore and exposes a live [Entitlements]
 * stream. The free cap resets on a rolling 30-day cycle: the first snatch of a fresh cycle
 * anchors [KEY_CYCLE_END]; reads past that boundary report a reset count so gates see the
 * new cycle even before a write occurs.
 */
@Singleton
class EntitlementStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val dataStore = context.entitlementDataStore

    private fun now(): Long = System.currentTimeMillis()

    val entitlements: Flow<Entitlements> = dataStore.data
        .map { prefs -> prefs.toEntitlements(now()) }

    /** Snapshot for imperative gate checks. */
    val state = entitlements.stateIn(scope, SharingStarted.Eagerly, Entitlements())

    suspend fun current(): Entitlements = entitlements.first()

    /** Count one successful snatch against the free cap (no-op effect for Pro). */
    suspend fun recordSnatch() {
        dataStore.edit { prefs ->
            val now = now()
            val cycleEnd = prefs[KEY_CYCLE_END] ?: 0L
            if (now >= cycleEnd) {
                // Start a new cycle anchored at now.
                prefs[KEY_CYCLE_END] = now + TierLimits.CYCLE_LENGTH_MS
                prefs[KEY_USED] = 1
            } else {
                prefs[KEY_USED] = (prefs[KEY_USED] ?: 0) + 1
            }
        }
    }

    /** Refund a snatch when the user undoes a wrong add — a failed/undone ID must not burn the cap. */
    suspend fun refundSnatch() {
        dataStore.edit { prefs ->
            val used = prefs[KEY_USED] ?: 0
            if (used > 0) prefs[KEY_USED] = used - 1
        }
    }

    suspend fun setTier(tier: SubscriptionTier) {
        dataStore.edit { it[KEY_TIER] = tier.name }
    }

    suspend fun setAutoVibeMatch(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_VIBE] = enabled }
    }

    private fun Preferences.toEntitlements(now: Long): Entitlements {
        val cycleEnd = this[KEY_CYCLE_END] ?: 0L
        val cycleElapsed = now >= cycleEnd
        return Entitlements(
            tier = this[KEY_TIER]?.let { runCatching { SubscriptionTier.valueOf(it) }.getOrNull() }
                ?: SubscriptionTier.FREE,
            snatchesUsedThisCycle = if (cycleElapsed) 0 else (this[KEY_USED] ?: 0),
            cycleEndEpochMs = cycleEnd,
            autoVibeMatchEnabled = this[KEY_AUTO_VIBE] ?: false,
        )
    }

    private companion object {
        val KEY_TIER = stringPreferencesKey("tier")
        val KEY_USED = intPreferencesKey("snatches_used")
        val KEY_CYCLE_END = longPreferencesKey("cycle_end")
        val KEY_AUTO_VIBE = booleanPreferencesKey("auto_vibe")
    }
}
