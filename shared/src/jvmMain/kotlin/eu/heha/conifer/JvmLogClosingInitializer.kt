package eu.heha.conifer

/**
 * A desktop run ends when the process does, and the JVM says so through a shutdown hook - which runs
 * on a window being closed, on `exitApplication`, and on the terminal's Ctrl+C or a `SIGTERM`.
 *
 * What it deliberately does not run on is `SIGKILL`, a power cut or the JVM itself dying, and that
 * is the point: those are exactly the endings that should leave the log without its goodbye and have
 * the next start say so.
 *
 * Nothing ever reopens here. A desktop process that has begun shutting down does not come back.
 */
object JvmLogClosingInitializer : LogClosingInitializer {
    override fun installHandler(closeLog: () -> Unit, reopenLog: () -> Unit) {
        Runtime.getRuntime().addShutdownHook(Thread({ closeLog() }, "conifer-log-closing"))
    }
}
