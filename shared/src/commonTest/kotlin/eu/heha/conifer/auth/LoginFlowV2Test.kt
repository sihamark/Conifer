package eu.heha.conifer.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration

/**
 * [LoginFlowV2] against a [MockEngine] standing in for the Nextcloud server - no real network,
 * exercising exactly the request/response contract the Nextcloud Login Flow v2 endpoints define.
 */
class LoginFlowV2Test {

    @Test
    fun startParsesTheLoginUrlAndPollFields() = runTest {
        val flow = LoginFlowV2(client { respondJson(INITIATE_BODY) })

        val session = flow.start("https://cloud.example.org")

        assertEquals("https://cloud.example.org/index.php/login/v2/flow/tok-123", session.loginUrl)
        assertEquals("tok-123", session.pollToken)
        assertEquals("https://cloud.example.org/login/v2/poll", session.pollEndpoint)
    }

    @Test
    fun startThrowsOnAnUnexpectedStatus() = runTest {
        val flow = LoginFlowV2(client { respond("", HttpStatusCode.InternalServerError) })

        assertFailsWith<LoginFlowException> { flow.start("https://cloud.example.org") }
    }

    @Test
    fun pollOnceReturnsNullWhileTheUserHasNotCompletedTheFlowYet() = runTest {
        val flow = LoginFlowV2(client { respond("", HttpStatusCode.NotFound) })

        val result = flow.pollOnce(SESSION)

        assertEquals(null, result)
    }

    @Test
    fun pollOnceReturnsTheCredentialsOnceTheFlowCompletes() = runTest {
        val flow = LoginFlowV2(client { respondJson(POLL_SUCCESS_BODY) })

        val result = flow.pollOnce(SESSION)

        assertEquals(
            LoginFlowV2.LoginResult(
                server = "https://cloud.example.org",
                loginName = "alice",
                appPassword = "app-password-xyz"
            ),
            result
        )
    }

    @Test
    fun pollOnceThrowsOnAnUnexpectedStatus() = runTest {
        val flow = LoginFlowV2(client { respond("", HttpStatusCode.InternalServerError) })

        assertFailsWith<LoginFlowException> { flow.pollOnce(SESSION) }
    }

    @Test
    fun awaitCompletionRetriesUntilTheFlowCompletes() = runTest {
        // Only one miss before success: awaitCompletion's retry loop sleeps a real second
        // (POLL_INTERVAL) between attempts, and there's no virtual-clock control over that here
        // (kotlinx-coroutines-test's virtual time isn't reliably fast-forwarded on every target,
        // e.g. wasmJs under karma) - keep the real wall-clock cost of this test to one tick.
        var attempt = 0
        val flow = LoginFlowV2(client {
            attempt++
            if (attempt < 2) respond(
                "",
                HttpStatusCode.NotFound
            ) else respondJson(POLL_SUCCESS_BODY)
        })

        val result = flow.awaitCompletion(SESSION)

        assertEquals(2, attempt)
        assertEquals("alice", result.loginName)
    }

    @Test
    fun awaitCompletionTimesOutIfTheUserNeverCompletesTheFlow() = runTest {
        val flow = LoginFlowV2(client { respond("", HttpStatusCode.NotFound) })

        // A zero timeout expires between the first poll and the deadline check, before the loop
        // ever reaches its real-second `delay` - keeps this real-time-cheap (see note above).
        assertFailsWith<LoginFlowTimeoutException> {
            flow.awaitCompletion(SESSION, timeout = Duration.ZERO)
        }
    }

    private fun client(handler: MockRequestHandler) = HttpClient(MockEngine(handler))

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
}

private val SESSION = LoginFlowV2.Session(
    loginUrl = "https://cloud.example.org/index.php/login/v2/flow/tok-123",
    pollToken = "tok-123",
    pollEndpoint = "https://cloud.example.org/login/v2/poll",
)

private const val INITIATE_BODY = """
{
  "poll": {
    "token": "tok-123",
    "endpoint": "https://cloud.example.org/login/v2/poll"
  },
  "login": "https://cloud.example.org/index.php/login/v2/flow/tok-123"
}
"""

private const val POLL_SUCCESS_BODY = """
{
  "server": "https://cloud.example.org",
  "loginName": "alice",
  "appPassword": "app-password-xyz"
}
"""
