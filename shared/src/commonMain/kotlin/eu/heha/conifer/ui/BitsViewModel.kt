package eu.heha.conifer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.heha.conifer.ClipboardController
import eu.heha.conifer.PermissionHandler
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.bits.BitsPaneState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import kotlin.time.Duration.Companion.milliseconds

class BitsViewModel(
    private val repository: BitsRepository,
    private val clipboardController: ClipboardController? = null
) : ViewModel() {

    /** Collection of the currently bound handler, see [bindPermissionHandler]. */
    private var permissionJob: Job? = null

    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    // null means "use the current time when the bit is added"
    private val selectedTime = MutableStateFlow<LocalTime?>(null)

    // The bit currently being edited inline, if any. Retained so its id/createdAt survive a save.
    private var editingBit: Bit? = null

    var state by mutableStateOf(
        BitsPaneState(
            isCopyPossible = clipboardController != null
        )
    )
        private set

    init {
        viewModelScope.launch {
            launch {
                combine(
                    repository.getBits(),
                    selectedDate
                ) { bits, selectedDate ->
                    bits to selectedDate
                }.collect { (bits, selectedDate) ->
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

                    // All days stay in the state; the selected date only filters what the list
                    // shows, so the day chips keep their indicators while filtering.
                    state = state.copy(
                        selectedDate = selectedDate,
                        bitsByDate = datedBits
                    )
                }
            }
            launch {
                selectedTime.collect { time ->
                    state = state.copy(selectedTime = time)
                }
            }
            launch { trackCurrentDateTime() }
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
                // The selection was loaded from the edited bit; drop it so it doesn't leak into
                // the next new bit. A manually chosen selection is kept when adding, so several
                // bits can be entered for the same date/time in a row.
                editingBit = null
                selectedDate.update { null }
                selectedTime.update { null }
            } else {
                val newBit = Bit(text = newBitText, date = date)
                repository.add(newBit)
                submittedBitId = newBit.id
            }
            state = state.copy(
                newBitText = "",
                editingBitId = null,
                scrollToBitId = submittedBitId
            )
        }
    }

    /**
     * Combines the (optionally) selected date and time. When neither is selected the current
     * date and time are used; a selected date keeps the current time-of-day and vice versa.
     */
    private fun newBitDateTime(): LocalDateTime {
        val current = now()
        return (selectedDate.value ?: current.date)
            .atTime(selectedTime.value ?: current.time)
    }

    fun onNewBitTextChange(newBit: String) {
        state = state.copy(newBitText = newBit)
    }

    /**
     * Starts editing [bit] by loading its text into the shared new-bit text field and its date and
     * time into the date/time selector, so the existing input controls are reused for editing.
     */
    fun startEditing(bit: Bit) {
        editingBit = bit
        selectedDate.update { bit.date.date }
        selectedTime.update { bit.date.time }
        state = state.copy(newBitText = bit.text, editingBitId = bit.id)
    }

    /** Leaves edit mode without saving, clearing the shared input controls. */
    fun cancelEdit() {
        editingBit = null
        selectedDate.update { null }
        selectedTime.update { null }
        state = state.copy(newBitText = "", editingBitId = null)
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

    fun selectDate(newDate: LocalDate) {
        selectedDate.update { oldDate ->
            // if the same date is selected again, deselect it
            if (oldDate == newDate) null else newDate
        }
    }

    /**
     * Lifts the day filter (the two-pane sidebar's "All days") without touching a chosen time, so
     * the next bit keeps that time on the current day.
     */
    fun selectAllDays() {
        selectedDate.update { null }
    }

    fun selectTime(newTime: LocalTime) {
        selectedTime.update { newTime }
    }

    /** Drops any custom date/time so the current date and time are used when a bit is added. */
    fun resetToNow() {
        selectedDate.update { null }
        selectedTime.update { null }
    }

    fun copyBitsOfDateToClipboard(date: LocalDate) {
        val clipboardController = clipboardController ?: return
        val bits = state.bitsByDate.firstOrNull { it.date == date }?.bits ?: return
        viewModelScope.launch {
            val header = "Bits of ${date.dayOfWeek}, ${date.day}.${date.month.number}.${date.year}"
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
