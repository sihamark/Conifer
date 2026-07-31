package eu.heha.conifer.sync

import eu.heha.conifer.net.CONIFER_USER_AGENT
import eu.heha.conifer.net.installHttpLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:getetag/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>"""

/**
 * WebDAV [RemoteStore] against a Nextcloud (or any WebDAV) server (spec §4). path parameters
 * are relative to `remote.php/dav/files/{username}/` — [eu.heha.conifer.sync.SyncEngine] (spec
 * §5) is responsible for prefixing them with `appRoot`/`.sync` as needed.
 *
 * Authentication is HTTP Basic against an app password (spec §3.2/§10 Login Flow v2); [client]
 * defaults to a plain platform [HttpClient] but can be overridden, e.g. in tests. [userAgent]
 * identifies the app in the server's access log (see [eu.heha.conifer.net.coniferUserAgent]).
 */
class KtorWebDavStore(
    serverUrl: String,
    username: String,
    password: String,
    userAgent: String = CONIFER_USER_AGENT,
    client: HttpClient = HttpClient(),
) : RemoteStore {

    private val filesBaseUrl =
        "${serverUrl.trimEnd('/')}/remote.php/dav/files/${encodeSegment(username)}/"
    private val bulkUrl = "${serverUrl.trimEnd('/')}/remote.php/dav/bulk"

    private val client: HttpClient = client.config {
        expectSuccess = false
        install(UserAgent) { agent = userAgent }
        installHttpLogging()
        install(Auth) {
            basic {
                credentials { BasicAuthCredentials(username, password) }
                // Nextcloud never challenges with a 401/WWW-Authenticate for an app password in
                // practice; sending Basic auth unconditionally avoids a doubled round trip.
                sendWithoutRequest { true }
            }
        }
    }

    override suspend fun etag(path: String): String? {
        val response = propfind(path, depth = "0")
        return when (response.status.value) {
            404 -> null
            in 200..299 -> parseMultistatus(response.bodyAsText()).firstOrNull()?.etag
            else -> throw WebDavException(path, response.status.value)
        }
    }

    override suspend fun list(path: String): List<RemoteStore.Entry> {
        val response = propfind(path, depth = "1")
        when (response.status.value) {
            404 -> throw WebDavNotFoundException(path)
            in 200..299 -> Unit
            else -> throw WebDavException(path, response.status.value)
        }
        val entries = parseMultistatus(response.bodyAsText())
        // A Depth:1 PROPFIND always includes the collection itself as one of the responses
        // (RFC 4918 §9.1), one path segment shallower than any of its children. Rather than
        // pattern-match the resource's own href against [path] (fragile: a child that happens to
        // share the last path segment, e.g. a folder listing another folder of the same name,
        // would falsely match a plain suffix check), drop whichever entry/entries sit at the
        // shallowest depth - by construction that's exactly the self entry.
        val selfDepth = entries.minOfOrNull { it.href.trimEnd('/').count { c -> c == '/' } }
            ?: return emptyList()
        return entries
            .filterNot { it.href.trimEnd('/').count { c -> c == '/' } == selfDepth }
            .map { entry ->
                RemoteStore.Entry(
                    name = entry.href.trimEnd('/').substringAfterLast('/'),
                    etag = entry.etag.orEmpty(),
                    isDirectory = entry.isCollection,
                )
            }
    }

    override suspend fun get(path: String): ByteArray {
        val response = withNetworkRetry { client.request(urlFor(path)) { method = HttpMethod.Get } }
        return when (response.status.value) {
            in 200..299 -> response.body()
            404 -> throw WebDavNotFoundException(path)
            else -> throw WebDavException(path, response.status.value)
        }
    }

    override suspend fun put(
        path: String,
        body: ByteArray,
        ifMatch: String?,
        ifNoneMatchAll: Boolean,
    ): String {
        val response = client.request(urlFor(path)) {
            method = HttpMethod.Put
            ifMatch?.let { header("If-Match", "\"$it\"") }
            if (ifNoneMatchAll) header("If-None-Match", "*")
            setBody(ByteArrayContent(body, ContentType.Application.OctetStream))
        }
        return when (response.status.value) {
            in 200..299 -> response.headers["ETag"]?.let(::normalizeEtag) ?: etag(path)
            ?: error("Nextcloud returned no ETag for $path after a successful PUT")

            412 -> throw WebDavConflictException(path)
            404 -> throw WebDavNotFoundException(path)
            else -> throw WebDavException(path, response.status.value)
        }
    }

    override suspend fun mkdirs(path: String) {
        val response = client.request(urlFor(path) + "/") { method = HttpMethod("MKCOL") }
        when (response.status.value) {
            in 200..299, 405 -> return // 405: the folder already exists, which is fine (spec §4)
            else -> throw WebDavException(path, response.status.value)
        }
    }

    override suspend fun delete(path: String) {
        val response = client.request(urlFor(path)) { method = HttpMethod.Delete }
        when (response.status.value) {
            in 200..299, 404 -> return
            else -> throw WebDavException(path, response.status.value)
        }
    }

    override suspend fun bulkPut(files: List<RemoteStore.BulkFile>): List<String> {
        if (files.isEmpty()) return emptyList()

        val boundary = "conifer-bulk-$BULK_BOUNDARY_SUFFIX"
        val bulkResponse = runCatching {
            client.request(bulkUrl) {
                method = HttpMethod.Post
                setBody(
                    ByteArrayContent(
                        buildBulkBody(boundary, files),
                        ContentType.parse("multipart/related; boundary=$boundary")
                    )
                )
            }
        }.getOrNull()

        // The bulk endpoint's response is itself a multipart reply whose exact shape isn't
        // stable enough to parse blindly (spec §4 calls bulkPut a pure optimization). Rather than
        // guess at that format, treat a successful POST as "the files are on the server now" and
        // fetch each one's authoritative ETag directly — still far cheaper than N conditional
        // PUTs, and correct regardless of the exact bulk response layout.
        if (bulkResponse != null && bulkResponse.status.value in 200..299) {
            return files.map { file ->
                etag(file.path) ?: error("no etag for ${file.path} after bulk upload")
            }
        }

        // Fallback: sequential unconditional creates (spec §4: "Fallback: sequential put()").
        return files.map { file -> put(file.path, file.body, ifNoneMatchAll = true) }
    }

    private suspend fun propfind(path: String, depth: String): HttpResponse =
        withNetworkRetry {
            client.request(urlFor(path)) {
                method = HttpMethod("PROPFIND")
                header("Depth", depth)
                setBody(
                    ByteArrayContent(
                        PROPFIND_BODY.encodeToByteArray(),
                        ContentType.Application.Xml
                    )
                )
            }
        }

    private fun urlFor(path: String): String = filesBaseUrl + encodePath(path.trim('/'))

    /**
     * Retries [block] with backoff on a network-level failure (spec §4: "Retries: idempotent
     * requests (PROPFIND, GET) 2× with backoff"). [block] must be a bare client call that either
     * returns an [HttpResponse] or throws on connection/timeout failure - `expectSuccess = false`
     * means HTTP error statuses (4xx/5xx) never throw here, so this never blindly retries a
     * server-level rejection, only a request that didn't get a response at all.
     */
    private suspend fun withNetworkRetry(block: suspend () -> HttpResponse): HttpResponse {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt > NETWORK_RETRY_ATTEMPTS) throw e
                delay((NETWORK_RETRY_BACKOFF_MS * attempt).milliseconds)
            }
        }
    }

    private companion object {
        // A fixed-looking but effectively unique boundary marker; only needs to not collide with
        // the post JSON bodies being uploaded, which never contain it.
        const val BULK_BOUNDARY_SUFFIX = "8f21b6-3c9e-4e2a-b6d1-conifer-sync"
        const val NETWORK_RETRY_ATTEMPTS = 2
        const val NETWORK_RETRY_BACKOFF_MS = 200L
    }
}

private fun buildBulkBody(boundary: String, files: List<RemoteStore.BulkFile>): ByteArray {
    val parts = mutableListOf<ByteArray>()
    fun line(text: String) = parts.add("$text\r\n".encodeToByteArray())
    for (file in files) {
        line("--$boundary")
        line("X-File-Path: /${file.path.trim('/')}")
        line("X-OC-Mtime: ${file.mtime}")
        line("Content-Length: ${file.body.size}")
        line("Content-Type: application/octet-stream")
        line("")
        parts.add(file.body)
        parts.add("\r\n".encodeToByteArray())
    }
    line("--$boundary--")
    return parts.fold(ByteArray(0)) { acc, part -> acc + part }
}

private val UNRESERVED_PATH_CHARS =
    (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')).toSet()

private fun encodeSegment(segment: String): String = buildString {
    for (byte in segment.encodeToByteArray()) {
        val char = byte.toInt().toChar()
        if (byte >= 0 && char in UNRESERVED_PATH_CHARS) {
            append(char)
        } else {
            append('%')
            append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
}

private fun encodePath(path: String): String =
    path.split('/').joinToString("/", transform = ::encodeSegment)
