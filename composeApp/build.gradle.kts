import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }

    androidLibrary {
        namespace = AppConfig.namespace + ".core"
        compileSdk = AppConfig.targetSdk
        minSdk = AppConfig.minSdk

        packaging {
            resources.excludes += ("META-INF/MANIFEST.MF")
        }

        compilerOptions {
            val javaVersion = AppConfig.javaVersion
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
        androidResources.enable = true
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ConiferApp"
            isStatic = true
        }
    }

    jvm()

//    js {
//        browser()
//        binaries.executable()
//    }

//    @OptIn(ExperimentalWasmDsl::class)
//    wasmJs {
//        browser()
//        binaries.executable()
//    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material.icons)
            implementation(libs.jetbrains.compose.resources)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.datetime)
            implementation(libs.napier)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
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