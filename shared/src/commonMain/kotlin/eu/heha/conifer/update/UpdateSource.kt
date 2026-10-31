package eu.heha.conifer.update

import eu.heha.conifer.net.CONIFER_USER_AGENT
import eu.heha.conifer.net.installHttpLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import eu.heha.conifer.AppJson as json

/** A published release: its tag and the page that describes it. */
data class Release(val version: String, val url: String)

/**
 * Where the newest release is looked up. Handed in only by the platforms whose builds are released
 * there — see `ConiferApp.initialize`.
 */
fun interface UpdateSource {
    /** The newest published release; throws when that cannot be found out. */
    suspend fun latestRelease(): Release
}

/**
 * The repository's newest release on GitHub. `releases/latest` leaves out drafts and pre-releases,
 * so a release only counts once someone has published its page.
 */
class GitHubReleases(
    userAgent: String = CONIFER_USER_AGENT,
    client: HttpClient = HttpClient(),
    private val repository: String = GITHUB_REPOSITORY,
) : UpdateSource {
    private val client: HttpClient = client.config {
        expectSuccess = false
        install(UserAgent) { agent = userAgent }
        installHttpLogging()
    }

    override suspend fun latestRelease(): Release {
        val response = client.get("https://api.github.com/repos/$repository/releases/latest") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        check(response.status.value in 200..299) { "GitHub answered ${response.status}" }
        val body = json.decodeFromString<LatestReleaseResponse>(response.bodyAsText())
        return Release(version = body.tagName, url = body.htmlUrl)
    }

    companion object {
        const val GITHUB_REPOSITORY = "sihamark/Conifer"
    }
}

@Serializable
private class LatestReleaseResponse(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)
