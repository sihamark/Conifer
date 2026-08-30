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
     * A message is under no obligation to be a sentence: a `VerifyError` arrives with kilobytes of
     * stackmap dump. The same reasoning that bounds the frames bounds this - the write happens on a
     * thread that is on its way out, and what it writes is read back at the next start - and the
     * whole of it is in the log file regardless.
     */
    @Test
    fun keepsOnlyTheBeginningOfAMessageWithNoEndToIt() {
        val dump = "locals: { 'androidx/compose/ui/Modifier', ".repeat(200)
        val breadcrumb = crashBreadcrumb(
            error = UncaughtError(origin = "main", throwable = IllegalStateException(dump)),
            at = AT,
            logFile = null,
            buildLabel = BUILD_LABEL,
        )

        val message = breadcrumb.message.orEmpty()
        assertTrue(
            message.length <= MAX_CRASH_MESSAGE_CHARS + 1,
            "expected at most $MAX_CRASH_MESSAGE_CHARS characters, got ${message.length}"
        )
        // Cut, and saying so: a message that merely stopped would read as one that ended.
        assertTrue(message.endsWith("…"), "the cut is not marked: $message")
        assertContains(message, "androidx/compose/ui/Modifier")
        // A message that fits is left exactly as it is.
        assertEquals("boom", breadcrumb().message)
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
        val store = InMemoryLastRunStore()
        val breadcrumb = crashBreadcrumb(
            error = UncaughtError(origin = "main", throwable = IllegalStateException("boom")),
            at = AT,
            logFile = "/logs/conifer.log",
            buildLabel = BUILD_LABEL,
        )

        writeLastRun(store, LastRunRecord(crash = breadcrumb))

        assertEquals(breadcrumb, readLastRun(store)?.crash)
    }

    /** Nothing stored, and nowhere to store it: both are ordinary, and neither is a failure. */
    @Test
    fun readsNothingWhereThereIsNothingToRead() {
        assertNull(readLastRun(InMemoryLastRunStore())?.crash)
        assertNull(readLastRun(null)?.crash)
    }

    /**
     * The file is written by a process that is being killed, so a truncated one is a thing that
     * happens - and an older app version is entitled to have written a shape this one cannot read.
     * Either way a start must not fail over it.
     */
    @Test
    fun readsNothingFromAFileItCannotMakeSenseOf() {
        assertNull(readLastRun(InMemoryLastRunStore("""{"crash":{"buildLabel":"1.0.0"""))?.crash)
        assertNull(readLastRun(InMemoryLastRunStore("not json at all"))?.crash)
    }

    /** A crash handler that throws replaces a reported bug with an unreported one. */
    @Test
    fun survivesAStoreThatCannotBeWritten() {
        val failing = object : LastRunStore {
            override fun write(text: String) = throw RuntimeException("no space left on device")
            override fun read(): String = throw RuntimeException("no space left on device")
        }

        writeLastRun(failing, LastRunRecord(crash = breadcrumb()))
        assertNull(readLastRun(failing)?.crash)
        RunEndReports(failing, LastRunEnd.Crashed(breadcrumb())).forget()
    }

    /** Dismissing the banner is what forgets the crash, so the next start is quiet again. */
    @Test
    fun forgettingTakesTheCrashOutOfTheRecord() {
        val store = InMemoryLastRunStore()
        writeLastRun(store, LastRunRecord(runningLogFile = LOG_NAME, crash = breadcrumb()))

        RunEndReports(
            store = store,
            lastEnd = LastRunEnd.Crashed(breadcrumb()),
            runningLogFile = LOG_NAME,
        ).forget()

        assertNull(readLastRun(store)?.crash)
        // The log file this run is writing stays named, because the next start still needs to know
        // which log to look at if *this* run is the one that ends without a word.
        assertEquals(LOG_NAME, readLastRun(store)?.runningLogFile)
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
        const val LOG_NAME = "conifer-2026-07-28_143201.log"
    }
}
