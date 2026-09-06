package com.tracksnatcher.app.billing

enum class SubscriptionTier { FREE, PRO }

/** Product ids as configured in the Play Console. */
object BillingProducts {
    const val PRO_MONTHLY = "pro_monthly"       // subscription
    const val PRO_YEARLY = "pro_yearly"         // subscription
    const val PRO_LIFETIME = "pro_lifetime"     // one-time (in-app) product

    val SUBSCRIPTIONS = listOf(PRO_MONTHLY, PRO_YEARLY)
    val ONE_TIME = listOf(PRO_LIFETIME)
    val ALL = SUBSCRIPTIONS + ONE_TIME
}

/** Per-tier limits. Free is deliberately tight to drive conversion. */
object TierLimits {
    const val FREE_MONTHLY_SNATCH_CAP = 18
    const val FREE_QUICK_PLAYLISTS = 1
    const val PRO_QUICK_PLAYLISTS = 4
    /** Rolling reset window for the free monthly cap. */
    const val CYCLE_LENGTH_MS = 30L * 24 * 60 * 60 * 1000
}

/**
 * The single source of truth for what the current user can do. Derived from persisted
 * billing + usage state ([EntitlementStore]) and read by every gate in the app.
 */
data class Entitlements(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val snatchesUsedThisCycle: Int = 0,
    val cycleEndEpochMs: Long = 0L,
    val autoVibeMatchEnabled: Boolean = false,
) {
    val isPro: Boolean get() = tier == SubscriptionTier.PRO

    val remainingFreeSnatches: Int
        get() = (TierLimits.FREE_MONTHLY_SNATCH_CAP - snatchesUsedThisCycle).coerceAtLeast(0)

    /** Pro is unlimited; free is capped per cycle. */
    val canSnatch: Boolean
        get() = isPro || remainingFreeSnatches > 0

    /** Number of quick-target buttons to surface in the capture grid. */
    val quickPlaylistLimit: Int
        get() = if (isPro) TierLimits.PRO_QUICK_PLAYLISTS else TierLimits.FREE_QUICK_PLAYLISTS

    /** Auto-vibe routing is a Pro feature and must also be toggled on. */
    val autoVibeActive: Boolean
        get() = isPro && autoVibeMatchEnabled
}
