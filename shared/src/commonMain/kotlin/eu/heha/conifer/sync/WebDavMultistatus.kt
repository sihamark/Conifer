package eu.heha.conifer.sync

/**
 * One `d:response` of a WebDAV PROPFIND multistatus reply, restricted to the two properties
 * [KtorWebDavStore] needs ([RemoteStore.Entry.etag] / [RemoteStore.Entry.isDirectory]).
 */
internal data class MultistatusEntry(
    val href: String,
    val etag: String?,
    val isCollection: Boolean,
)

// Element names are matched by local name only (ignoring the namespace prefix, e.g. "d:"/"D:"/
// none), since servers are free to choose their own prefix for the "DAV:" namespace (spec §4:
// "write a minimal parser, no WebDAV library needed").
//
// Element content can legitimately span newlines (pretty-printed server responses), so "any
// character" below is spelled as [\s\S] rather than a plain "." + RegexOption.DOT_MATCHES_ALL:
// on this project's KMP setup, DOT_MATCHES_ALL doesn't resolve for the commonMain metadata
// target even though every individual platform target has it - [\s\S] needs no such option and
// is portable everywhere.
private val RESPONSE_REGEX =
    Regex("""<(?:[\w.-]+:)?response[ >][\s\S]*?</(?:[\w.-]+:)?response>""")
private val HREF_REGEX =
    Regex("""<(?:[\w.-]+:)?href[^>]*>([\s\S]*?)</(?:[\w.-]+:)?href>""")
private val PROPSTAT_REGEX =
    Regex("""<(?:[\w.-]+:)?propstat[ >][\s\S]*?</(?:[\w.-]+:)?propstat>""")
private val STATUS_REGEX =
    Regex("""<(?:[\w.-]+:)?status[^>]*>([\s\S]*?)</(?:[\w.-]+:)?status>""")
private val GETETAG_REGEX =
    Regex("""<(?:[\w.-]+:)?getetag[^>]*>([\s\S]*?)</(?:[\w.-]+:)?getetag>""")
private val RESOURCETYPE_REGEX =
    Regex("""<(?:[\w.-]+:)?resourcetype[^>]*>([\s\S]*?)</(?:[\w.-]+:)?resourcetype>""")
private val COLLECTION_REGEX = Regex("""<(?:[\w.-]+:)?collection\b""")
private val XML_ENTITY_REGEX = Regex("""&(amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);""")
private val STATUS_OK_REGEX = Regex("""\s200\s""")

/** Parses a WebDAV multistatus (status 207) response body into one entry per `d:response`. */
internal fun parseMultistatus(xml: String): List<MultistatusEntry> =
    RESPONSE_REGEX.findAll(xml).map { responseMatch ->
        val response = responseMatch.value
        val href = HREF_REGEX.find(response)?.groupValues?.get(1)
            ?.let(::decodeXmlEntities)
            ?.let(::decodePercentEncoded)
            ?: error("WebDAV response without an href: $response")

        var etag: String? = null
        var isCollection = false
        // A response can carry several propstat blocks (e.g. one status-200 block for the props
        // the server has, one status-404 block for ones it doesn't) - only trust the 200 ones.
        for (propstatMatch in PROPSTAT_REGEX.findAll(response)) {
            val propstat = propstatMatch.value
            val status = STATUS_REGEX.find(propstat)?.groupValues?.get(1).orEmpty()
            if (!STATUS_OK_REGEX.containsMatchIn(" $status ")) continue

            GETETAG_REGEX.find(propstat)?.groupValues?.get(1)?.let {
                etag = normalizeEtag(decodeXmlEntities(it))
            }
            RESOURCETYPE_REGEX.find(propstat)?.groupValues?.get(1)?.let {
                if (COLLECTION_REGEX.containsMatchIn(it)) isCollection = true
            }
        }
        MultistatusEntry(href = href, etag = etag, isCollection = isCollection)
    }.toList()

/** Strips a leading `W/` and surrounding quotes, per spec §4: "always compare normalized values". */
internal fun normalizeEtag(raw: String): String {
    var value = raw.trim()
    if (value.startsWith("W/")) value = value.removePrefix("W/").trim()
    if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
        value = value.substring(1, value.length - 1)
    }
    return value
}

private fun decodeXmlEntities(raw: String): String =
    XML_ENTITY_REGEX.replace(raw) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> when {
                entity.startsWith("#x") -> entity.substring(2).toInt(16).toChar().toString()
                entity.startsWith("#") -> entity.substring(1).toInt().toChar().toString()
                else -> match.value
            }
        }
    }

// hrefs are always plain ASCII with %XX escapes by URI grammar, so it's safe to treat every
// non-escaped character as a single UTF-8 byte and decode the whole assembled buffer as UTF-8.
private fun decodePercentEncoded(raw: String): String {
    val bytes = ArrayList<Byte>(raw.length)
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '%' && i + 3 <= raw.length) {
            val byte = raw.substring(i + 1, i + 3).toIntOrNull(16)
            if (byte != null) {
                bytes.add(byte.toByte())
                i += 3
                continue
            }
        }
        bytes.add(c.code.toByte())
        i++
    }
    return bytes.toByteArray().decodeToString()
}
