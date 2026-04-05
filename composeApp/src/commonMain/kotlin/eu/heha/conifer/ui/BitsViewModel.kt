package eu.heha.conifer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.heha.conifer.ConiferApp
import eu.heha.conifer.ConiferApp.repository
import eu.heha.conifer.model.database.Bit
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Clock

class BitsViewModel(
    private val permissionHandler: ConiferApp.PermissionHandler? = null
) : ViewModel() {

    private val clipboardController = ConiferApp.clipboardController

    private val selectedDate = MutableStateFlow<LocalDate?>(null)

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
                    var dates: List<LocalDate> = listOf()
                    var datedBits: List<DatedBits> = listOf()

                    for (bit in bits) {
                        val date = bit.date.dateTimeInDefaultTz().date
                        if (date !in dates) dates = dates + date

                        if (selectedDate != null && date != selectedDate) {
                            // skip bits that don't match the selected date
                            continue
                        }
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

                    state = state.copy(
                        selectedDate = selectedDate,
                        dates = dates,
                        bitsByDate = datedBits
                    )
                }
            }
            launch {
                permissionHandler?.let { handler ->
                    handler.isPermissionGranted.collect { isGranted ->
                        Napier.e { "notification permission granted: $isGranted" }
                        state = state.copy(
                            permissionRationale = handler.permissionRationale.takeUnless { isGranted },
                        )
                    }
                }
            }
        }
    }

    fun onClickAdd() {
        val newBitText = state.newBitText
        if (newBitText.isNotBlank()) {
            viewModelScope.launch {
                val date =
                    selectedDate.value
                        ?.atTime(12, 0)
                        ?.toInstant(TimeZone.currentSystemDefault())
                        ?: Clock.System.now()
                repository.add(
                    Bit(
                        text = newBitText,
                        date = date
                    )
                )
                state = state.copy(newBitText = "")
            }
        }
    }

    fun onNewBitTextChange(newBit: String) {
        state = state.copy(newBitText = newBit)
    }

    fun selectDate(newDate: LocalDate) {
        selectedDate.update { oldDate ->
            // if the same date is selected again, deselect it
            if (oldDate == newDate) null else newDate
        }
    }

    fun copyBitsOfDateToClipboard(date: LocalDate) {
        val clipboardController = clipboardController ?: return
        val bits = state.bitsByDate.firstOrNull { it.date == date }?.bits ?: return
        buildString {
            appendLine("##### Bits of ${date.dayOfWeek}, ${date.day}. ${date.month} ${date.year}:\n")
            bits.reversed().forEach { bit ->
                appendLine("- ${bit.text}\n")
            }
        }.let { textToCopy ->
            clipboardController.copyToClipboard(textToCopy)
        }
    }
}
