package eu.heha.conifer

import eu.heha.conifer.log.UncaughtError

/**
 * Android's own last-resort hook, set from `Application.onCreate` so it covers every thread the app
 * ever starts - including the main thread, where a throw out of a composable ends up, and any
 * coroutine whose failure kotlinx.coroutines passes to the failing thread's uncaught handler.
 *
 * The previous handler is always there and always matters: it is the framework's own
 * `KillApplicationHandler`, the one that writes the crash to logcat, tells the activity manager and
 * ends the process. Reporting instead of it would leave the app half-dead on screen with no crash
 * recorded anywhere, so the error is written down first and handed straight on.
 */
object AndroidUncaughtErrorInitializer : UncaughtErrorInitializer {
    override fun installHandler(report: (UncaughtError) -> Unit) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            report(UncaughtError(origin = thread.name, throwable = throwable))
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                // Not reachable on a real device (the framework installs its own before any app code
                // runs) - and not delegated to the thread group either, whose uncaughtException asks
                // for the default handler, this one, and would loop forever.
                System.err.print("Exception in thread \"${thread.name}\" ")
                throwable.printStackTrace()
            }
        }
    }
}
