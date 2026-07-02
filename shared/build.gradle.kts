import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }

    android {
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

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.jetbrains.compose.ui.tooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.sqlite.bundled)
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
            // execSQL is not available to common code spanning web + non-web targets;
            // the async executeSQL from sqlite-async is the common replacement.
            implementation(libs.androidx.sqlite.async)
            implementation(libs.kotlinx.datetime)
            // api: KoinComponent is a public supertype of NotificationController (androidMain),
            // which the :androidApp module instantiates, so koin must be on its classpath.
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.napier)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // BundledSQLiteDriver is only published for the native/JVM/Android targets, so it lives in
        // the per-platform source sets rather than commonMain (the web target uses sqlite-web).
        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.androidx.sqlite.bundled)
        }
        wasmJsMain.dependencies {
            implementation(libs.androidx.sqlite.web)
            // The Web Worker that backs WebWorkerSQLiteDriver is not shipped by androidx; we vendor
            // their example worker as a local npm package (it pulls in @sqlite.org/sqlite-wasm).
            implementation(npm("sqlite-web-worker", project.file("sqlite-web-worker")))
        }
    }
}

// The generated Compose resources accessor (`Res`) is consumed by the standalone
// :desktopApp module, so it must be public. The package is pinned so it stays stable
// regardless of the module name.
compose.resources {
    publicResClass = true
    packageOfResClass = "conifer.shared.generated.resources"
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspWasmJs", libs.androidx.room.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}