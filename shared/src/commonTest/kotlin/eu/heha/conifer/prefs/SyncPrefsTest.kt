package eu.heha.conifer.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class SyncPrefsTest {

    private fun prefs() = SyncPrefs(InMemoryPreferencesStore())

    @Test
    fun deviceIdIsGeneratedOnceAndStable() = runTest {
        val prefs = prefs()
        val first = kotlin.uuid.Uuid.parse(prefs.deviceId())
        assertEquals(first, kotlin.uuid.Uuid.parse(prefs.deviceId()))
    }

    @Test
    fun appRootFallsBackToTheDefault() = runTest {
        val prefs = prefs()
        assertEquals(SyncPrefs.DEFAULT_APP_ROOT, prefs.appRoot())
        prefs.setAppRoot("MyNotes")
        assertEquals("MyNotes", prefs.appRoot())
    }

    @Test
    fun valuesRoundTrip() = runTest {
        val prefs = prefs()
        assertNull(prefs.rootEtag())
        assertNull(prefs.lastSyncAt())
        assertNull(prefs.lastGcAt())
        assertNull(prefs.serverUrl())

        prefs.setRootEtag("etag-1")
        prefs.setLastSyncAt(Instant.fromEpochMilliseconds(1_752_408_000_000))
        prefs.setLastGcAt(Instant.fromEpochMilliseconds(1_752_494_400_000))
        prefs.setServerUrl("https://cloud.example.org")

        assertEquals("etag-1", prefs.rootEtag())
        assertEquals(Instant.fromEpochMilliseconds(1_752_408_000_000), prefs.lastSyncAt())
        assertEquals(Instant.fromEpochMilliseconds(1_752_494_400_000), prefs.lastGcAt())
        assertEquals("https://cloud.example.org", prefs.serverUrl())
    }

    @Test
    fun clearRootEtagDropsIt() = runTest {
        val prefs = prefs()
        prefs.setRootEtag("etag-1")
        prefs.clearRootEtag()
        assertNull(prefs.rootEtag())
    }
}

private class InMemoryPreferencesStore : DataStore<Preferences> {

    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
