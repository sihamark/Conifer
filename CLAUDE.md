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

## CI

Three workflows in `.github/workflows`, all also runnable by hand (`workflow_dispatch`):

- **`tests.yml`** — on pushes to `main` and PRs against it. Three jobs: the browser tests
  (`wasmJsBrowserTest`), the Android host tests (`testAndroidHostTest`, `commonTest` against the
  stubbed `android.jar` — no emulator) and the iOS simulator tests (`iosSimulatorArm64Test`, the
  only iOS target with a test task). The JVM suite is deliberately *not* repeated here; coverage.yml
  already runs it.
- **`coverage.yml`** — same triggers, runs `:shared:jvmCoverageReport` under `xvfb-run` (the Compose
  desktop UI tests need a display). Report-only: no coverage number fails a build.
- **`release.yml`** — on a pushed bare three-part version tag (`1.2.8`, no `v`). Builds the desktop
  distributable on a macOS, a Windows and a Linux runner (no cross-compiling — one per OS) and the
  signed Android APK, AAB, mapping and debug APK on a fourth, then a `release` job assembles a
  **draft** GitHub release and uploads everything to Nextcloud. See "Releasing" in the readme for
  the process around it.

Things that will bite when changing these:

- **The badges are published to a `badges` branch**, not committed beside the code: `main` takes
  changes only through a pull request, so a run cannot push to it. The branch holds nothing but
  `jacoco.svg` and `branches.svg`, built with git plumbing (`hash-object`/`mktree`/`commit-tree`) so
  the job never leaves `main`. `.github/badges/` is git-ignored, and the readme's badge URLs read
  from that branch.
- **All four test jobs write a run summary** through `.github/scripts/test-summary.py`, which sums
  the JUnit XML under `shared/build/test-results/<task>/` and names what failed. Every such step is
  `if: always()` — a summary that only appeared on success would be missing when it matters — and
  the script always exits 0, since the Gradle step has already failed the job.
- **A release tag must equal `AppConfig.versionName`.** Nothing carries the tag into the build (the
  artifacts are named from `AppConfig`), so a mismatch would put `Conifer.1.2.8.*` files on a page
  titled something else; the workflow checks and stops.
- **A release needs its changelog entry.** The release notes are cut out of `changelog.md` by its
  `## Version <version>` heading; a version with no entry fails the job rather than publishing a
  page with nothing written on it.
- **`workflow_dispatch` on release.yml is a rehearsal**: same builds, same page marked as such, and
  no upload — a rehearsal has no business writing into the folder of an unreleased version.
- **Seven repository secrets**, no repository variables: three `ANDROID_*` for signing and four
  `NEXTCLOUD_*` for the upload. Each is written into a git-ignored properties file for the build and
  deleted afterwards. All four Nextcloud values stay masked in what a run prints — including the
  folder path in its percent-encoded spelling, which substring redaction would otherwise miss in the
  URL a failed upload reports.

## Architecture

**Module layout:** `shared` is the KMP library (common/android/ios/jvm/wasmJs source sets) where
essentially all real code lives, including the Compose UI and the platform-specific implementations
in `androidMain`/`iosMain`/`jvmMain`/`wasmJsMain`. Each platform has a thin application module that
depends on `shared` and only holds its entry point: `androidApp` (Android `Application`/`Activity`),
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

**Dependency injection: the initializer pattern, then Koin.** This project deliberately avoids
`expect`/`actual`; the **only** one left is `AppDatabaseConstructor`, which Room's compiler
generates. Everything else platform-specific is an interface in `commonMain` (`Dependencies.kt`) —
`Platform`, `DateTimeFormats`, `DatabaseInitializer`, `PreferencesInitializer`,
`CredentialsInitializer`, `BrowserOpener`, plus the optional `ClipboardController`,
`ReportShareController`, `LogFileInitializer`, `UncaughtErrorInitializer`, `AppPresenceInitializer`
— with one implementation per source set (`JvmDatabaseInitializer`, `AndroidClipboardController`,
…).

Each entry point calls `ConiferApp.initialize(...)` with its own implementations. That function
starts this run's log file, reads how the previous run ended, installs the uncaught-error and
app-presence handlers, and *then* builds the graph:
`startKoin { modules(coreModule, platformModule(...)) }` (`di/DependencyModules.kt`).

- **`platformModule(...)`** binds exactly what was handed to `initialize`. An optional
  implementation that is null is simply left unbound, so `getOrNull()` resolves it to null — that
  is how "this platform has no clipboard / nowhere to share a report" reaches the screen. It also
  takes two things that are *findings* rather than platform implementations, decided before the
  graph exists and unresolvable from inside it: `RunEndReports` (what the last run left behind) and
  `AppPresence`. Both default to the quiet variant a test or preview graph wants.
- **`coreModule`** holds everything platform-agnostic: `DatabaseController`,
  `SyncPrefs`/`DraftPrefs` (built from the injected `PreferencesInitializer`), `Credentials` (from
  `CredentialsInitializer`), `BitsRepository`, `SyncCoordinator`, and
  `BitsViewModel`/`SyncViewModel`.
- **Watch the constructor shortcuts.** `singleOf`/`viewModelOf` resolve *every* constructor
  parameter through `get()`, ignoring Kotlin's own defaults and failing outright on an optional
  dependency. That is why `SyncCoordinator` (whose `loginFlow` has a default) and both view models
  (whose `clipboardController` is optional) are written out longhand with `getOrNull()`. Read the
  comments there before "tidying" them into `singleOf`.
- **The UI resolves through Koin too:** `BitsRoute` takes `BitsViewModel` and `SyncViewModel` via
  `koinViewModel()` and `Platform` via `koinInject()`. `koin-core` is an `api` dependency of
  `shared` rather than `implementation`, because `NotificationController` (androidMain) is a public
  `KoinComponent` that `:androidApp` instantiates.
- **Not everything is in the graph:** `ConiferApp.isDebug` is a property `initialize` sets, and the
  permission handler is bound by the screen (`BitsViewModel.bindPermissionHandler`) because it does
  not outlive an Android recreation. Anything running without `initialize` — a preview, a test —
  therefore still gets sensible answers.

When adding platform-specific behavior: add an interface in `commonMain`, implement it in each
source set, add a parameter to `ConiferApp.initialize` and bind it in `platformModule` — never an
`expect fun`. If the screen looks it up by type at runtime rather than through a constructor,
add it to `jvmTest/.../di/PlatformModuleTest`, which resolves exactly those lookups (today
`Platform` and `DateTimeFormats`) — a missing binding is otherwise a crash on someone else's
first screen rather than a compile error.

**Data flow:** `BitDao` (Room) → `DatabaseController` (singleton, lazily holds the `AppDatabase`
built by the injected `DatabaseInitializer`) → `BitsRepository` →
`BitsViewModel` → `BitsRoute`/`BitsPane` (Compose UI). The ViewModel exposes a single `state` (
`mutableStateOf`) and collects `getAllBits()` as a Flow, grouping bits by local date.

**The bits screen** is split by region across `ui/bits/`, one file per part of the screen:
`BitsPane` (the multi-pane frame, the main pane and the top bar), `BitsList` (the day-grouped list
and its scroll position), `BitComposer` (date/time chip, day strip, time slider, text field),
`DaySidebar` (the two-pane day list), `BitItem`, `TimeOfDayPicker` (the exact-time dialog behind the
slider), `KeyboardShortcuts` (`handleShortcut` and the overlay listing `SHORTCUT_GROUPS`, so a key
wired up and never documented shows), `EmojiFallScene` (the empty state's and the beginning marker's
ambient scenes), `PermissionPrompt`, `RunEndPrompt` (the banner offering the previous run's bad
ending for reporting, fed by `log/RunEndReports`), `BitsPaneContract`
(`BitsPaneState`/`BitsPaneActions`) and `BitsPanePreviews`. Since Kotlin has no package-private,
anything used across those files is `internal`; `BitsPane` and the contract types are the only
public API of the package. `DatedBits` lives next to `BitsViewModel`, which builds it.

`BitsPane` takes two independent layout decisions, both derived from the window size classes and
both overridable so previews/tests can pick one directly: `BitsLayout` arranges the days, bits and
composer (see its KDoc), while `SyncPresentation` (in `ui/SyncPane.kt`) decides whether sync appears
as a popover/sheet over the bits or as a third pane beside them. `SyncUiState.isSyncOpen` only
records that the user asked to see sync; the presentation turns that into a surface.

**Room database** lives in `commonMain/.../model/database`. Schema is at version 4, reached by three
`AutoMigration`s with specs in `Migrations.kt` (1→2 dropped `concerned_at` and seeded `date` from
`created_at`; 2→3 turned `date` into a zoneless `LocalDateTime`; 3→4 added the sync bookkeeping
columns and the three sync-only tables), with the JSON schemas for every version exported to
`shared/schemas` and committed. Four entities: `Bit` plus `BucketState`/`ReadableState`/
`ReadablePending`, which belong to sync alone — `BitDao` is what the UI uses, `SyncDao` is not.
KSP generates the Room code for the android/ios/jvm/wasmJs targets (see the `ksp*` dependency lines
and the `room3 { schemaDirectory(...) }` block). If you change an entity, bump `version`, add a
migration, and commit the new schema JSON. `jvmTest/.../MigrationTest` runs the migrations against
those exported schemas.

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
  `ConiferApp.initialize`. Every call is also mirrored into this run's own log file
  (`log/FileAntilog`, a new file per start, ten kept).
- **How a run ended is reported at the next start** (`log/LastRun.kt`). Every start writes a
  `last-run.json` record beside the logs (`LastRunStore`) naming the log file it is writing. A crash
  adds a `CrashBreadcrumb` to that record on the crashing thread; an ordinary ending instead writes
  `--- log closed ---` into the log (`LogClosingInitializer` — a JVM shutdown hook, Android/iOS
  going to the background, `pagehide` on web). So the next start classifies the previous run as
  `LastRunEnd.Crashed`, `Vanished` (log stops mid-sentence: killed for memory, a signal, a native
  crash below Kotlin) or nothing at all, and `ui/bits/RunEndPrompt` offers the bad ones for
  reporting.
- What it offers is `log/RunEndReport`: the summary plus the tail of that run's log, read back
  through `LogTailReader` (which `LogFileInitializer` implements — always by *file name*, never a
  path, see `logTailFileName`) and handed to the clipboard or to the platform's
  `ReportShareController` — share sheet on Android/iOS, a folder in the file manager on desktop, a
  download on web. All four targets have the whole chain; web keeps its logs and record in
  `localStorage` (`WasmLogFileInitializer`, fewer and smaller — see its KDoc).
- **Where the desktop app keeps its data** (`shared/src/jvmMain/.../JvmDataFolder.kt`): one per-user
  folder outside the installed program — `~/Library/Application Support/Conifer`,
  `%LOCALAPPDATA%\Conifer`, `$XDG_DATA_HOME/conifer` — holding the database, the preferences,
  `logs/`
  and `reports/`. Never derive it from the jar's location again: that put it inside `Conifer.app`,
  where an update deleted it. A Gradle `run` gets a `-dev` folder of its own, and
  `-Dconifer.dataFolder=` points it anywhere.
- Gradle configuration cache and build cache are enabled.