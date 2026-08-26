package eu.heha.conifer.log

import io.github.aakira.napier.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The log file's two promises: every line is timestamped, and no secret ever reaches it (spec §3.2
 * keeps credentials out of plain storage - a log file on disk is plain storage).
 */
class FileAntilogTest {

    @Test
    fun formatsALineWithTimestampLevelAndMessage() {
        val line = formatLogLine(
            at = AT,
            priority = LogLevel.INFO,
            tag = null,
            throwable = null,
            message = "sync started (manual)",
            timeZone = TimeZone.UTC,
        )

        assertEquals("2026-07-28 12:34:56.789 I  sync started (manual)", line)
    }

    @Test
    fun includesTheTagWhenNapierSuppliesOne() {
        val line = formatLogLine(
            at = AT,
            priority = LogLevel.WARNING,
            tag = "SyncEngine",
            throwable = null,
            message = "push: retrying",
            timeZone = TimeZone.UTC,
        )

        assertEquals("2026-07-28 12:34:56.789 W  [SyncEngine] push: retrying", line)
    }

    /** A retried, self-healing warning shouldn't bury the log in stack frames. */
    @Test
    fun summarizesAThrowableBelowErrorButSpellsItOutAtError() {
        val warning = formatLogLine(
            at = AT,
            priority = LogLevel.WARNING,
            tag = null,
            throwable = IllegalStateException("etag mismatch"),
            message = "push failed",
            timeZone = TimeZone.UTC,
        )
        val error = formatLogLine(
            at = AT,
            priority = LogLevel.ERROR,
            tag = null,
            throwable = IllegalStateException("etag mismatch"),
            message = "push failed",
            timeZone = TimeZone.UTC,
        )

        assertEquals(
            "2026-07-28 12:34:56.789 W  push failed | IllegalStateException: etag mismatch",
            warning
        )
        assertContains(error, "IllegalStateException")
        assertTrue(error.lines().size > 1, "an error should carry its stack trace: $error")
    }

    @Test
    fun redactsCredentialsInAUrl() {
        val redacted = redactSecrets(
            "PUT https://alice:s3cret-app-password@cloud.example.org/remote.php/dav failed"
        )

        assertEquals(
            "PUT https://<redacted>@cloud.example.org/remote.php/dav failed",
            redacted
        )
        assertFalse("s3cret-app-password" in redacted)
    }

    @Test
    fun redactsTokenAndPasswordParameters() {
        val redacted = redactSecrets("body was token=abc123&appPassword=xyz789&user=alice")

        assertEquals(
            "body was token=<redacted>&appPassword=<redacted>&user=alice",
            redacted
        )
    }

    /** Whoever holds the Login Flow v2 URL can complete the login - it is a credential. */
    @Test
    fun redactsTheLoginFlowUrlToken() {
        val redacted =
            redactSecrets("opening https://cloud.example.org/index.php/login/v2/flow/tok-123")

        assertEquals(
            "opening https://cloud.example.org/index.php/login/v2/flow/<redacted>",
            redacted
        )
    }

    @Test
    fun redactsAnAuthorizationHeaderEchoedBackAtUs() {
        val redacted = redactSecrets("Authorization: Basic YWxpY2U6c2VjcmV0cGFzc3dvcmQ=")

        assertEquals("Authorization: Basic <redacted>", redacted)
    }

    @Test
    fun leavesOrdinarySyncLinesAlone() {
        val line = "pull: 2 of 5 buckets changed, appRoot Conifer, etag \"abc123\""

        assertEquals(line, redactSecrets(line))
    }

    /** What Napier hands the antilog also goes through the redactor, not just our own messages. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun writesRedactedLinesToTheSink() = runTest {
        val sink = RecordingSink()
        val antilog = FileAntilog(
            sink = sink,
            scope = backgroundScope,
            clock = object : Clock {
                override fun now(): Instant = AT
            },
            timeZone = TimeZone.UTC,
        )

        antilog.log(LogLevel.INFO, null, null, "GET https://alice:pw@cloud.example.org/dav")
        runCurrent()

        assertEquals(
            listOf("2026-07-28 12:34:56.789 I  GET https://<redacted>@cloud.example.org/dav"),
            sink.lines
        )
    }

    private class RecordingSink : LogFileSink {
        override val location: String = "memory"
        val lines = mutableListOf<String>()
        override fun appendLine(line: String) {
            lines += line
        }
    }

    private companion object {
        /** 2026-07-28 12:34:56.789 UTC. */
        val AT = Instant.fromEpochMilliseconds(1_785_242_096_789)
    }
}
