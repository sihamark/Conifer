package eu.heha.conifer

import eu.heha.conifer.log.FileAntilog
import eu.heha.conifer.log.LogFileSink
import eu.heha.conifer.log.UncaughtError
import eu.heha.conifer.log.logUncaughtError
import io.github.aakira.napier.LogLevel
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The whole chain on a real file: the installed handler reports, the error is formatted, and it is on
 * disk by the time the handler returns - which is all the guarantee a crashing process gets.
 *
 * Unlike the common test this runs against a live [FileAntilog] drain coroutine on real threads, so
 * it also says that the crashing thread writing beside that coroutine is safe (see [LogFileSink]).
 */
class JvmUncaughtErrorLoggingTest {

    private val folder: File = Files.createTempDirectory("conifer-uncaught").toFile()
    private val original = Thread.getDefaultUncaughtExceptionHandler()

    @AfterTest
    fun cleanUp() {
        Thread.setDefaultUncaughtExceptionHandler(original)
        folder.deleteRecursively()
    }

    @Test
    fun writesTheCrashToTheLogFileBeforeTheHandlerReturns() {
        val file = File(folder, "conifer-run.log")
        val antilog = FileAntilog(AppendingSink(file))
        // No previous handler, so the JVM's own stderr trace is what this falls through to.
        Thread.setDefaultUncaughtExceptionHandler(null)
        JvmUncaughtErrorInitializer.installHandler { logUncaughtError(it, antilog) }

        antilog.log(LogLevel.INFO, null, null, "pull: 3 buckets changed")
        Thread.getDefaultUncaughtExceptionHandler()
            .uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        // Read immediately: nothing is awaited, because a crashing app has nothing to await with.
        val written = file.readText()
        assertTrue("uncaught error" in written, "no crash line in:\n$written")
        assertTrue("IllegalStateException: boom" in written, "no throwable in:\n$written")
        assertTrue(
            "JvmUncaughtErrorLoggingTest" in written,
            "expected the stack trace of the failing call in:\n$written"
        )
        assertTrue("pull: 3 buckets changed" in written, "queued line was lost:\n$written")
    }

    /** The same sink the desktop and Android initializers use: open, append, close, per line. */
    private class AppendingSink(private val file: File) : LogFileSink {
        override val location: String = file.absolutePath
        override fun appendLine(line: String) {
            file.appendText(line + "\n")
        }
    }
}
