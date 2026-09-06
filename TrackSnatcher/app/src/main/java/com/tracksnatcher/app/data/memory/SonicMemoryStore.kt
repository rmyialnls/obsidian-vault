package com.tracksnatcher.app.data.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tracksnatcher.app.domain.model.SonicMemory
import com.tracksnatcher.app.domain.repository.SonicMemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.memoryDataStore: DataStore<Preferences> by preferencesDataStore("sonic_memories")

/**
 * Local, append-only store of [SonicMemory] entries serialized as a JSON array in DataStore.
 * Fine for the expected volume (a handful of snatches a day); migrate to Room if it grows.
 */
@Singleton
class SonicMemoryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) : SonicMemoryRepository {

    private val dataStore = context.memoryDataStore

    override fun observeMemories(): Flow<List<SonicMemory>> = dataStore.data.map { prefs ->
        prefs[KEY_MEMORIES]?.let { decode(it) }.orEmpty().sortedByDescending { it.timestampMs }
    }

    override suspend fun save(memory: SonicMemory) {
        dataStore.edit { prefs ->
            val existing = prefs[KEY_MEMORIES]?.let { decode(it) }.orEmpty()
            prefs[KEY_MEMORIES] = json.encodeToString(existing + memory)
        }
    }

    private fun decode(raw: String): List<SonicMemory> =
        runCatching { json.decodeFromString<List<SonicMemory>>(raw) }.getOrDefault(emptyList())

    private companion object {
        val KEY_MEMORIES = stringPreferencesKey("memories_json")
    }
}
