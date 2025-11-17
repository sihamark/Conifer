package eu.heha.conifer

import io.github.aakira.napier.DebugAntilog

object IosConiferApp {

    fun initialize() {
        ConiferApp.initialize(DebugAntilog())
    }
}