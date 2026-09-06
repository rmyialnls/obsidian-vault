package com.tracksnatcher.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {

    @Test
    fun `free tier is capped and offers one quick playlist`() {
        val free = Entitlements(tier = SubscriptionTier.FREE, snatchesUsedThisCycle = 0)
        assertTrue(free.canSnatch)
        assertEquals(TierLimits.FREE_MONTHLY_SNATCH_CAP, free.remainingFreeSnatches)
        assertEquals(1, free.quickPlaylistLimit)
        assertFalse(free.autoVibeActive)
    }

    @Test
    fun `free tier blocks snatching once the cap is reached`() {
        val exhausted = Entitlements(
            tier = SubscriptionTier.FREE,
            snatchesUsedThisCycle = TierLimits.FREE_MONTHLY_SNATCH_CAP,
        )
        assertFalse(exhausted.canSnatch)
        assertEquals(0, exhausted.remainingFreeSnatches)
    }

    @Test
    fun `pro tier is unlimited with four quick playlists`() {
        val pro = Entitlements(tier = SubscriptionTier.PRO, snatchesUsedThisCycle = 999)
        assertTrue(pro.canSnatch)
        assertEquals(TierLimits.PRO_QUICK_PLAYLISTS, pro.quickPlaylistLimit)
    }

    @Test
    fun `auto-vibe is active only when pro and toggled on`() {
        assertFalse(Entitlements(tier = SubscriptionTier.FREE, autoVibeMatchEnabled = true).autoVibeActive)
        assertTrue(Entitlements(tier = SubscriptionTier.PRO, autoVibeMatchEnabled = true).autoVibeActive)
        assertFalse(Entitlements(tier = SubscriptionTier.PRO, autoVibeMatchEnabled = false).autoVibeActive)
    }
}
