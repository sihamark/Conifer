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
        if (text.isBlank()) return null
        return ComposerDraft(
            text = text,
            editingBitId = preferences[EDITING_BIT_ID],
            // A stored date or time that cannot be parsed is treated as absent rather than fatal:
            // the draft's text is the part worth saving, and the clock can stand in for the rest.
            composerDate = preferences[COMPOSER_DATE]?.let { value ->
                runCatching { LocalDate.parse(value, LocalDate.Formats.ISO) }.getOrNull()
            },
            composerTime = preferences[COMPOSER_TIME]?.let { value ->
                runCatching { LocalTime.parse(value, LocalTime.Formats.ISO) }.getOrNull()
            }
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
        val text = draft?.text?.takeUnless { it.isBlank() }
        if (text == null && store.data.first().asMap().isEmpty()) return
        store.edit { preferences ->
            preferences.clear()
            if (draft == null || text == null) return@edit
            preferences[TEXT] = text
            draft.editingBitId?.let { preferences[EDITING_BIT_ID] = it }
            // ISO, spelled out rather than left to toString(), as everything stored is (see
            // DateTimeFormats and the database converters): the reader's own spelling belongs on
            // screen and nowhere else, and what a default rendering does is not this file's to
            // assume.
            //
            // The formats rather than `printIso()`: that one is for showing a time and stops at the
            // minute, while a time restored into an edit is the edited bit's own, down to the
            // millisecond BitsRepository.add uses to keep same-minute bits in order.
            draft.composerDate?.let {
                preferences[COMPOSER_DATE] = LocalDate.Formats.ISO.format(it)
            }
            draft.composerTime?.let {
                preferences[COMPOSER_TIME] = LocalTime.Formats.ISO.format(it)
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
    }
}

/**
 * An unfinished composer, stored whole rather than as text alone: the text field is shared between
 * writing a new bit and editing an existing one ([editingBitId] saying which), and the date and
 * time are what the bit would be stamped with. Restoring the text on its own would quietly turn an
 * interrupted edit into a new bit, dated now instead of whenever the user had chosen.
 */
data class ComposerDraft(
    val text: String,
    val editingBitId: String? = null,
    val composerDate: LocalDate? = null,
    val composerTime: LocalTime? = null
)
