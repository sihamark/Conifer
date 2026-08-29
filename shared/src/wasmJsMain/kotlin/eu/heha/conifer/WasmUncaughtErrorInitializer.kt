package eu.heha.conifer

import eu.heha.conifer.log.UncaughtError
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * The browser's two ways of saying that something got away: `error` for anything thrown out of a
 * task, and `unhandledrejection` for a promise nobody caught - which is where a failed coroutine
 * ends up.
 *
 * Neither hands over a Kotlin [Throwable]; what arrives is a browser event, so the error is reported
 * in words ([UncaughtError.message]) exactly as that type allows for. Nothing is cancelled or
 * consumed here: the browser still prints its own message to the console afterwards, which is the
 * one place a web developer looks first.
 */
object WasmUncaughtErrorInitializer : UncaughtErrorInitializer {
    override fun installHandler(report: (UncaughtError) -> Unit) {
        window.addEventListener("error", { event: Event ->
            report(UncaughtError(origin = "window", throwable = null, message = describe(event)))
        })
        window.addEventListener("unhandledrejection", { event: Event ->
            report(UncaughtError(origin = "promise", throwable = null, message = describe(event)))
        })
    }

    /**
     * What the event says about itself. Read through JavaScript rather than through typed properties
     * because the two events carry the same information under different names (`message` on one, the
     * rejection's `reason` on the other), and neither is worth a cast that can fail on a browser
     * that spells it differently.
     */
    private fun describe(event: Event): String = describeEvent(event).toString()
}

@Suppress("unused")
@JsFun(
    """(event) => {
        const reason = event.reason;
        const message = event.message || (reason && (reason.message || reason)) || 'unknown error';
        const at = event.filename ? ` (${'$'}{event.filename}:${'$'}{event.lineno})` : '';
        const stack = (event.error && event.error.stack) || (reason && reason.stack) || '';
        return String(message) + at + (stack ? '\n' + stack : '');
    }"""
)
private external fun describeEvent(event: Event): JsString
