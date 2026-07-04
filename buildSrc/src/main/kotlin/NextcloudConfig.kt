import java.io.File
import java.util.Properties

/**
 * Reads the Nextcloud upload configuration from a `nextcloud.properties` file at the project root.
 * The file is git-ignored (see `nextcloud.properties.example` for the expected keys). Returns
 * `null` when the file is absent so the upload task can fail with a helpful message only when it is
 * actually invoked, rather than breaking every build.
 */
data class NextcloudConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val remoteFolder: String
) {
    companion object {
        const val FILE_NAME = "nextcloud.properties"

        fun loadOrNull(rootDir: File): NextcloudConfig? {
            val file = rootDir.resolve(FILE_NAME)
            if (!file.exists()) return null
            val props = Properties().apply { file.inputStream().use { load(it) } }
            fun required(key: String) = props.getProperty(key)?.takeIf { it.isNotBlank() }
                ?: error("Missing '$key' in $FILE_NAME")
            return NextcloudConfig(
                serverUrl = required("nextcloud.url"),
                username = required("nextcloud.username"),
                password = required("nextcloud.password"),
                remoteFolder = props.getProperty("nextcloud.remoteFolder")
                    ?.takeIf { it.isNotBlank() }
                    ?: "${AppConfig.appName}/${AppConfig.versionName}"
            )
        }
    }
}