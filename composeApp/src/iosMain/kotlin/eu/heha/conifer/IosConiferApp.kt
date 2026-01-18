package eu.heha.conifer

import io.github.aakira.napier.DebugAntilog

@Suppress("unused")
object IosConiferApp {

    fun initialize() {
        ConiferApp.initialize(DebugAntilog())
    }
}