package eu.heha.conifer.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Nextcloud Login Flow v2: a stock, unauthenticated Nextcloud endpoint that lets a device obtain
 * an app password without ever seeing the user's real password (Nextcloud sync spec §10). The
 * device starts a session, the user completes it in their own browser, and the device polls a
 * one-shot endpoint until the server hands back credentials - no custom server-side app needed.
 */
class LoginFlowV2(
    client: HttpClient = HttpClient(),
) {
    private val client: HttpClient = client.config { expectSuccess = false }
    private val json = Json { ignoreUnknownKeys = true }

    /** `POST {serverUrl}/index.php/login/v2`: starts a new session. */
    suspend fun start(serverUrl: String): Session {
        val response = client.request("${serverUrl.trimEnd('/')}/index.php/login/v2") {
            method = HttpMethod.Post
        }
        if (response.status.value !in 200..299) throw LoginFlowException(response.status.value)
        val body = json.decodeFromString<InitiateResponse>(response.bodyAsText())
        return Session(
            loginUrl = body.login,
            pollToken = body.poll.token,
            pollEndpoint = body.poll.endpoint
        )
    }

    /**
     * Polls [session] once. Returns `null` while the user hasn't completed the flow in their
     * browser yet (server responds 404); the server delivers the success response only once.
     */
    suspend fun pollOnce(session: Session): LoginResult? {
        val response = client.request(session.pollEndpoint) {
            method = HttpMethod.Post
            setBody(FormDataContent(Parameters.build { append("token", session.pollToken) }))
        }
        return when (response.status.value) {
            in 200..299 -> json.decodeFromString<PollResponse>(response.bodyAsText())
                .let {
                    LoginResult(
                        server = it.server,
                        loginName = it.loginName,
                        appPassword = it.appPassword
                    )
                }

            404 -> null
            else -> throw LoginFlowException(response.status.value)
        }
    }

    /**
     * Repeats [pollOnce] roughly once a second until the user completes the flow or [timeout]
     * elapses (the server itself expires the session's token after ~20 minutes). There is no
     * separate cancel API - cancel the calling coroutine to give up early.
     */
    suspend fun awaitCompletion(session: Session, timeout: Duration = 20.minutes): LoginResult {
        val deadline = TimeSource.Monotonic.markNow() + timeout
        while (true) {
            pollOnce(session)?.let { return it }
            if (deadline.hasPassedNow()) throw LoginFlowTimeoutException()
            delay(POLL_INTERVAL)
        }
    }

    /** The user-facing [loginUrl] to open in a browser, plus what's needed to poll for completion. */
    data class Session(val loginUrl: String, val pollToken: String, val pollEndpoint: String)

    /** The account name and app password to store in [eu.heha.conifer.auth.Credentials]. */
    data class LoginResult(val server: String, val loginName: String, val appPassword: String)

    @Serializable
    private data class InitiateResponse(val poll: Poll, val login: String) {
        @Serializable
        data class Poll(val token: String, val endpoint: String)
    }

    @Serializable
    private data class PollResponse(
        val server: String,
        val loginName: String,
        val appPassword: String
    )

    private companion object {
        val POLL_INTERVAL = 1.seconds
    }
}

/** A Login Flow v2 request failed with an unexpected (non-404) HTTP status. */
class LoginFlowException(val status: Int) :
    Exception("Login flow request failed with status $status")

/** [LoginFlowV2.awaitCompletion] gave up waiting for the user to complete the flow in time. */
class LoginFlowTimeoutException :
    Exception("Login flow timed out waiting for the user to complete it")
