package com.tracksnatcher.app.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tracksnatcher.app.billing.PurchaseResult
import com.tracksnatcher.app.billing.SubscriptionManager
import com.tracksnatcher.app.billing.SubscriptionProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val subscriptionManager: SubscriptionManager,
) : ViewModel() {

    val products: StateFlow<List<SubscriptionProduct>> = subscriptionManager.products
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Purchase/restore outcomes for the screen to surface (snackbar, auto-close on success). */
    val events: Flow<PurchaseResult> = subscriptionManager.events

    init {
        subscriptionManager.start()
    }

    fun purchase(activity: Activity, productId: String) {
        viewModelScope.launch { subscriptionManager.purchase(activity, productId) }
    }

    fun restore() {
        viewModelScope.launch { subscriptionManager.restorePurchases() }
    }
}
