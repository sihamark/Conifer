package eu.heha.conifer

import io.github.aakira.napier.Napier
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement

/**
 * A browser's version of the share sheet is a download: the report is handed to the page as a file
 * the browser then saves wherever that user's downloads go, and from there it can be attached to
 * whatever the report is being sent in.
 *
 * A `data:` URL rather than a `Blob` and an object URL, because a report is capped at
 * [eu.heha.conifer.log.MAX_LOG_TAIL_CHARS] and comfortably fits in one - and this way there is no
 * object URL whose lifetime has to be managed and leaked if the click never happens.
 */
object WasmReportShareController : ReportShareController {
    override fun share(fileName: String, text: String): Boolean = runCatching {
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = "data:text/plain;charset=utf-8," + encodeUriComponent(text)
        anchor.download = fileName
        // Attached, clicked and taken straight back out: a click on an element outside the document
        // is ignored by some browsers, and the anchor has no business staying on the page.
        document.body?.appendChild(anchor)
        anchor.click()
        anchor.remove()
        true
    }.getOrElse { error ->
        Napier.e(error) { "could not offer the report as a download" }
        false
    }
}

@Suppress("unused")
@JsFun("(text) => encodeURIComponent(text)")
private external fun encodeUriComponent(text: String): String
