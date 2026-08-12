package eu.heha.conifer.log

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What the run that crashed has to leave behind for the run after it: enough to say which build went
 * down, when and to what - and nothing that makes reading it back a second failure.
 */
class CrashBreadcrumbTest {

    @Test
    fun keepsTheBuildTheMomentAndTheError() {
        val breadcrumb = crashBreadcrumb(
            error = UncaughtError(origin = "main", throwable = IllegalStateException("boom")),
            at = AT,
            logFile = "/logs/conifer-2026-07-28_143201.log",
            buildLabel = BUILD_LABEL,
        )

        assertEquals(BUILD_LABEL, breadcrumb.buildLabel)
        assertEquals(AT, breadcrumb.at)
        assertEquals("main", breadcrumb.origin)
        assertEquals("IllegalStateException", breadcrumb.type)
        assertEquals("boom", breadcrumb.message)
        assertEquals("/logs/conifer-2026-07-28_143201.log", breadcrumb.logFile)
    }

    /** A platform can report a crash in words rather than as a throwable (see [UncaughtError]). */
    @Test
    fun takesThePlatformSOwnWordingWhereThereIsNoThrowable() {
        val breadcrumb = crashBreadcrumb(
            error = UncaughtError(origin = "worker", throwable = null, message = "script error"),
            at = AT,
            logFile = null,
            buildLabel = BUILD_LABEL,
        )

        assertNull(breadcrumb.type)
        assertEquals("script error", breadcrumb.message)
        assertTrue(breadcrumb.frames.isEmpty(), "no throwable, so no frames: ${breadcrumb.frames}")
    }

    /**
     * The frames are the pointer, not the record - the whole trace is in the log file. A crash on its
     * way out can afford one short write and no more.
     */
    @Test
    fun keepsOnlyTheTopFramesAndDropsTheHeaderLine() {
        val breadcrumb = crashBreadcrumb(
            error = UncaughtError(origin = "main", throwable = deepThrowable()),
            at = AT,
            logFile = null,
            buildLabel = BUILD_LABEL,
        )

        assertTrue(
            breadcrumb.frames.size <= MAX_CRASH_FRAMES,
            "expected at most $MAX_CRASH_FRAMES frames, got ${breadcrumb.frames.size}"
        )
        // The first line of a printed trace is the type and the message, which the breadcrumb already
        // holds as fields - a frame repeating them would be the one line that says nothing.
        assertTrue(
            breadcrumb.frames.none { it.contains(DEEP_MESSAGE) },
            "the header line should not be among the frames: ${breadcrumb.frames}"
        )
        assertTrue(
            breadcrumb.frames.none { it != it.trim() || it.isEmpty() },
            "frames should be trimmed and non-empty: ${breadcrumb.frames}"
        )
    }

    /**
     * The breadcrumb is built to be handed to somebody else, so it goes through the same redaction as
     * every log line ([redactSecrets]) - an exception message quoting the request that failed can
     * carry a token here just as well as there.
     */
    @Test
    fun redactsSecretsOutOfTheMessage() {
        val breadcrumb = crashBreadcrumb(
            error = UncaughtError(
                origin = "main",
                throwable = IllegalStateException(
                    "PUT https://alice:app-password@cloud.example.org/x failed"
                ),
            ),
            at = AT,
            logFile = null,
            buildLabel = BUILD_LABEL,
        )

        val message = breadcrumb.message.orEmpty()
        assertTrue("app-password" !in message, "the password survived redaction: $message")
        assertContains(message, "<redacted>")
    }

    @Test
    fun isWrittenAndReadBackThroughTheStore() {
        val store = InMemoryCrashBreadcrumbStore()
        val breadcrumb = crashBreadcrumb(
            error = UncaughtError(origin = "main", throwable = IllegalStateException("boom")),
            at = AT,
            logFile = "/logs/conifer.log",
            buildLabel = BUILD_LABEL,
        )

        writeCrashBreadcrumb(store, breadcrumb)

        assertEquals(breadcrumb, readCrashBreadcrumb(store))
    }

    /** Nothing stored, and nowhere to store it: both are ordinary, and neither is a failure. */
    @Test
    fun readsNothingWhereThereIsNothingToRead() {
        assertNull(readCrashBreadcrumb(InMemoryCrashBreadcrumbStore()))
        assertNull(readCrashBreadcrumb(null))
    }

    /**
     * The file is written by a process that is being killed, so a truncated one is a thing that
     * happens - and an older app version is entitled to have written a shape this one cannot read.
     * Either way a start must not fail over it.
     */
    @Test
    fun readsNothingFromAFileItCannotMakeSenseOf() {
        assertNull(readCrashBreadcrumb(InMemoryCrashBreadcrumbStore("""{"buildLabel":"1.0.0""")))
        assertNull(readCrashBreadcrumb(InMemoryCrashBreadcrumbStore("not json at all")))
    }

    /** A crash handler that throws replaces a reported bug with an unreported one. */
    @Test
    fun survivesAStoreThatCannotBeWritten() {
        val failing = object : CrashBreadcrumbStore {
            override fun write(text: String) = throw RuntimeException("no space left on device")
            override fun read(): String = throw RuntimeException("no space left on device")
            override fun clear() = throw RuntimeException("no space left on device")
        }

        writeCrashBreadcrumb(failing, breadcrumb())
        assertNull(readCrashBreadcrumb(failing))
        CrashReports(failing, breadcrumb()).forget()
    }

    /** Dismissing the banner is what forgets the crash, so the next start is quiet again. */
    @Test
    fun forgettingClearsTheStore() {
        val store = InMemoryCrashBreadcrumbStore()
        writeCrashBreadcrumb(store, breadcrumb())

        CrashReports(store, readCrashBreadcrumb(store)).forget()

        assertNull(readCrashBreadcrumb(store))
    }

    private fun breadcrumb() = crashBreadcrumb(
        error = UncaughtError(origin = "main", throwable = IllegalStateException("boom")),
        at = AT,
        logFile = null,
        buildLabel = BUILD_LABEL,
    )

    /** A throwable with more frames than the breadcrumb keeps, however shallow the target's traces. */
    private fun deepThrowable(): Throwable = try {
        throwAt(MAX_CRASH_FRAMES * 2)
    } catch (e: IllegalStateException) {
        e
    }

    private fun throwAt(depth: Int): Nothing =
        if (depth == 0) throw IllegalStateException(DEEP_MESSAGE) else throwAt(depth - 1)

    private companion object {
        /** 2026-07-28 12:34:56.789 UTC, as in the log tests. */
        val AT = Instant.fromEpochMilliseconds(1_785_242_096_789)
        const val BUILD_LABEL = "1.2.4 (9), commit 29ba2459, built 2026-08-10T19:58:08Z"
        const val DEEP_MESSAGE = "down here"
    }
}
