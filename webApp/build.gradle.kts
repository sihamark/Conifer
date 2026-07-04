import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // The app is served/launched from this module; the shared library only declares the target.
        outputModuleName = "conifer"
        browser {
            commonWebpackConfig {
                outputFileName = "conifer.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.resources)
            implementation(libs.kotlinx.browser)
            implementation(libs.napier)
        }
    }
}

// The Compose resources accessor (`Res`) is generated and owned by :shared; this module only
// consumes it, so it generates no class of its own.
compose.resources {
    generateResClass = never
}