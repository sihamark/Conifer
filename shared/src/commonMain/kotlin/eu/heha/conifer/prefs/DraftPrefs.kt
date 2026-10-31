package eu.heha.conifer.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * What the composer was holding when the app was last put away, so that text typed but not yet
 * added survives the app being recycled in the background, a desktop window being closed or a
 * browser tab being reloaded.
 *
 * Not the same question as sync: this is one device's unfinished sentence, which no other device
 * has any business seeing, so it stays out of [SyncPrefs] and out of the database — a draft is
 * not a bit until the user says it is.
 */
class DraftPrefs(private val store: DataStore<Preferences>) {

    /** The stored draft, or null when there is none (or nothing but blank text) to restore. */
    suspend fun draft(): ComposerDraft? {
        val preferences = store.data.first()
        val text = preferences[TEXT] ?: return null
        val setAsideText = preferences[SET_ASIDE_TEXT]
        if (text.isBlank() && setAsideText.isNullOrBlank()) return null
        // A stored date or time that cannot be parsed is treated as absent rather than fatal: the
        // draft's text is the part worth saving, and the clock can stand in for the rest.
        return ComposerDraft(
            text = text,
            editingBitId = preferences[EDITING_BIT_ID],
            composerDate = preferences[COMPOSER_DATE]?.toDateOrNull(),
            composerTime = preferences[COMPOSER_TIME]?.toTimeOrNull(),
            setAsideText = setAsideText,
            setAsideDate = preferences[SET_ASIDE_DATE]?.toDateOrNull(),
            setAsideTime = preferences[SET_ASIDE_TIME]?.toTimeOrNull()
        )
    }

    /**
     * Writes [draft], or clears the store when it is null — nothing left to restore should leave
     * nothing behind, so that the next start does not offer a draft the user has already added.
     *
     * Clearing what is already empty writes nothing: an app started and closed without a word typed
     * into it should not touch the disk on the way past.
     */
    suspend fun save(draft: ComposerDraft?) {
        val stored = draft?.takeIf { it.hasText }
        if (stored == null && store.data.first().asMap().isEmpty()) return
        store.edit { preferences ->
            preferences.clear()
            if (stored == null) return@edit
            preferences[TEXT] = stored.text
            stored.editingBitId?.let { preferences[EDITING_BIT_ID] = it }
            // ISO, spelled out rather than left to toString(), as everything stored is (see
            // DateTimeFormats and the database converters): the reader's own spelling belongs on
            // screen and nowhere else, and what a default rendering does is not this file's to
            // assume.
            //
            // The formats rather than `printIso()`: that one is for showing a time and stops at the
            // minute, while a time restored into an edit is the edited bit's own, down to the
            // millisecond BitsRepository.add uses to keep same-minute bits in order.
            stored.composerDate?.let {
                preferences[COMPOSER_DATE] = LocalDate.Formats.ISO.format(it)
            }
            stored.composerTime?.let {
                preferences[COMPOSER_TIME] = LocalTime.Formats.ISO.format(it)
            }
            stored.setAsideText?.let { preferences[SET_ASIDE_TEXT] = it }
            stored.setAsideDate?.let {
                preferences[SET_ASIDE_DATE] = LocalDate.Formats.ISO.format(it)
            }
            stored.setAsideTime?.let {
                preferences[SET_ASIDE_TIME] = LocalTime.Formats.ISO.format(it)
            }
        }
    }

    companion object {
        /** DataStore requires the file name to end in `.preferences_pb`. */
        const val STORE_FILE_NAME = "draft.preferences_pb"

        private val TEXT = stringPreferencesKey("text")
        private val EDITING_BIT_ID = stringPreferencesKey("editingBitId")
        private val COMPOSER_DATE = stringPreferencesKey("composerDate")
        private val COMPOSER_TIME = stringPreferencesKey("composerTime")
        private val SET_ASIDE_TEXT = stringPreferencesKey("setAsideText")
        private val SET_ASIDE_DATE = stringPreferencesKey("setAsideDate")
        private val SET_ASIDE_TIME = stringPreferencesKey("setAsideTime")
    }
}

private fun String.toDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this, LocalDate.Formats.ISO) }.getOrNull()

private fun String.toTimeOrNull(): LocalTime? =
    runCatching { LocalTime.parse(this, LocalTime.Formats.ISO) }.getOrNull()

/**
 * An unfinished composer, stored whole rather than as text alone: the text field is shared between
 * writing a new bit and editing an existing one ([editingBitId] saying which), and the date and
 * time are what the bit would be stamped with. Restoring the text on its own would quietly turn an
 * interrupted edit into a new bit, dated now instead of whenever the user had chosen.
 *
 * During an edit, the `setAside*` fields hold the new bit the edit took the composer from, which
 * comes back when the edit ends.
 */
data class ComposerDraft(
    val text: String,
    val editingBitId: String? = null,
    val composerDate: LocalDate? = null,
    val composerTime: LocalTime? = null,
    val setAsideText: String? = null,
    val setAsideDate: LocalDate? = null,
    val setAsideTime: LocalTime? = null
) {
    /** Whether either text is worth storing; dates and times alone are not a draft. */
    val hasText: Boolean get() = text.isNotBlank() || !setAsideText.isNullOrBlank()
}
