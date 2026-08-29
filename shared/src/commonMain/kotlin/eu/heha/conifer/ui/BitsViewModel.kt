package eu.heha.conifer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.heha.conifer.ClipboardController
import eu.heha.conifer.DateTimeFormats
import eu.heha.conifer.PermissionHandler
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.prefs.ComposerDraft
import eu.heha.conifer.prefs.DraftPrefs
import eu.heha.conifer.ui.bits.BitsPaneState
import eu.heha.conifer.ui.bits.dateShiftedBy
import eu.heha.conifer.ui.bits.nearestDateWithBits
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlin.time.Duration.Companion.milliseconds

class BitsViewModel(
    private val repository: BitsRepository,
    private val dateTimeFormats: DateTimeFormats,
    private val draftPrefs: DraftPrefs,
    private val clipboardController: ClipboardController? = null
) : ViewModel() {

    /** Collection of the currently bound handler, see [bindPermissionHandler]. */
    private var permissionJob: Job? = null

    // The bit currently being edited inline, if any. Retained so its id/createdAt survive a save.
    private var editingBit: Bit? = null

    /**
     * What the composer was set to before the current edit started, put back when the edit ends.
     *
     * Editing loads the edited bit's date and time into the composer, so without remembering them
     * a selection the user had made for the bits they are writing would be lost to a passing edit.
     */
    private var composerSelectionBeforeEdit: ComposerSelection? = null

    var state by mutableStateOf(
        BitsPaneState(
            isCopyPossible = clipboardController != null
        )
    )
        private set

    init {
        viewModelScope.launch {
            launch {
                repository.getBits().collect { bits ->
                    Napier.d { "has found ${bits.size} bits" }
                    var datedBits: List<DatedBits> = listOf()

                    for (bit in bits) {
                        val date = bit.date.date
                        datedBits = if (datedBits.none { it.date == date }) {
                            datedBits + DatedBits(date, listOf(bit))
                        } else {
                            datedBits.map { datedBit ->
                                if (datedBit.date == date) {
                                    datedBit.copy(bits = datedBit.bits + bit)
                                } else {
                                    datedBit
                                }
                            }
                        }
                    }

                    // All days stay in the state; the day filter only decides what the list shows,
                    // so the day chips keep their indicators while filtering.
                    state = state.copy(bitsByDate = datedBits)
                }
            }
            launch { trackCurrentDateTime() }
            // Restoring reads what saving would overwrite, so the two share a coroutine and run in
            // that order — never a save of the empty composer landing on top of the stored draft.
            launch {
                restoreDraft()
                saveDraftAsItChanges()
            }
        }
    }

    /**
     * Puts the stored draft back into the composer, if there is one — see [DraftPrefs].
     *
     * Leaves the composer alone once anything has been typed into it: reading the draft is
     * asynchronous, and the user is faster than a cold DataStore more often than one would think.
     * Losing a draft to that is a great deal better than overwriting the sentence they are in the
     * middle of with one from yesterday.
     */
    private suspend fun restoreDraft() = catchingDraftFailure {
        val draft = draftPrefs.draft() ?: return@catchingDraftFailure
        if (state.newBitText.isNotEmpty() || editingBit != null) {
            Napier.d { "not restoring the draft, the composer is already in use" }
            return@catchingDraftFailure
        }
        // An edit whose bit is gone — deleted here or on another device while the app was away —
        // becomes a new bit, keeping the text. The alternative is throwing away what was written
        // because of something the user did to a different bit entirely.
        val edited = draft.editingBitId?.let { repository.getBit(it) }
        editingBit = edited
        // What the edit would be restored *to* was not stored: it is the composer the user had
        // before an edit they may well have forgotten starting. Saving the restored edit therefore
        // hands the composer back to the clock, as an edit started and finished right now would.
        composerSelectionBeforeEdit = ComposerSelection().takeIf { edited != null }
        Napier.d { "restored draft, editing ${edited?.id}" }
        state = state.copy(
            newBitText = draft.text,
            editingBitId = edited?.id,
            composerDate = draft.composerDate,
            composerTime = draft.composerTime
        )
    }

    /**
     * Mirrors the composer into [DraftPrefs] from here on, [DRAFT_SAVE_DELAY] after it last
     * changed — a keystroke is not worth a write of its own, and a pause in typing is exactly when
     * a background app is likely to be recycled. [collectLatest] is the debounce: every further
     * change cancels the wait, so only the composer that stood still gets written.
     *
     * No `distinctUntilChanged`: [snapshotFlow] is already one, emitting only when what its block
     * returns is unequal to what it last returned.
     */
    private suspend fun saveDraftAsItChanges() {
        snapshotFlow { state.toDraft() }
            .collectLatest { draft ->
                delay(DRAFT_SAVE_DELAY)
                catchingDraftFailure { draftPrefs.save(draft) }
            }
    }

    /**
     * Runs [block] — reading or writing the draft — and lets it fail no further than a log line.
     *
     * The draft is stored by the same machinery as everything else, and that machinery can fail on
     * its own terms: a preferences file that cannot be read, a browser that will not hand out
     * `localStorage`, a disk with nothing left on it. The coroutine doing this work is a sibling of
     * the ones feeding the bits list and the clock, so an exception let through here would cancel
     * all three and leave a frozen screen behind. Whatever the draft is worth, it is not worth
     * that: a draft that cannot be read is one the user has lost, and no more than that.
     */
    private suspend fun catchingDraftFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            // Not a failure: the debounce cancels the save in flight on every further keystroke,
            // and swallowing this would leave the coroutine that was cancelled running.
            throw e
        } catch (e: Exception) {
            Napier.e(e) { "the composer's draft could not be read or written" }
        }
    }

    /**
     * Binds the permission prompt to [handler], replacing whatever was bound before.
     *
     * Called from the screen rather than taken as a constructor parameter: asking for a permission
     * needs whatever the platform's current screen is (an Activity on Android), so the handler is a
     * new instance after every recreation while this ViewModel is not. A handler kept from
     * construction would go stale on the first rotation — the state would follow one nobody checks
     * any more, leaving the prompt up however often the permission is granted — and would hold the
     * screen it belongs to alive along with it.
     */
    fun bindPermissionHandler(handler: PermissionHandler?) {
        permissionJob?.cancel()
        if (handler == null) {
            state = state.copy(permissionRationale = null)
            return
        }
        permissionJob = viewModelScope.launch {
            handler.isPermissionGranted.collect { isGranted ->
                Napier.d { "notification permission granted: $isGranted" }
                state = state.copy(
                    permissionRationale = handler.permissionRationale.takeUnless { isGranted }
                )
            }
        }
    }

    /**
     * Keeps [BitsPaneState.today] and [BitsPaneState.currentTime] in sync with the wall clock so
     * the time display progresses and the date rolls over at midnight. Re-aligns to the start of
     * each minute (the displayed resolution) so updates land right after the minute changes.
     */
    private suspend fun trackCurrentDateTime() {
        while (true) {
            yield()
            val current = now()
            state = state.copy(
                today = current.date,
                currentTime = current.time
            )
            val millisToNextMinute = (60 - current.second) * 1_000L -
                    current.nanosecond / 1_000_000
            delay(millisToNextMinute.milliseconds)
        }
    }

    /**
     * Confirms the text field: updates the bit currently being edited, or adds a new one when not
     * editing. Blank text is ignored.
     */
    fun onClickAdd() {
        val newBitText = state.newBitText
        if (newBitText.isBlank()) return
        val edited = editingBit
        viewModelScope.launch {
            val date = newBitDateTime()
            val submittedBitId: String
            if (edited != null) {
                repository.update(edited.copy(text = newBitText, date = date))
                submittedBitId = edited.id
                editingBit = null
            } else {
                val newBit = Bit(text = newBitText, date = date)
                repository.add(newBit)
                submittedBitId = newBit.id
            }
            // Cleared here rather than left to the debounced save below: the text is a bit now, and
            // being killed in the half second in between would otherwise bring it back as a draft
            // of a bit that already exists.
            catchingDraftFailure { draftPrefs.save(null) }
            // A selection the user made by hand for new bits is kept, so several bits can be
            // entered for the same date and time in a row. An edit's selection was loaded from the
            // bit, so it gives way to whatever the composer was set to before the edit.
            val composerSelection = if (edited != null) {
                composerSelectionBeforeEdit ?: ComposerSelection()
            } else {
                ComposerSelection(state.composerDate, state.composerTime)
            }
            composerSelectionBeforeEdit = null
            state = state.copy(
                newBitText = "",
                editingBitId = null,
                composerDate = composerSelection.date,
                composerTime = composerSelection.time,
                // A filtered list follows the bit that was just written when it lands on another
                // day, so it stays in view (and can be scrolled to) instead of disappearing behind
                // the filter. Filtered to the day it landed on anyway — the common case — this
                // changes nothing, which is the point: the list keeps its scroll position.
                filterDate = if (state.filterDate != null) date.date else null,
                scrollToBitId = submittedBitId
            )
        }
    }

    /**
     * Combines the composer's (optionally) chosen date and time. When neither is chosen the current
     * date and time are used; a chosen date keeps the current time-of-day and vice versa.
     */
    private fun newBitDateTime(): LocalDateTime {
        val current = now()
        return (state.composerDate ?: current.date)
            .atTime(state.composerTime ?: current.time)
    }

    fun onNewBitTextChange(newBit: String) {
        state = state.copy(newBitText = newBit)
    }

    /**
     * Starts editing [bit] by loading its text into the shared new-bit text field and its date and
     * time into the date/time selector, so the existing input controls are reused for editing.
     *
     * The day filter is deliberately left alone: it says which day the user chose to look at, and
     * editing one of the bits in front of them is no such choice. Filtering along would rebuild the
     * list under the bit being edited — and rebuild it again on saving — which is exactly what the
     * scroll position cannot survive.
     */
    fun startEditing(bit: Bit) {
        // Switching straight from one bit to another keeps what was remembered for the first, so
        // the date the earlier edit loaded doesn't become what the composer is restored to.
        if (editingBit == null) {
            composerSelectionBeforeEdit =
                ComposerSelection(state.composerDate, state.composerTime)
        }
        editingBit = bit
        state = state.copy(
            newBitText = bit.text,
            editingBitId = bit.id,
            composerDate = bit.date.date,
            composerTime = bit.date.time
        )
    }

    /** Leaves edit mode without saving, putting the composer back the way the edit found it. */
    fun cancelEdit() {
        editingBit = null
        // As in onClickAdd: the text was dropped on purpose, so it should not outlive the decision.
        viewModelScope.launch { catchingDraftFailure { draftPrefs.save(null) } }
        val restored = composerSelectionBeforeEdit ?: ComposerSelection()
        composerSelectionBeforeEdit = null
        state = state.copy(
            newBitText = "",
            editingBitId = null,
            composerDate = restored.date,
            composerTime = restored.time
        )
    }

    /** Called by the UI once it has scrolled to (or verified visibility of) the submitted bit. */
    fun onScrolledToBit() {
        state = state.copy(scrollToBitId = null)
    }

    fun deleteBit(bit: Bit) {
        viewModelScope.launch {
            repository.delete(bit)
        }
    }

    /**
     * Picks the day, which the day strip and the sidebar both advertise as filtering the list *and*
     * dating what is written next — so it sets both. Picking the day that is already selected
     * deselects it, back to all days and the current date.
     */
    fun selectDate(newDate: LocalDate) {
        // Keyed on the filter, because that is the selection both day lists highlight.
        val isDeselecting = state.filterDate == newDate
        state = state.copy(
            filterDate = if (isDeselecting) null else newDate,
            composerDate = if (isDeselecting) null else newDate
        )
    }

    /**
     * Steps the day by [days] — the keyboard's way in, where the day lists have [selectDate].
     *
     * It counts from the day being written to ([BitsPaneState.effectiveDate]) rather than from the
     * filter, so the first press from an unfiltered list goes straight to yesterday instead of
     * spending itself landing on today. It also keeps the list where it is unless the list was
     * already showing a single day: stepping the date while writing is a thing one does mid-sentence,
     * and having the bits jump about underneath would be no help. While a bit is being edited, that
     * makes this the date counterpart of the time nudge — it re-dates the bit in hand.
     *
     * Clamped to the days both day lists offer, so the selection is always one of them can show.
     */
    fun shiftDate(days: Int) = moveToDate(state.dateShiftedBy(days))

    /**
     * As [shiftDate], but skips to the nearest day in that direction that has bits — the whole
     * month is mostly empty days, and stepping through them one press at a time to reach the
     * writing is a poor way to spend a keyboard. Does nothing when there is no such day.
     */
    fun skipToDateWithBits(days: Int) {
        moveToDate(state.nearestDateWithBits(days) ?: return)
    }

    /** Back to today, on [shiftDate]'s terms. */
    fun selectToday() = moveToDate(state.today)

    private fun moveToDate(date: LocalDate) {
        state = state.copy(
            // Today is left to the clock rather than written down, so that a date stepped back to
            // today still rolls over at midnight like one never picked at all.
            composerDate = date.takeIf { it != state.today },
            filterDate = state.filterDate?.let { date }
        )
    }

    /**
     * Shows every day again — the two-pane sidebar's "All days", which is where that layout puts
     * what the day strip does by tapping the selected day again, so it does the same thing: the
     * date goes back to the current one, a chosen time is kept.
     */
    fun selectAllDays() {
        state = state.copy(filterDate = null, composerDate = null)
    }

    /**
     * Everything the user had pointed the screen at, dropped in one go: all days again, and the
     * clock back in charge of both the date and the time. [selectAllDays] and [resetToNow] each undo
     * half of that, one because the day lists ask for it and one because the chip's reset does; this
     * is the whole of it, and belongs to Esc — the key for being done with whatever you were in.
     */
    fun resetSelection() {
        state = state.copy(filterDate = null, composerDate = null, composerTime = null)
    }

    fun selectTime(newTime: LocalTime) {
        state = state.copy(composerTime = newTime)
    }

    /**
     * Drops the composer's custom date/time so the current date and time are used when a bit is
     * added. The day filter stays as it is: it belongs to the day lists, not to the date chip this
     * sits next to — and a bit written for another day pulls the filter along by itself.
     */
    fun resetToNow() {
        state = state.copy(composerDate = null, composerTime = null)
    }

    fun copyBitsOfDateToClipboard(date: LocalDate) {
        val clipboardController = clipboardController ?: return
        val bits = state.bitsByDate.firstOrNull { it.date == date }?.bits ?: return
        viewModelScope.launch {
            // The one export a person reads rather than a machine, so it is spelled their way; the
            // sync files stay ISO (see ReadableRenderer).
            val header = "Bits of ${dateTimeFormats.dateWithWeekday(date)}"
            buildString {
                appendLine(header)
                bits.reversed().forEach { bit ->
                    appendLine(bit.text)
                }
            }.let { textToCopy ->
                clipboardController.copyToClipboard(textToCopy)
            }
        }
    }
}

/**
 * How long the composer has to stand still before it is written to [DraftPrefs]. Long enough that
 * typing is not a stream of writes, short enough that a pause counts as saved.
 */
private val DRAFT_SAVE_DELAY = 500.milliseconds

/**
 * The composer as [DraftPrefs] stores it, or null when there is nothing worth storing — blank text
 * is not a draft, and a date or time on its own is a selection the user can see on screen rather
 * than something to be restored days later.
 */
private fun BitsPaneState.toDraft(): ComposerDraft? =
    ComposerDraft(
        text = newBitText,
        editingBitId = editingBitId,
        composerDate = composerDate,
        composerTime = composerTime
    ).takeIf { newBitText.isNotBlank() }

/**
 * What the composer is set to date a bit with — null in either field meaning "whatever the clock
 * says when it is written". Kept together so an edit can put both back at once, see
 * [BitsViewModel.composerSelectionBeforeEdit].
 */
private data class ComposerSelection(
    val date: LocalDate? = null,
    val time: LocalTime? = null
)

/** The bits of one day, as the list and both day pickers consume them. */
data class DatedBits(
    val date: LocalDate,
    val bits: List<Bit>
) {
    /**
     * Dots shown on the day chip: one for any bit, two when both the morning (before 12:00) and
     * the afternoon have one, three when on top of that the day holds more than three bits.
     */
    val dots: Int = run {
        val hasMorningBit = bits.any { it.date.hour < 12 }
        val hasAfternoonBit = bits.any { it.date.hour >= 12 }
        when {
            hasMorningBit && hasAfternoonBit && bits.size > 3 -> 3
            hasMorningBit && hasAfternoonBit -> 2
            else -> 1
        }
    }
}
