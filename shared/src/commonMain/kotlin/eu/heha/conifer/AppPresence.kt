package eu.heha.conifer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app is in front of somebody, as the platform reports it through
 * [AppPresenceInitializer].
 *
 * Off screen is not stopped: a phone app that has been put away keeps its process and its coroutines
 * for as long as the system lets it, so anything that would only be worth doing for a person who is
 * looking has to ask - which is what this is for (`runSyncTriggers`).
 *
 * Starts on screen. An app that has just started is being looked at, and the one platform that never
 * says otherwise - a desktop process, which is only ever put away by shutting down - is right about
 * itself the whole way through.
 */
class AppPresence {
    private val onScreen = MutableStateFlow(true)

    val isOnScreen: StateFlow<Boolean> = onScreen.asStateFlow()

    fun onPutAway() {
        onScreen.value = false
    }

    fun onBroughtBack() {
        onScreen.value = true
    }
}
