package eu.heha.conifer.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A [DataStore] that keeps the preferences in memory, for the tests of the stores built on it. */
internal class InMemoryPreferencesStore : DataStore<Preferences> {

    private val state = MutableStateFlow(emptyPreferences())

    /** How often something was written, for the tests that are about a write not happening. */
    var writeCount = 0
        private set

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(state.value)
        writeCount++
        state.value = updated
        return updated
    }
}
