# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## What this is

Conifer is a Kotlin Multiplatform + Compose Multiplatform note-taking app targeting **Android, iOS,
and JVM Desktop**. A "Bit" is a single dated note; the whole app is essentially one screen for
adding bits and viewing them grouped by date.

The project follows
the [new KMP default structure](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/):
one `shared` library module consumed by separate per-platform application modules (`androidApp`,
`desktopApp`, and the `iosApp` Xcode project).

The **wasmJs (web)** target is enabled: `shared` declares `wasmJs { browser() }` and the standalone
`webApp` module (Compose `ComposeViewport`) is the browser entry point. Web was previously disabled
because Room 2 couldn't target it; Room 3 (`androidx.room3`) added web support. On web, Room uses
`WebWorkerSQLiteDriver` (`androidx.sqlite:sqlite-web`) backed by the vendored `sqlite-web-worker`
npm
package under `shared/` (the androidx example worker + `@sqlite.org/sqlite-wasm`), persisting to
OPFS.
OPFS requires cross-origin isolation, so the dev server sets COOP/COEP headers via
`webApp/webpack.config.d/` (a production host must send the same headers). Plain JS is still not a
target.

## Build & run

```bash
./gradlew :desktopApp:run                    # run desktop (JVM) app
./gradlew :androidApp:assembleDebug          # build Android APK
./gradlew :shared:compileAndroidMain         # compile the shared library (Android target)
./gradlew :desktopApp:buildDesktopRelease    # package desktop release into ./releases (custom task)
./gradlew :webApp:wasmJsBrowserDevelopmentRun # run the web (wasmJs) app in the browser
./gradlew :webApp:wasmJsBrowserDistribution   # build the production web bundle
# after changing npm deps, refresh the lock with: ./gradlew kotlinWasmUpgradeYarnLock
```

iOS: open `iosApp/` in Xcode and run (the iOS entry point calls `IosConiferApp.initialize()`).

### Tests

```bash
./gradlew :shared:allTests                              # all platforms
./gradlew :shared:jvmTest                               # JVM only
./gradlew :shared:jvmTest --tests "*ComposeAppCommonTest.example"   # single test
```

Common tests live in `shared/src/commonTest`.

## Architecture

**Module layout:** `shared` is the KMP library (common/android/ios/jvm source sets) where
essentially all real code lives, including the Compose UI and the platform-specific implementations
in `androidMain`/`iosMain`/`jvmMain`. Each platform has a thin application module that depends on
`shared` and only holds its entry point: `androidApp` (Android `Application`/`Activity`),
`desktopApp` (a plain `kotlin("jvm")` module with `main.kt` plus the `compose.desktop` packaging
config and the `buildDesktopRelease` task), `iosApp` (Xcode project; the iOS entry calls
`IosConiferApp.initialize()`), and `webApp` (a wasmJs `kotlin.multiplatform` module whose `main.kt`
calls `ConiferApp.initialize(...)` with `WasmDatabaseInitializer` and mounts the UI via
`ComposeViewport`).

**Compose resources across modules:** the generated `Res` accessor is owned by `shared`, with its
package pinned to `conifer.shared.generated.resources` and `publicResClass = true` (in
`shared/build.gradle.kts`) so `desktopApp` can use it for the window icon/title. `desktopApp` sets
`generateResClass = never` since it has no resources of its own. If you add resources, put them
under `shared/src/commonMain/composeResources`.

**Dependency injection without expect/actual.** Per the changelog, this project deliberately avoids
`expect`/`actual` declarations in favor of an initializer pattern. `ConiferApp` (in `commonMain`) is
a singleton holding `Platform`, `DatabaseInitializer`, and `ClipboardController` interfaces. Each
platform's entry point calls `ConiferApp.initialize(...)` passing its concrete implementations (e.g.
`JvmDatabaseInitializer`, `AndroidClipboardController`). The **only** remaining `expect`/`actual` is
`AppDatabaseConstructor`, which Room's compiler generates. When adding platform-specific behavior,
add an interface to `ConiferApp` and a per-platform impl rather than an `expect fun`.

**Data flow:** `BitDao` (Room) → `DatabaseController` (singleton, lazily holds the `AppDatabase`
built by the injected `DatabaseInitializer`) → `BitsRepository` →
`BitsViewModel` → `BitsRoute`/`BitsPane` (Compose UI). The ViewModel exposes a single `state` (
`mutableStateOf`) and collects `getAllBits()` as a Flow, grouping bits by local date.

**The bits screen** is split by region across `ui/bits/`, one file per part of the screen:
`BitsPane` (the multi-pane frame, the main pane and the top bar), `BitsList` (the day-grouped list
and its scroll position), `BitComposer` (date/time chip, day strip, time slider, text field),
`DaySidebar` (the two-pane day list), `BitItem`, `PermissionPrompt`, `CrashReportPrompt` (the banner
offering the previous run's crash for reporting, fed by `log/CrashBreadcrumb`), `BitsPaneContract`
(`BitsPaneState`/`BitsPaneActions`) and `BitsPanePreviews`. Since Kotlin has no package-private,
anything used across those files is `internal`; `BitsPane` and the contract types are the only
public API of the package. `DatedBits` lives next to `BitsViewModel`, which builds it.

`BitsPane` takes two independent layout decisions, both derived from the window size classes and
both overridable so previews/tests can pick one directly: `BitsLayout` arranges the days, bits and
composer (see its KDoc), while `SyncPresentation` (in `ui/SyncPane.kt`) decides whether sync appears
as a popover/sheet over the bits or as a third pane beside them. `SyncUiState.isSyncOpen` only
records that the user asked to see sync; the presentation turns that into a surface.

**Room database** lives in `commonMain/.../model/database`. Schema is at version 2 with an
`AutoMigration` and JSON schemas exported to `shared/schemas`. KSP generates the Room code for
android/ios/jvm targets (see the `ksp*` dependency lines and the `room { schemaDirectory(...) }`
block). If you change an entity, bump `version`, add a migration, and the new schema JSON should be
committed.

## Conventions & config

- **`buildSrc/src/main/kotlin/AppConfig.kt`** is the single source of truth for `versionName`,
  `versionCode`, `minSdk` (30), `targetSdk` (37), `javaVersion` (21), `namespace` (
  `eu.heha.conifer`), and `appName`. Edit there, not in individual build files.
- **`BuildInfo` is generated, not written.** The `generateBuildInfo` task (`buildSrc`, wired up in
  `shared/build.gradle.kts`) writes `eu.heha.conifer.BuildInfo` into `commonMain` at build time with
  the `AppConfig` version, the git commit, whether the tree was modified, and the build time — which
  is how runtime code gets at the version at all. `buildLabel()` formats it for a log or a report.
- Versions/plugins are centralized in `gradle/libs.versions.toml`.
- Opt-ins `kotlin.time.ExperimentalTime` and `kotlin.uuid.ExperimentalUuidApi` are enabled
  project-wide — `Instant`, `Clock`, and `Uuid` from `kotlin.*` are used directly (not the kotlinx
  variants).
- Logging uses **Napier** (`Napier.d/i/e`), initialized via the `antilog` passed into
  `ConiferApp.initialize`. Every call is also mirrored into this run's own log file (
  `log/FileAntilog`,
  a new file per start, ten kept). An uncaught error goes into that file *and* into a small
  `last-crash.json` breadcrumb beside it (`log/CrashBreadcrumb`); the next start reads the
  breadcrumb
  (`CrashReports`) and the bits screen offers that crash for reporting (
  `ui/bits/CrashReportPrompt`).
  Web has neither: no file system, so `logFileInitializer` is null there and both are simply absent.
- Gradle configuration cache and build cache are enabled.