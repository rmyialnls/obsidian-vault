package com.tracksnatcher.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tracksnatcher.app.people.NearbyUser
import com.tracksnatcher.app.people.PeopleRepository
import com.tracksnatcher.app.people.UserPrefs
import com.tracksnatcher.app.people.UserPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NearbyViewModel @Inject constructor(
    peopleRepository: PeopleRepository,
    private val userPrefsStore: UserPrefsStore,
) : ViewModel() {

    val nearby: StateFlow<List<NearbyUser>> = peopleRepository.observeNearby()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prefs: StateFlow<UserPrefs> = userPrefsStore.prefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPrefs())

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch { userPrefsStore.setHideSnatches(hidden) }
    }
}
