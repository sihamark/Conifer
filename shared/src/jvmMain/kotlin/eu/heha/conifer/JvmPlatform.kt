package eu.heha.conifer

data object JvmPlatform : Platform {
    /**
     * The OS comes first because that is what distinguishes one desktop from another in
     * Nextcloud's device list (see [eu.heha.conifer.net.coniferUserAgent]); the JVM version is
     * only of interest once a bug turns out to be runtime-specific.
     */
    override val name: String = "${osName()}, Java ${property("java.version")}"
}

private fun osName(): String = "${property("os.name")} ${property("os.version")}"

/** JVM system properties are nullable in theory; an unknown value must not become "null". */
private fun property(key: String): String = System.getProperty(key) ?: "unknown"
