package eu.heha.conifer.net

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpLoggingTest {

    private val recorded = RecordingAntilog()

    @BeforeTest
    fun installRecorder() {
        // Napier's base list is global; takeLogarithm() in teardown puts it back as we found it
        // (no antilog is installed in tests, only ConiferApp.initialize installs any).
        Napier.base(recorded)
    }

    @AfterTest
    fun removeRecorder() {
        Napier.takeLogarithm()
    }

    @Test
    fun trafficIsLoggedUnderTheHttpTag() = runTest {
        client().request("https://cloud.example.org/remote.php/dav/files/alice/")
            .bodyAsText()

        assertTrue(recorded.entries.isNotEmpty(), "expected the client to log something")
        assertTrue(
            recorded.entries.all { it.tag == HTTP_LOG_TAG },
            "every line must carry the http tag, got ${recorded.entries.map { it.tag }.distinct()}"
        )
        assertTrue(
            recorded.text().contains("https://cloud.example.org/remote.php/dav/files/alice/"),
            "expected the request URL in the log, got:\n${recorded.text()}"
        )
    }

    @Test
    fun trafficIsLoggedAtDebugSoItFiltersWithTheRestOfTheDetail() = runTest {
        client().request("https://cloud.example.org/x").bodyAsText()

        assertEquals(listOf(LogLevel.DEBUG), recorded.entries.map { it.priority }.distinct())
    }

    @Test
    fun theAuthorizationHeaderIsNeverLogged() = runTest {
        val password = "s3cr3t-app-password"

        client().request("https://cloud.example.org/x") {
            header(HttpHeaders.Authorization, "Basic $password")
        }.bodyAsText()

        val logged = recorded.text()
        assertTrue(logged.contains(HttpHeaders.Authorization), "the header should still be listed")
        assertFalse(
            logged.contains(password),
            "the credential must be sanitized before logging, got:\n$logged"
        )
    }

    @Test
    fun responseBodiesAreNotLoggedAtTheDefaultLevel() = runTest {
        // The Login Flow v2 poll response body is the app password itself, so HEADERS (not ALL)
        // has to stay the default - see installHttpLogging.
        client(body = """{"appPassword":"s3cr3t-app-password"}""")
            .request("https://cloud.example.org/login/v2/poll")
            .bodyAsText()

        assertFalse(
            recorded.text().contains("s3cr3t-app-password"),
            "response bodies must not be logged by default, got:\n${recorded.text()}"
        )
    }

    private fun client(body: String = "ok") = HttpClient(
        MockEngine { respond(body, HttpStatusCode.OK) }
    ).config { installHttpLogging() }

    private class RecordingAntilog : Antilog() {
        val entries = mutableListOf<Entry>()

        override fun performLog(
            priority: LogLevel,
            tag: String?,
            throwable: Throwable?,
            message: String?
        ) {
            entries += Entry(priority, tag, message)
        }

        fun text() = entries.joinToString("\n") { it.message.orEmpty() }

        data class Entry(val priority: LogLevel, val tag: String?, val message: String?)
    }
}
