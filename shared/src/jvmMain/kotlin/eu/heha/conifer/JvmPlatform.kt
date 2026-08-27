package eu.heha.conifer

data object JvmPlatform : Platform {
    /**
     * The OS comes first because that is what distinguishes one desktop from another in
     * Nextcloud's device list (see [eu.heha.conifer.net.coniferUserAgent]); the JVM version is
     * only of interest once a bug turns out to be runtime-specific.
     */
    override val name: String = "${osName()} ${osVersion()}, Java ${javaVersion()}"

    /** A desktop window is being looked at over a keyboard, without exception. */
    override val hasHardwareKeyboard: Boolean = true

    /** macOS is the one desktop whose text editing jumps words with ⌥ rather than Ctrl. */
    override val usesOptionForWordJump: Boolean = isMacOs()

    /**
     * Whether this is a Mac — matched loosely because the JVM still reports "Mac OS X" on a system
     * that has been called macOS for years, and either spelling should answer the same. No other
     * desktop has "mac" in its name.
     */
    private fun isMacOs(): Boolean = osName().contains("mac", ignoreCase = true)

    /** What the OS calls itself: "Mac OS X", "Windows 11", "Linux". */
    private fun osName(): String = property("os.name")
    private fun osVersion(): String = property("os.version")
    private fun javaVersion(): String = property("java.version")

    /** These properties are nullable in theory, and an absent one must not read as "null". */
    private fun property(key: String): String = System.getProperty(key) ?: "unknown"
}
