package eu.heha.conifer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.heha.conifer.ConiferApp.repository
import eu.heha.conifer.model.Bit
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch

class BitsPaneViewModel : ViewModel() {

    var state by mutableStateOf(BitsPaneState())
        private set

    init {
        viewModelScope.launch {
            repository.bits.collect {
                Napier.d { "has found ${it.size} bits" }
                state = state.copy(bits = it)
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

}
