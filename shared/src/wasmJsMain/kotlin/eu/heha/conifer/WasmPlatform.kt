package eu.heha.conifer

import eu.heha.conifer.WasmPlatform.usesOptionForWordJump
import kotlinx.browser.window

data object WasmPlatform : Platform {
    /** Declared first: [usesOptionForWordJump] reads it while this object is initializing. */
    private val appleAgents = listOf("Macintosh", "Mac OS X", "iPad", "iPhone")

    override val name: String = "Web"

    /**
     * Web means a browser, and browsers are overwhelmingly used at a keyboard — a phone browser is
     * the exception, and one that costs an icon in the top bar rather than anything that matters.
     */
    override val hasHardwareKeyboard: Boolean = true

    /**
     * The machine the browser is running on, not the browser: whoever is at an Apple keyboard jumps
     * words with ⌥ in a web page as well, and Compose's own text field follows the same convention
     * on this target.
     *
     * Read off the user agent because that is the string every browser still fills in truthfully
     * enough for this ("Macintosh" on macOS, "iPad"/"iPhone" on iOS — and an iPad claiming to be a
     * Mac gets the same answer either way, which is the whole reason this is safe to sniff for).
     */
    override val usesOptionForWordJump: Boolean = appleAgents.any {
        window.navigator.userAgent.contains(it, ignoreCase = true)
    }
}