import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hot.reload)
}

kotlin {
    jvmToolchain(AppConfig.javaVersion)
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }
}

// The Compose resources accessor (`Res`) is generated and owned by :shared; this
// module only consumes it, so it generates no class of its own.
compose.resources {
    generateResClass = never
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.resources)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.napier)
}

compose.desktop {
    application {
        mainClass = "eu.heha.conifer.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            modules("java.sql", "java.instrument", "jdk.unsupported")

            packageName = AppConfig.appName
            packageVersion = AppConfig.versionName
            description = "A simple and delightful note-taking application."
            vendor = "HeHa Foundation"
            copyright = "2025-2026 HeHa Foundation"

            windows {
                iconFile = project.file("desktopIcons/app_icon.ico")
            }
            macOS {
                iconFile = project.file("desktopIcons/app_icon.icns")
                bundleID = AppConfig.namespace
            }
            linux {
                iconFile = project.file("desktopIcons/app_icon.png")
            }
        }
    }
}

tasks.register<CopyDesktopArtifacts>("buildDesktopRelease") {
    description = "Builds the release distributable for the desktop application."
    group = "release"
    intoFolder = rootDir.resolve("releases")
    version = AppConfig.versionName
    artifactName = AppConfig.appName
    appPackage = AppConfig.namespace
    dependsOn("createReleaseDistributable")
}