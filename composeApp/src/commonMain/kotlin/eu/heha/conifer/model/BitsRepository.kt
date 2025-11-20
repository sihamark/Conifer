package eu.heha.conifer.model

import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class BitsRepository {
    private val _bits: MutableStateFlow<List<Bit>> = MutableStateFlow(emptyList())
    val bits = _bits.asStateFlow()

    suspend fun add(bit: Bit) = withContext(Default) {
        Napier.d { "add new bit $bit" }
        _bits.update { it + bit }
    }
}