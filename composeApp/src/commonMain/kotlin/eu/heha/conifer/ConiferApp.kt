package eu.heha.conifer

import androidx.compose.runtime.Composable
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.ui.BitsRoute
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.Napier

object ConiferApp {

    val repository by lazy { BitsRepository() }

    fun initialize(antilog: Antilog) {
        Napier.base(antilog)
    }

    @Composable
    fun AppContent() {
        BitsRoute()
    }
}