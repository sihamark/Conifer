package eu.heha.conifer

import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * A tab has no shutdown, but it does have `pagehide` - fired when the page is navigated away from,
 * reloaded or closed, and the last event a browser reliably delivers. `pageshow` is its other half:
 * a page restored from the back/forward cache carries on in the same run.
 *
 * So here too the goodbye means "the run was put away properly", and a log without it belongs to a
 * tab the browser discarded for memory, a renderer that crashed, or a machine that went down -
 * exactly the endings that leave nothing else behind.
 */
object WasmAppPresenceInitializer : AppPresenceInitializer {
    override fun installHandler(onPutAway: () -> Unit, onBroughtBack: () -> Unit) {
        var hasBeenPutAway = false
        window.addEventListener("pagehide", { _: Event ->
            hasBeenPutAway = true
            onPutAway()
        })
        // `pageshow` also fires on the ordinary first load, where there is nothing to bring back and
        // a "reopened" line above the log's own first line would read oddly.
        window.addEventListener("pageshow", { _: Event -> if (hasBeenPutAway) onBroughtBack() })
    }
}
