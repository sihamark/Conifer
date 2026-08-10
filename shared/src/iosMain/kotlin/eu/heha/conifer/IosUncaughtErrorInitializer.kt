package eu.heha.conifer

import eu.heha.conifer.log.UncaughtError
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

/**
 * Kotlin/Native's unhandled exception hook: every Kotlin exception that reaches the edge of the
 * runtime, on any thread and including an unhandled coroutine failure, passes through here.
 *
 * Only Kotlin exceptions do. A Swift or Objective-C exception on the other side of the interop
 * boundary, and anything that arrives as a signal (a bad access, a watchdog kill), goes straight to
 * iOS' own crash reporting and is never seen here.
 */
@OptIn(ExperimentalNativeApi::class)
object IosUncaughtErrorInitializer : UncaughtErrorInitializer {
    override fun installHandler(report: (UncaughtError) -> Unit) {
        setUnhandledExceptionHook { throwable ->
            report(UncaughtError(origin = "native", throwable = throwable))
            // The hook replaces the runtime's default handling rather than adding to it, so the
            // default has to be asked for by name - otherwise the app would end without the crash
            // report that is the only way a crash on someone else's iPhone ever gets seen.
            terminateWithUnhandledException(throwable)
        }
    }
}
