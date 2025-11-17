package eu.heha.conifer

import androidx.compose.runtime.Composable
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.Napier

object ConiferApp {

    fun initialize(antilog: Antilog) {
        Napier.base(antilog)
    }

    @Composable
    fun AppContent() {
        App()
    }
}