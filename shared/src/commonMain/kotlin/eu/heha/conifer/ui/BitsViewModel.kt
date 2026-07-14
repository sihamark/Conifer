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
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class BitsViewModel(
    private val repository: BitsRepository,
    private val clipboardController: ClipboardController? = null,
    private val permissionHandler: PermissionHandler? = null
) : ViewModel() {

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
                        val date = bit.date.dateTimeInDefaultTz().date
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
            launch {
                permissionHandler?.let { handler ->
                    handler.isPermissionGranted.collect { isGranted ->
                        Napier.e { "notification permission granted: $isGranted" }
                        state = state.copy(
                            permissionRationale = handler.permissionRationale
                                .takeUnless { isGranted }
                        )
                    }
                }
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
            val date = newBitInstant()
            if (edited != null) {
                repository.update(edited.copy(text = newBitText, date = date))
                // The selection was loaded from the edited bit; drop it so it doesn't leak into
                // the next new bit. A manually chosen selection is kept when adding, so several
                // bits can be entered for the same date/time in a row.
                editingBit = null
                selectedDate.update { null }
                selectedTime.update { null }
            } else {
                repository.add(Bit(text = newBitText, date = date))
            }
            state = state.copy(newBitText = "", editingBitId = null)
        }
    }

    /**
     * Combines the (optionally) selected date and time. When neither is selected the exact
     * current instant is used; a selected date keeps the current time-of-day and vice versa.
     */
    private fun newBitInstant(): Instant {
        val date = selectedDate.value
        val time = selectedTime.value
        if (date == null && time == null) return Clock.System.now()
        val current = now()
        return (date ?: current.date)
            .atTime(time ?: current.time)
            .toInstant(TimeZone.currentSystemDefault())
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
        val dateTime = bit.date.dateTimeInDefaultTz()
        selectedDate.update { dateTime.date }
        selectedTime.update { dateTime.time }
        state = state.copy(newBitText = bit.text, editingBitId = bit.id)
    }

    /** Leaves edit mode without saving, clearing the shared input controls. */
    fun cancelEdit() {
        editingBit = null
        selectedDate.update { null }
        selectedTime.update { null }
        state = state.copy(newBitText = "", editingBitId = null)
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
