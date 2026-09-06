package com.tracksnatcher.app.people

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore("user_prefs")

/** Persists the visibility flags. Defaults follow the Napster "visible unless you hide" model. */
@Singleton
class UserPrefsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.userPrefsDataStore

    val prefs: Flow<UserPrefs> = dataStore.data.map { p ->
        UserPrefs(
            visibleNearby = p[KEY_VISIBLE_NEARBY] ?: true,
            visibleInSession = p[KEY_VISIBLE_SESSION] ?: true,
            hideSnatches = p[KEY_HIDE_SNATCHES] ?: false,
            showSpotifyProfile = p[KEY_SHOW_SPOTIFY] ?: false,
        )
    }

    suspend fun setHideSnatches(hidden: Boolean) = dataStore.edit { it[KEY_HIDE_SNATCHES] = hidden }
    suspend fun setVisibleNearby(visible: Boolean) = dataStore.edit { it[KEY_VISIBLE_NEARBY] = visible }
    suspend fun setVisibleInSession(visible: Boolean) = dataStore.edit { it[KEY_VISIBLE_SESSION] = visible }
    suspend fun setShowSpotifyProfile(show: Boolean) = dataStore.edit { it[KEY_SHOW_SPOTIFY] = show }

    private companion object {
        val KEY_VISIBLE_NEARBY = booleanPreferencesKey("visible_nearby")
        val KEY_VISIBLE_SESSION = booleanPreferencesKey("visible_session")
        val KEY_HIDE_SNATCHES = booleanPreferencesKey("hide_snatches")
        val KEY_SHOW_SPOTIFY = booleanPreferencesKey("show_spotify")
    }
}
