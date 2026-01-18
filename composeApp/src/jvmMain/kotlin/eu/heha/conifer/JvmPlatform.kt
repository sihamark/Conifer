package eu.heha.conifer

data object JvmPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}