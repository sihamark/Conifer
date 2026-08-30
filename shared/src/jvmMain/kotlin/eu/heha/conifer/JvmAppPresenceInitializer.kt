package eu.heha.conifer

/**
 * A desktop run ends when the process does, and the JVM says so through a shutdown hook - which runs
 * on a window being closed, on `exitApplication`, and on the terminal's Ctrl+C or a `SIGTERM`.
 *
 * What it deliberately does not run on is `SIGKILL`, a power cut or the JVM itself dying, and that
 * is the point: those are exactly the endings that should leave the log without its goodbye and have
 * the next start say so.
 *
 * Nothing is ever brought back here. A desktop process that has begun shutting down does not come
 * back, and a window that is merely minimized has not been put away: the app is still running for
 * whoever left it open, and goes on syncing as it did before.
 */
object JvmAppPresenceInitializer : AppPresenceInitializer {
    override fun installHandler(onPutAway: () -> Unit, onBroughtBack: () -> Unit) {
        Runtime.getRuntime().addShutdownHook(Thread({ onPutAway() }, "conifer-app-presence"))
    }
}
