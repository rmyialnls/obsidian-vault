package com.tracksnatcher.app.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A purchasable Pro offering, normalised across subscriptions and the lifetime unlock. */
data class SubscriptionProduct(
    val productId: String,
    val title: String,
    val formattedPrice: String,
    val period: BillingPeriod,
    val isBestValue: Boolean = false,
)

enum class BillingPeriod { MONTHLY, YEARLY, LIFETIME }

/** Outcome of a purchase / restore attempt, surfaced to the paywall. */
sealed interface PurchaseResult {
    data object Purchased : PurchaseResult
    data object Restored : PurchaseResult
    data object NothingToRestore : PurchaseResult
    data object Cancelled : PurchaseResult
    data object Pending : PurchaseResult
    data class Error(val message: String) : PurchaseResult
}

/**
 * Abstracts the billing backend so callers never touch Play Billing directly — a RevenueCat
 * implementation could be swapped in behind this interface without changing the paywall or
 * the gating logic. Successful purchases flip the tier in [EntitlementStore].
 */
interface SubscriptionManager {
    /** Live catalogue of Pro products (empty until [start] connects and queries). */
    val products: StateFlow<List<SubscriptionProduct>>

    /** One-shot purchase/restore results for the UI to react to. */
    val events: Flow<PurchaseResult>

    /** Connect to billing and load products; safe to call multiple times. */
    fun start()

    /** Launch the platform purchase flow for [productId]. Requires a foreground [activity]. */
    suspend fun purchase(activity: Activity, productId: String)

    /** Re-check owned purchases and restore Pro if found. */
    suspend fun restorePurchases()
}
