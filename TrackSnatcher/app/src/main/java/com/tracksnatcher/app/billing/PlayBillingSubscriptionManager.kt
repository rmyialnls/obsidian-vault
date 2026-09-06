package com.tracksnatcher.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.tracksnatcher.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google Play Billing v7 implementation of [SubscriptionManager]. Owns the [BillingClient]
 * lifecycle, normalises product details into [SubscriptionProduct]s, and — on a verified
 * purchase or restore — promotes the user to [SubscriptionTier.PRO] in [EntitlementStore].
 *
 * NOTE: production code should verify purchases server-side before granting entitlement;
 * this scaffold trusts the on-device signal to keep the flow demonstrable.
 */
@Singleton
class PlayBillingSubscriptionManager @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val entitlementStore: EntitlementStore,
) : SubscriptionManager, PurchasesUpdatedListener {

    private val _products = MutableStateFlow<List<SubscriptionProduct>>(emptyList())
    override val products: StateFlow<List<SubscriptionProduct>> = _products.asStateFlow()

    private val _events = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 8)
    override val events: Flow<PurchaseResult> = _events.asSharedFlow()

    /** ProductId -> details, so purchase() can launch without re-querying. */
    private val detailsCache = mutableMapOf<String, ProductDetails>()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    override fun start() {
        scope.launch {
            if (ensureConnected()) queryProducts()
        }
    }

    private suspend fun ensureConnected(): Boolean {
        if (billingClient.isReady) return true
        return suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    private suspend fun queryProducts() {
        val subs = queryDetails(BillingProducts.SUBSCRIPTIONS, BillingClient.ProductType.SUBS)
        val oneTime = queryDetails(BillingProducts.ONE_TIME, BillingClient.ProductType.INAPP)
        (subs + oneTime).forEach { detailsCache[it.productId] = it }
        _products.value = (subs + oneTime).mapNotNull { it.toSubscriptionProduct() }
            .sortedBy { it.period.ordinal }
    }

    private suspend fun queryDetails(ids: List<String>, type: String): List<ProductDetails> {
        if (ids.isEmpty()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(type)
                        .build()
                },
            )
            .build()
        return billingClient.queryProductDetails(params).productDetailsList.orEmpty()
    }

    override suspend fun purchase(activity: Activity, productId: String) {
        if (!ensureConnected()) {
            _events.emit(PurchaseResult.Error("Billing unavailable"))
            return
        }
        val details = detailsCache[productId] ?: run {
            queryProducts()
            detailsCache[productId]
        }
        if (details == null) {
            _events.emit(PurchaseResult.Error("Product not found"))
            return
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        // Subscriptions require an offer token; one-time products do not.
        details.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let {
            productParamsBuilder.setOfferToken(it)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override suspend fun restorePurchases() {
        if (!ensureConnected()) {
            _events.emit(PurchaseResult.Error("Billing unavailable"))
            return
        }
        val active = ownedPurchases(BillingClient.ProductType.SUBS) +
            ownedPurchases(BillingClient.ProductType.INAPP)
        val proOwned = active.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (proOwned) {
            active.forEach { acknowledgeIfNeeded(it) }
            entitlementStore.setTier(SubscriptionTier.PRO)
            _events.emit(PurchaseResult.Restored)
        } else {
            _events.emit(PurchaseResult.NothingToRestore)
        }
    }

    private suspend fun ownedPurchases(type: String): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
        return billingClient.queryPurchasesAsync(params).purchasesList
    }

    // --- PurchasesUpdatedListener ---

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> scope.launch { _events.emit(PurchaseResult.Cancelled) }
            else -> scope.launch { _events.emit(PurchaseResult.Error("Purchase failed (${result.responseCode})")) }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        scope.launch {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    acknowledgeIfNeeded(purchase)
                    entitlementStore.setTier(SubscriptionTier.PRO)
                    _events.emit(PurchaseResult.Purchased)
                }
                Purchase.PurchaseState.PENDING -> _events.emit(PurchaseResult.Pending)
                else -> Unit
            }
        }
    }

    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params)
    }

    private fun ProductDetails.toSubscriptionProduct(): SubscriptionProduct? {
        val period = when (productId) {
            BillingProducts.PRO_MONTHLY -> BillingPeriod.MONTHLY
            BillingProducts.PRO_YEARLY -> BillingPeriod.YEARLY
            BillingProducts.PRO_LIFETIME -> BillingPeriod.LIFETIME
            else -> return null
        }
        val price = when (period) {
            BillingPeriod.LIFETIME -> oneTimePurchaseOfferDetails?.formattedPrice
            else -> subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice
        } ?: return null

        return SubscriptionProduct(
            productId = productId,
            title = title.ifBlank { name },
            formattedPrice = price,
            period = period,
            isBestValue = period == BillingPeriod.YEARLY,
        )
    }
}
