import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
        // AppDatabaseConstructor is the one expect/actual class left, and Room's compiler is what
        // writes the actual - so the Beta warning it raises is not ours to fix.
        freeCompilerArgs.add("-Xexpect-actual-classes")
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

        // commonTest on the JVM against the stubbed android.jar - no emulator involved. The
        // instrumented counterpart (withDeviceTest) is deliberately left off.
        withHostTest {}
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
        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
                }
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.jetbrains.compose.ui.tooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material3.adaptive)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material.icons)
            implementation(libs.jetbrains.compose.resources)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.androidx.datastore.core)
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
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ksafe)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(libs.androidx.room.testing)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.testcontainers)
            // Drives the composer's key handling for real; the desktop target is the one with a
            // physical keyboard, so it is the one that runs the interaction tests. `ui-test`
            // alone is enough - the tests use `runComposeUiTest`, not the JUnit4 rule.
            implementation(libs.jetbrains.compose.ui.test)
        }
        // BundledSQLiteDriver is only published for the native/JVM/Android targets, so it lives in
        // the per-platform source sets rather than commonMain (the web target uses sqlite-web).
        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.okhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.androidx.sqlite.web)
            // The Web Worker that backs WebWorkerSQLiteDriver is not shipped by androidx; we vendor
            // their example worker as a local npm package (it pulls in @sqlite.org/sqlite-wasm).
            implementation(npm("sqlite-web-worker", project.file("sqlite-web-worker")))
            implementation(libs.ktor.client.js)
        }
    }
}

// Asked on every build, and only ever answered from git itself - see GitCommit for why neither the
// configuration cache nor a task action is where this may be read.
private val gitParameters: GitParameters.() -> Unit = {
    workingDirectory.set(rootProject.layout.projectDirectory)
}
val generateBuildInfo = tasks.register<GenerateBuildInfo>("generateBuildInfo") {
    description = "Generates the BuildInfo object with the version, git commit and build time."
    group = "build"
    packageName.set(AppConfig.namespace)
    versionName.set(AppConfig.versionName)
    versionCode.set(AppConfig.versionCode)
    commit.set(providers.of(GitCommit::class) { parameters(gitParameters) })
    hasLocalChanges.set(providers.of(GitIsModified::class) { parameters(gitParameters) })
    outputDirectory.set(layout.buildDirectory.dir("generated/buildInfo/kotlin"))
}

// commonMain, so every target gets the same BuildInfo. The task's output directory carries the task
// with it, which is what makes each platform's compilation wait for the file to be written.
kotlin.sourceSets.commonMain.configure {
    kotlin.srcDir(generateBuildInfo.flatMap { it.outputDirectory })
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

// Test coverage. Kover, the usual choice for Kotlin Multiplatform, cannot be applied here: it
// insists on the project-level `android` extension, which the new `com.android.kotlin.
// multiplatform.library` plugin does not create. JaCoCo on the jvm target loses nothing that
// matters - the jvm test source set is where all but a handful of the tests live, and it exercises
// commonMain and jvmMain together.
val coverageExclusions = listOf(
    // Generated: Room's DAO/database implementations, the BuildInfo object, the Compose resource
    // accessor, and the lambda holders the Compose compiler emits.
    "**/*_Impl*",
    "**/BuildInfo*",
    "conifer/shared/generated/resources/**",
    "**/ComposableSingletons*",
    // Preview-only composables, which no test drives on purpose.
    "**/*Previews*"
)

tasks.register<JacocoReport>("jvmCoverageReport") {
    description = "Runs the JVM tests and reports their coverage of commonMain and jvmMain."
    group = "verification"
    dependsOn(tasks.named("jvmTest"))

    executionData.from(layout.buildDirectory.file("jacoco/jvmTest.exec"))
    classDirectories.from(
        layout.buildDirectory.dir("classes/kotlin/jvm/main").map {
            fileTree(it) { exclude(coverageExclusions) }
        }
    )
    sourceDirectories.from(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))

    reports {
        html.required.set(true)
        xml.required.set(true)
        // The badge workflow's generator reads the CSV.
        csv.required.set(true)
    }
}
