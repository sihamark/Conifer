package eu.heha.conifer.log

/** What a redacted value is replaced with - deliberately obvious in a log file. */
private const val REDACTED = "<redacted>"

/**
 * Second line of defence for the log file's "no secrets" rule.
 *
 * The first line is the log calls themselves: nothing in the sync stack ever passes an app
 * password, a Login Flow v2 poll token or a login URL to [io.github.aakira.napier.Napier] (see
 * [eu.heha.conifer.auth.Credentials] and [eu.heha.conifer.sync.SyncCoordinator]). But log lines
 * also carry text nobody here wrote - exception messages from Ktor and the server, which can quote
 * the request that failed - so every line goes through this on its way to [LogFileSink].
 *
 * Deliberately over-eager: a log with a redacted marker where a value would be useful costs a
 * troubleshooting round trip, a log with a live app password in it costs an account.
 */
internal fun redactSecrets(text: String): String =
    REDACTIONS.fold(text) { redacted, (pattern, replacement) ->
        pattern.replace(redacted, replacement)
    }

private val REDACTIONS: List<Pair<Regex, String>> = listOf(
    // `https://alice:app-password@cloud.example.org/...` - Basic credentials in a URL.
    Regex("://[^\\s/@]+@") to "://$REDACTED@",
    // `?token=...`, `&appPassword=...` in a URL or a quoted form body.
    Regex(
        "(token|password|passwd|pwd|secret|access_token)=[^&\\s\"'}\\]]*",
        RegexOption.IGNORE_CASE
    ) to "$1=$REDACTED",
    // The one-shot Login Flow v2 URL: whoever holds it can finish the login.
    Regex("(/login/v2/flow/)[^\\s\"'}\\]]+") to "$1$REDACTED",
    // An `Authorization: Basic ...` / `Bearer ...` header quoted back at us.
    Regex(
        "(basic|bearer)\\s+[A-Za-z0-9+/=._~-]{8,}",
        RegexOption.IGNORE_CASE
    ) to "$1 $REDACTED",
)
