package eu.heha.conifer

import eu.heha.conifer.log.UncaughtError

/**
 * The JVM's own last-resort hook, which is more than its name suggests: besides an exception off the
 * end of a thread, kotlinx.coroutines routes an unhandled coroutine failure to the failing thread's
 * uncaught handler too, so a crashed `launch` in the sync stack lands here as well.
 *
 * Not covered: a throwable the AWT event pump catches on the event dispatch thread, which AWT prints
 * itself and only ever offers to the legacy `sun.awt.exception.handler` system property - a
 * reflectively instantiated class, and so one more thing for the release build's ProGuard rules to
 * keep alive. Compose's own composition failures propagate out of `application {}` on the main
 * thread and do reach this handler.
 */
object JvmUncaughtErrorInitializer : UncaughtErrorInitializer {
    override fun installHandler(report: (UncaughtError) -> Unit) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            report(UncaughtError(origin = thread.name, throwable = throwable))
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                // What the JVM would have printed on its own. Deliberately not delegated to the
                // thread group, whose uncaughtException asks for the default handler - this one -
                // and would loop forever.
                System.err.print("Exception in thread \"${thread.name}\" ")
                throwable.printStackTrace()
            }
        }
    }
}
