package eu.heha.conifer.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GitHubReleasesTest {

    @Test
    fun readsTheTagAndThePageOfTheLatestRelease() = runTest {
        var requestedUrl: String? = null
        var userAgent: String? = null
        val releases = releases { request ->
            requestedUrl = request.url.toString()
            userAgent = request.headers[HttpHeaders.UserAgent]
            respond(
                """{"tag_name":"1.4.0","html_url":"https://github.com/sihamark/Conifer/releases/tag/1.4.0","draft":false,"assets":[]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val release = releases.latestRelease()

        assertEquals(
            Release("1.4.0", "https://github.com/sihamark/Conifer/releases/tag/1.4.0"),
            release
        )
        assertEquals("https://api.github.com/repos/sihamark/Conifer/releases/latest", requestedUrl)
        assertEquals("Conifer (Test)", userAgent)
    }

    /** No release yet (404) and the rate limit (403) are failures, not a release. */
    @Test
    fun failsOnAnErrorStatus() = runTest {
        listOf(HttpStatusCode.NotFound, HttpStatusCode.Forbidden).forEach { status ->
            assertFails { releases { respond("{}", status) }.latestRelease() }
        }
    }

    @Test
    fun failsOnAnAnswerWithoutATag() = runTest {
        assertFails {
            releases { respond("""{"message":"Moved"}""", HttpStatusCode.OK) }.latestRelease()
        }
    }

    private fun releases(handler: MockRequestHandler) =
        GitHubReleases(userAgent = "Conifer (Test)", client = HttpClient(MockEngine(handler)))
}
