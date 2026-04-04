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
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class BitsViewModel(
    private val permissionHandler: ConiferApp.PermissionHandler? = null
) : ViewModel() {

    var state by mutableStateOf(BitsPaneState())
        private set

    init {
        viewModelScope.launch {
            launch {
                repository.getBits().collect { bits ->
                    Napier.d { "has found ${bits.size} bits" }
                    var dates: List<LocalDate> = listOf()
                    var datedBits: List<DatedBits> = listOf()

                    bits.forEach { bit ->
                        val date = bit.date.dateTimeInDefaultTz().date
                        if (!dates.contains(date)) {
                            dates = dates + date
                            datedBits = datedBits + DatedBits(date, listOf(bit))
                        } else {
                            datedBits = datedBits.map { datedBit ->
                                if (datedBit.date == date) {
                                    datedBit.copy(bits = datedBit.bits + bit)
                                } else {
                                    datedBit
                                }
                            }
                        }
                    }

                    state = state.copy(
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
                repository.add(Bit(text = newBitText))
                state = state.copy(newBitText = "")
            }
        }
    }

    fun onNewBitTextChange(newBit: String) {
        state = state.copy(newBitText = newBit)
    }

    fun selectDate(date: LocalDate) {
        state = state.copy(
            selectedDate = date.takeIf { it != state.selectedDate }
        )
    }
}
