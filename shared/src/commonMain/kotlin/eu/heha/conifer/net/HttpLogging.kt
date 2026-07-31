package eu.heha.conifer.net

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders

/**
 * The tag every HTTP line carries, so the wire traffic can be read - or filtered out - on its
 * own: `grep '\[http\]' conifer-*.log`, `adb logcat -s http`.
 *
 * Nothing else in the app tags its [Napier] calls, so the sync stack's own commentary stays
 * untagged and the two are trivially separable in the same log file.
 */
const val HTTP_LOG_TAG: String = "http"

/**
 * Mirrors Ktor's client traffic into [Napier] under [HTTP_LOG_TAG].
 *
 * Logged at DEBUG, matching the level the sync stack uses for its own per-request detail, so a
 * console antilog that filters DEBUG out drops both together.
 *
 * [level] defaults to [LogLevel.HEADERS] rather than [LogLevel.ALL] on purpose: the Login Flow v2
 * poll response body *is* the app password, and [eu.heha.conifer.log.redactSecrets] only catches
 * `password=` in form/URL shape, not the `"appPassword": "..."` JSON the server actually sends.
 * Headers are safe to log because `Authorization` is replaced here, before the line is ever
 * formatted - so it never reaches the log file *or* the debug console.
 *
 * Pass [LogLevel.ALL] only against a throwaway account.
 */
fun HttpClientConfig<*>.installHttpLogging(level: LogLevel = LogLevel.HEADERS) {
    install(Logging) {
        logger = NapierHttpLogger
        this.level = level
        sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
    }
}

private object NapierHttpLogger : Logger {
    override fun log(message: String) {
        Napier.d(tag = HTTP_LOG_TAG) { message }
    }
}
