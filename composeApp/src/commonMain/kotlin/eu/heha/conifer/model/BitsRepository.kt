package eu.heha.conifer.model

import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class BitsRepository {
    private var bits: MutableStateFlow<List<Bit>> = MutableStateFlow(emptyList())

    suspend fun add(bit: Bit) = withContext(Default) {
        bits.update { it + bit }
    }
}