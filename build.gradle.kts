plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.multiplatform.library) apply false

    alias(libs.plugins.compose.hot.reload) apply false
    alias(libs.plugins.compose.multiplatform) apply false

    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false

    alias(libs.plugins.androidx.room) apply false

    alias(libs.plugins.ksp) apply false
}

// Uploads everything collected under ./releases to Nextcloud over WebDAV. Configure credentials
// and the target folder in a git-ignored `nextcloud.properties` file (see the .example).
tasks.register<UploadToNextcloud>("uploadReleasesToNextcloud") {
    description = "Uploads the built release artifacts from ./releases to Nextcloud."
    group = "release"
    sourceFolder = layout.projectDirectory.dir("releases")

    val config = NextcloudConfig.loadOrNull(rootDir)
    // Defaults keep the @Input validation happy so the doFirst check produces the friendly error.
    serverUrl.convention(config?.serverUrl ?: "")
    username.convention(config?.username ?: "")
    password.convention(config?.password ?: "")
    remoteFolder.convention(config?.remoteFolder ?: "")

    doFirst {
        if (config == null) {
            throw GradleException(
                "Missing ${NextcloudConfig.FILE_NAME}. Copy ${NextcloudConfig.FILE_NAME}.example " +
                        "to ${NextcloudConfig.FILE_NAME} and fill in your Nextcloud credentials."
            )
        }
    }
}