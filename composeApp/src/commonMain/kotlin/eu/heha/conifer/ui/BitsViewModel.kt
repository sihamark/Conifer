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

class BitsViewModel(
    private val permissionHandler: ConiferApp.PermissionHandler? = null
) : ViewModel() {

    var state by mutableStateOf(BitsPaneState())
        private set

    init {
        viewModelScope.launch {
            launch {
                repository.getBits().collect {
                    Napier.d { "has found ${it.size} bits" }
                    state = state.copy(bits = it)
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

}
