package com.tracksnatcher.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tracksnatcher.app.billing.EntitlementStore
import com.tracksnatcher.app.billing.Entitlements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val entitlementStore: EntitlementStore,
) : ViewModel() {

    val entitlements: StateFlow<Entitlements> = entitlementStore.entitlements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Entitlements())

    fun setAutoVibeMatch(enabled: Boolean) {
        viewModelScope.launch { entitlementStore.setAutoVibeMatch(enabled) }
    }
}
