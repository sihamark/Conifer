package eu.heha.conifer

data object WasmPlatform : Platform {
    override val name: String = "Web"

    /**
     * Web means a browser, and browsers are overwhelmingly used at a keyboard — a phone browser is
     * the exception, and one that costs an icon in the top bar rather than anything that matters.
     */
    override val hasHardwareKeyboard: Boolean = true
}