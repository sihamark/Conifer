package eu.heha.conifer.prefs

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraftPrefsTest {

    private val store = InMemoryPreferencesStore()
    private val prefs = DraftPrefs(store)

    @Test
    fun thereIsNoDraftUntilOneIsSaved() = runTest {
        assertNull(prefs.draft())
    }

    @Test
    fun aDraftIsRestoredWholeIncludingTheEditItBelongsTo() = runTest {
        val draft = ComposerDraft(
            text = "half a thought",
            editingBitId = "bit-1",
            composerDate = LocalDate(2026, 8, 6),
            composerTime = LocalTime(14, 30)
        )
        prefs.save(draft)
        assertEquals(draft, prefs.draft())
    }

    @Test
    fun aDraftWithoutADateOrTimeRestoresWithoutOne() = runTest {
        val draft = ComposerDraft(text = "whenever")
        prefs.save(draft)
        assertEquals(draft, prefs.draft())
    }

    @Test
    fun savingNullClearsTheStoredDraft() = runTest {
        prefs.save(ComposerDraft(text = "written and added"))
        prefs.save(null)
        assertNull(prefs.draft())
    }

    @Test
    fun blankTextIsNoDraftAndOverwritesTheStoredOne() = runTest {
        prefs.save(ComposerDraft(text = "typed"))
        prefs.save(ComposerDraft(text = "   "))
        assertNull(prefs.draft())
    }

    @Test
    fun savingADraftDropsWhatThePreviousOneHeld() = runTest {
        prefs.save(
            ComposerDraft(
                text = "an edit",
                editingBitId = "bit-1",
                composerDate = LocalDate(2026, 8, 6),
                composerTime = LocalTime(14, 30)
            )
        )
        val plain = ComposerDraft(text = "a new bit")
        prefs.save(plain)
        assertEquals(plain, prefs.draft())
    }

    @Test
    fun theDateAndTimeAreStoredAsIso() = runTest {
        prefs.save(
            ComposerDraft(
                text = "read by a machine",
                composerDate = LocalDate(2026, 8, 6),
                composerTime = LocalTime(14, 30)
            )
        )
        val stored = store.data.first()
        assertEquals("2026-08-06", stored[stringPreferencesKey("composerDate")])
        // Seconds and all: the ISO format writes them out even at zero, where `toString()` would
        // have left them off. Either spelling parses back, so this only pins what is written.
        assertEquals("14:30:00", stored[stringPreferencesKey("composerTime")])
    }

    @Test
    fun aTimeWrittenWithoutSecondsStillParses() = runTest {
        prefs.save(ComposerDraft(text = "no seconds"))
        store.edit { it[stringPreferencesKey("composerTime")] = "14:30" }

        assertEquals(LocalTime(14, 30), prefs.draft()?.composerTime)
    }

    /**
     * The time of an edited bit is its own, down to the millisecond `BitsRepository.add` adds to
     * keep same-minute bits in order — so storing it must not stop at the minute.
     */
    @Test
    fun aTimeKeepsItsSecondsAndMilliseconds() = runTest {
        val time = LocalTime(14, 30, 12, 345_000_000)
        prefs.save(ComposerDraft(text = "to the millisecond", composerTime = time))

        assertEquals(
            "14:30:12.345",
            store.data.first()[stringPreferencesKey("composerTime")]
        )
        assertEquals(time, prefs.draft()?.composerTime)
    }

    @Test
    fun clearingAnEmptyStoreWritesNothing() = runTest {
        prefs.save(null)
        assertEquals(0, store.writeCount)

        prefs.save(ComposerDraft(text = "typed"))
        prefs.save(null)
        assertEquals(2, store.writeCount)
    }

    @Test
    fun anUnparseableDateOrTimeLeavesTheTextRestorable() = runTest {
        prefs.save(ComposerDraft(text = "still worth keeping"))
        store.edit {
            it[stringPreferencesKey("composerDate")] = "not a date"
            it[stringPreferencesKey("composerTime")] = "not a time"
        }
        assertEquals(ComposerDraft(text = "still worth keeping"), prefs.draft())
    }
}
