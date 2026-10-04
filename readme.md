[![Coverage](https://raw.githubusercontent.com/sihamark/Conifer/badges/jacoco.svg)](https://github.com/sihamark/Conifer/actions/workflows/coverage.yml)
[![Branches](https://raw.githubusercontent.com/sihamark/Conifer/badges/branches.svg)](https://github.com/sihamark/Conifer/actions/workflows/coverage.yml)

# Conifer

Conifer is a note-taking app that is one screen. A **Bit** is a short note with a date and a time:
you write it, it lands under its day, and that is the whole of it. Everything stays on the device it
was written on unless you connect a Nextcloud account of your own — and then it is your server the
bits travel through and nobody else's.

It is a Kotlin Multiplatform / Compose Multiplatform project. One `shared` module holds the logic
*and* the user interface; Android, iOS, the desktop (JVM) and the browser (wasmJs) each add little
more than an entry point.

---

## What it can do

**Write a bit.** Type into the field, press the check (or Enter), and the bit is stored with a date
and a time — by default the current ones, ticking along with the clock until you pick something
else. Bits can be edited in place and deleted with a confirmation. Half a sentence you have typed
but not added is written down half a second after you stop typing and put back the next time the
screen opens, together with the date and time you had chosen and any edit you were in the middle
of.

**Read them by day.** Bits are grouped per calendar day under sticky headers, newest first, each
card showing only its time. A day strip in the composer and — where the window has the room — a
sidebar beside the list let you filter to a single day; each day carries one to three dots for how
much was written on it. Both lists start at 30 days and reach as far back as they are scrolled. A 🌱
marks where your oldest bit is, and the empty state and that marker each grow a small emoji scene
with the occasional animal walking through it.

**Set the date and time by hand.** A chip opens the day strip, a slider moves the time in quarter
hours, and the time beside it opens the system's own clock picker for the minute the slider cannot
reach. What the list is filtered to and what the composer will stamp on the bit are two separate
things, so an edit never throws the list about.

**Use the keyboard.** Every shortcut lives in one place and works whether or not the text field has
the cursor: the arrows with Alt (⌃⌥ on Apple's keyboards, where ⌥ belongs to the text field) move
the bit's day and time, Shift skips to the nearest day that has bits, and single letters — T for
today, N for now, 0 for every day — do the things Home and End would do on a keyboard that has
them. `Alt+H` or `F1` shows the whole list; `Esc` backs out of whatever is open. The button that
opens that list appears only where a keyboard exists to use it.

**Read dates in your own language.** Times, weekdays, day-and-month labels and whole dates are
spelled by the platform — `java.time` on the desktop, ICU on Android, `NSDateFormatter` on iOS,
`Intl.DateTimeFormat` in the browser. What is stored is deliberately not localized: the database,
the sync files and the logs stay ISO.

**Copy a day.** The copy icon in a date header puts that day on the clipboard as plain text, one
line per bit.

**Sync through your own Nextcloud — optionally.** Sync is off until you connect an account, and
nothing leaves the device before you do. Signing in goes through Nextcloud's Login Flow v2 in your
browser, so the app only ever holds an app-scoped token, kept encrypted with a key from the
device's own secure store (and it says so plainly when a platform has none). From then on it syncs
when the app comes to the front, every five minutes while you are in it, and ten seconds after an
edit settles. Conflicts resolve last-write-wins with a deterministic tiebreak; deleted bits become
tombstones that are collected 90 days later. Beside the machine data — one JSON file per bit in
monthly buckets under a hidden `.sync/` folder — it writes one Markdown file per day, so opening
the folder in Nextcloud's web UI shows something a person can read. The app bar's cloud icon shows
the current state and, expanded, the troubleshooting details a bug report would want: device id,
app root, last sync, root ETag, last error, last tally.

**Tell you when the last run ended badly.** Every start writes its own log file and a record of the
run. A crash is written into that record as it happens; an ordinary ending writes `--- log closed
---`. So the next start can say whether the last run crashed, or simply vanished (killed for
memory, a signal, a native crash below Kotlin), and offer that run's summary and the tail of its
log — never anything you wrote — to the share sheet, a folder, a download or the clipboard.

**Add a bit from a notification (Android).** A conversation notification with inline reply creates
a bit with the app closed, and keeps the last three bits as its history.

## What it can't do

- **No search, no tags, no folders, no notebooks.** Bits are found by scrolling to their day, and
  that is the only way.
- **No attachments** — no images, no files, no links treated as anything but text.
- **No formatting.** Markdown is neither rendered nor styled in the app; a bit is plain text. (The
  Markdown files sync writes to the server are generated *from* bits, never read back.)
- **No settings screen.** Sync is the only thing that can be configured; there is no theme picker,
  no font size, no data folder chooser.
- **No reminders, no alarms, no scheduled anything.** The Android notification exists to write a
  bit, not to nag you about one.
- **No sharing between people, no accounts of Conifer's own.** Sync is one Nextcloud account and
  one folder, for the devices of one person; there is no multi-user story and no conflict dialog —
  the newest write wins.
- **No end-to-end encryption of what lands on the server.** The bits are stored as plain JSON and
  Markdown in your own Nextcloud; the credentials on the device are encrypted, the content on the
  server is not.
- **No import, and no export beyond copying a day to the clipboard** (and the Markdown rendering
  sync uploads, which is a one-way view, not a channel back in).
- **No undo.** A deleted bit is gone, which is why deleting asks first.
- **No web clipboard and no notifications outside Android** — see the table below for what each
  platform is missing.

---

## Where each target stands

| Target            | State                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Desktop (JVM)** | **In daily use.** Everything works: window with its own icon, AWT clipboard, sync with the key in the OS secret store, logs and reports in a per-user data folder. Built and released for macOS, Windows and Linux.                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| **Android**       | **In daily use.** Everything works, plus the quick-add notification with inline reply. Built, signed and released as APK and AAB.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **iOS**           | **Functional, but not actually used.** The whole chain exists — Compose in a `UIViewController`, `UIPasteboard`, the database in the documents directory, sync, share sheet, crash reporting — and the simulator test suite runs on every push, but nobody uses the app day to day and no build of it is released: signing is not set up and CI does not build one. Take it as unverified rather than as tested.                                                                                                                                                                                                                                                                    |
| **Web (wasmJs)**  | **Technically functional, and needs care.** It runs, stores bits and syncs, but: the page must be cross-origin isolated (`COOP`/`COEP`) or Room's OPFS storage does not work at all; logs, preferences and credentials share the origin's `localStorage`, which is a few megabytes for all three, so fewer and shorter logs are kept; there is no clipboard and no notifications; and the key protecting the sync credentials is the browser's to keep (a non-extractable WebCrypto key) rather than an OS secret store's, with the sheet saying so before connecting if custody falls back further than that. Nothing is deployed anywhere — the web app is built and run locally. |

---

## Architecture

### Modules

The project follows the [new KMP default
structure](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/): one shared library
consumed by per-platform application modules.

* [`/shared`](./shared/src) — the Kotlin Multiplatform library, where essentially all of the code
  lives, the Compose UI included.
    - [`commonMain`](./shared/src/commonMain/kotlin) — everything that is not platform-specific.
    - [`androidMain`](./shared/src/androidMain/kotlin),
      [`iosMain`](./shared/src/iosMain/kotlin), [`jvmMain`](./shared/src/jvmMain/kotlin),
      [`wasmJsMain`](./shared/src/wasmJsMain/kotlin) — the implementations of the interfaces common
      code asks for: `Platform`, `DateTimeFormats`, `DatabaseInitializer`, `PreferencesInitializer`,
      `CredentialsInitializer`, `BrowserOpener`, `ClipboardController`, `ReportShareController`,
      `LogFileInitializer`, `UncaughtErrorInitializer`, `AppPresenceInitializer`.
* [`/androidApp`](./androidApp) — the Android `Application`/`Activity`.
* [`/desktopApp`](./desktopApp) — a plain `kotlin("jvm")` module: `main.kt`, the
  `compose.desktop` packaging configuration and the `buildDesktopRelease` task.
* [`/iosApp`](./iosApp/iosApp) — the Xcode project; its entry point calls
  `IosConiferApp.initialize()`.
* [`/webApp`](./webApp) — a wasmJs module whose `main.kt` mounts the shared UI with
  `ComposeViewport`.
* [`/buildSrc`](./buildSrc) — [`AppConfig`](./buildSrc/src/main/kotlin/AppConfig.kt) (the single
  source of truth for version, SDK levels, Java version, namespace and app name), the `BuildInfo`
  generator, the desktop release copier and the Nextcloud uploader.

### How a platform hands itself in

There is exactly one `expect`/`actual` declaration in the project — `AppDatabaseConstructor`, which
Room's compiler writes itself. Everything else platform-specific is an interface in `commonMain`
with an implementation per source set, passed to `ConiferApp.initialize(...)` by each entry point
and wrapped into a Koin module there
([
`di/DependencyModules.kt`](./shared/src/commonMain/kotlin/eu/heha/conifer/di/DependencyModules.kt)).
Optional ones — a clipboard, a share target — are simply left unbound, and the screen asks whether
they are there. Adding platform behaviour means adding an interface and four implementations, not
an `expect fun`.

### The way data moves

`BitDao` (Room) → `DatabaseController` → `BitsRepository` → `BitsViewModel` → `BitsRoute` →
`BitsPane`. The view models expose a single `state` (`mutableStateOf`) and collect the DAO's Flow;
`BitsViewModel` groups bits into `DatedBits` per day, `SyncViewModel` wraps `SyncCoordinator` and
owns the sync surface's visibility and its triggers.

### The screen

[`ui/bits/`](./shared/src/commonMain/kotlin/eu/heha/conifer/ui/bits) holds one file per region of
the screen — `BitsPane` (the frame and top bar), `BitsList`, `BitComposer`, `DaySidebar`, `BitItem`,
`KeyboardShortcuts`, `RunEndPrompt`, `PermissionPrompt`, `TimeOfDayPicker`, `EmojiFallScene`, the
`BitsPaneContract` types and the previews. Kotlin has no package-private, so anything shared across
those files is `internal`; `BitsPane` and the contract are the package's only public API.

`BitsPane` takes two layout decisions from the window size classes, both overridable so previews
and tests can pick one: `BitsLayout` (`Stacked`, `DaySidebar`, `SideComposer`) arranges days, bits
and composer, and `SyncPresentation` decides whether sync appears as a popover, a sheet or a third
pane.

### Database

Room 3 (`androidx.room3`) in
[`model/database`](./shared/src/commonMain/kotlin/eu/heha/conifer/model/database), schema version 4,
auto-migrations, JSON schemas exported to [`shared/schemas`](./shared/schemas) and committed. KSP
generates the Room code for the android, ios, jvm and wasmJs targets. `bits` carries what the UI
uses plus the sync bookkeeping the UI never touches; three further tables belong to sync alone.
Change an entity and you bump the version, add a migration and commit the new schema JSON.

### Sync

[`sync/`](./shared/src/commonMain/kotlin/eu/heha/conifer/sync) and
[`auth/`](./shared/src/commonMain/kotlin/eu/heha/conifer/auth) implement
[`docs/nexcloud_sync_spec.md`](./docs/nexcloud_sync_spec.md) against a stock Nextcloud over WebDAV,
with no server-side code of any kind: `KtorWebDavStore` (the transport), `SyncEngine` (fast path,
pull, push, GC, finalize), `MergePolicy` (last-write-wins), `ReadableRenderer` (the per-day Markdown
view), `GarbageCollector`, `LoginFlowV2` and `Credentials` (KSafe-encrypted app password).
`SyncCoordinator` is what the UI talks to.

### Logging and what a run leaves behind

Napier, mirrored into a per-start log file
([`log/`](./shared/src/commonMain/kotlin/eu/heha/conifer/log)), ten kept. `LastRunStore` writes a
record naming that log; a crash adds a breadcrumb to it, an ordinary ending closes the log with a
line saying so, and the next start turns the difference into `Crashed`, `Vanished` or nothing at
all. `RunEndReport` is the summary plus the tail of that log, which is what the prompt offers to
share. On the web all of this lives in `localStorage` instead of in files.

### Build configuration

Versions and plugins are centralized in
[`gradle/libs.versions.toml`](./gradle/libs.versions.toml); everything about the app's identity is
in `AppConfig`. `BuildInfo` — version, git commit, whether the tree was dirty, build time — is
*generated* into `commonMain` at build time by the `generateBuildInfo` task, which is how runtime
code gets at the version at all; `buildLabel()` formats it for a log or a report. The opt-ins
`kotlin.time.ExperimentalTime` and `kotlin.uuid.ExperimentalUuidApi` are on project-wide, so
`Instant`, `Clock` and `Uuid` come from `kotlin.*`. Gradle's configuration cache and build cache are
enabled.

---

## Building and running

Every target has a run configuration in the IDE's run widget; the commands below are the same thing
from a terminal. On Windows use `.\gradlew.bat` in place of `./gradlew`.

### Desktop (JVM)

```shell
./gradlew :desktopApp:run                 # run it
./gradlew :desktopApp:hotRun              # run it with Compose hot reload
./gradlew :desktopApp:buildDesktopRelease # package a release distributable into ./releases
```

A Gradle `run` is marked as a debug launch and gets a data folder of its own (`Conifer-dev`), so it
never touches the data of an installed copy. `-Dconifer.dataFolder=` points it anywhere else.

### Android

```shell
./gradlew :androidApp:assembleDebug
```

### iOS

Open [`iosApp/`](./iosApp) in Xcode and run, or use the `iosApp` run configuration. Nothing else is
needed — the shared framework is built by Gradle as part of it.

### Web (wasmJs)

```shell
./gradlew :webApp:wasmJsBrowserDevelopmentRun # dev server, opens the browser
./gradlew :webApp:wasmJsBrowserDistribution   # production bundle
./gradlew kotlinWasmUpgradeYarnLock           # after changing npm dependencies
```

The database goes to the Origin Private File System, which requires the page to be cross-origin
isolated. The dev server sends the two headers that make it so (see
[webApp/webpack.config.d](./webApp/webpack.config.d)); **a production host must send them too**, or
the app has nowhere to store anything. Room reaches OPFS through `androidx.sqlite:sqlite-web` and
the SQLite web worker vendored under [`shared/sqlite-web-worker`](./shared/sqlite-web-worker) —
androidx's own example worker plus `@sqlite.org/sqlite-wasm`, since androidx ships no worker of its
own.

---

## Tests

Tests are written with [kotlin-test](https://kotlinlang.org/api/core/kotlin-test/) and
[kotlinx-coroutines-test](https://github.com/Kotlin/kotlinx.coroutines/tree/master/kotlinx-coroutines-test)
(`runTest`); Ktor's `MockEngine` stands in for the network.

- [`shared/src/commonTest`](./shared/src/commonTest/kotlin) — runs on **every** target: the merge
  policy, the Markdown renderer, the WebDAV multistatus parser, the login flow, the preferences,
  the whole log/last-run/report chain.
- [`shared/src/jvmTest`](./shared/src/jvmTest/kotlin) — where most of the tests are, because it is
  where the most can be run: the Room migration tests (`room3-testing`'s `MigrationTestHelper`
  against the exported schemas), the sync engine and garbage collector, the view models, and the
  Compose UI tests that drive the composer's keys, the list's scrolling and the day lists for real
  (`runComposeUiTest` — these need a display, which is why CI runs them under `xvfb-run`).
- [`shared/src/iosTest`](./shared/src/iosTest/kotlin) and
  [`shared/src/wasmJsTest`](./shared/src/wasmJsTest/kotlin) — the platform's own date formatting,
  and on the web the `localStorage`-backed log files.
- [
  `KtorWebDavStoreIntegrationTest`](./shared/src/jvmTest/kotlin/eu/heha/conifer/sync/KtorWebDavStoreIntegrationTest.kt)
  runs the WebDAV transport against a **real Nextcloud in a container** (Testcontainers). Without a
  reachable Docker daemon each of its tests logs a notice and returns early, so `jvmTest` still
  passes on a machine that has no Docker running.

```shell
./gradlew :shared:allTests                  # every target
./gradlew :shared:jvmTest                   # JVM only — fastest, and the biggest suite
./gradlew :shared:testAndroidHostTest       # commonTest against the stubbed android.jar, no emulator
./gradlew :shared:iosSimulatorArm64Test     # iOS simulator (macOS only)
./gradlew :shared:wasmJsBrowserTest         # headless Firefox, via Karma
./gradlew :shared:jvmTest --tests "*MergePolicyTest"   # a single class
./gradlew :shared:jvmCoverageReport         # JVM tests + HTML/XML/CSV coverage report
```

Coverage is JaCoCo on the jvm target rather than Kover: Kover insists on the project-level `android`
extension, which the new KMP library plugin never creates. It loses little — the jvm test source
set exercises `commonMain` and `jvmMain` together — and it is report-only. Generated code (Room
implementations, `BuildInfo`, the Compose resource accessor, composable singletons, previews) is
excluded from the count.

---

## CI

Three workflows in [`.github/workflows`](./.github/workflows), each also runnable by hand from the
Actions tab (`workflow_dispatch`).

| Workflow                                           | When                         | What it does                                                                                                                                                                                       |
|----------------------------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`tests.yml`](./.github/workflows/tests.yml)       | push to `main`, PR to `main` | Three jobs, one per target JaCoCo cannot see: the browser tests (headless Firefox), the Android host tests, the iOS simulator tests (`iosSimulatorArm64` is the only iOS target with a test task). |
| [`coverage.yml`](./.github/workflows/coverage.yml) | push to `main`, PR to `main` | `:shared:jvmCoverageReport` under `xvfb-run` — the Compose desktop UI tests want a display — then refreshes the two badges at the top of this file.                                                |
| [`release.yml`](./.github/workflows/release.yml)   | a pushed version tag         | Builds every release artifact, assembles a draft release page and uploads the lot to Nextcloud. See [Releasing](#releasing).                                                                       |

The JVM suite is deliberately not repeated in `tests.yml`: `coverage.yml` already runs it on the
same triggers. The Android job *does* repeat `commonTest`, on purpose — it runs against the stubbed
`android.jar`, which is what catches common code reaching for a JVM API that Android has not.

**Every test job says how it went on the run's own summary page.**
[`.github/scripts/test-summary.py`](./.github/scripts/test-summary.py) sums the JUnit XML Gradle
leaves behind, names what failed and with what message, and the JVM job prints the coverage number
beside its count. Those steps run `if: always()` — a summary that appeared only on success would be
missing exactly when it is wanted — and the script always exits 0, since the step that ran the tests
has already failed the job. Failed runs keep their test results as artifacts for 14 days.

**The badges live on a branch of their own.** `main` takes changes only through a pull request, so a
run cannot commit to it; the coverage job builds a commit out of git's plumbing
(`hash-object`/`mktree`/`commit-tree`) and pushes it to a `badges` branch holding nothing but
`jacoco.svg` and `branches.svg`. `.github/badges/` is git-ignored, and the badge URLs at the top of
this file read from that branch.

**Seven repository secrets, no repository variables.** `ANDROID_STORE_PASSWORD`,
`ANDROID_KEY_PASSWORD` and `ANDROID_KEY_ALIAS` sign the Android build — the keystore itself is in
the repository, only those three are not — and `NEXTCLOUD_URL`, `NEXTCLOUD_USERNAME`,
`NEXTCLOUD_PASSWORD` and `NEXTCLOUD_REMOTE_FOLDER` reach the upload, the password best being a
Nextcloud *app password*. Each is written into a git-ignored properties file for the build and
deleted again afterwards. All four Nextcloud values stay masked in everything a run prints — a run
page on a public repository is readable by anyone — including the folder path in its percent-encoded
spelling, which substring redaction would otherwise miss in the URL a failed upload reports.

Not built by CI: **iOS**, which needs a signing identity of its own, and the **web bundle**, which
is not deployed anywhere.

---

## Releasing

A release is a version tag. Everything the tag sets off is done by GitHub Actions; dating the
changelog, pushing the tag and pressing Publish are what is left by hand.

1. **Check the version.** `versionName` and `versionCode` in
   [buildSrc/src/main/kotlin/AppConfig.kt](./buildSrc/src/main/kotlin/AppConfig.kt) are bumped when
   the work on a version starts, not at release time — so at this point they should already be
   right.

2. **Date the changelog.** The newest heading in [changelog.md](./changelog.md) carries no date
   while the version is in progress; add it now, in the same `dd.MM.yyyy` form as the entries below
   it:

   ```markdown
   ## Version 1.2.7 (28.08.2026)
   ```

3. **Tag and push.** The tag is the bare three-part version, the way every tag in this repository is
   written — no `v` prefix, and it has to equal the `versionName` from step 1. The artifacts are
   named after `AppConfig`, which the tag never reaches, so a tag that disagrees would put
   `Conifer.1.2.7.*` files on a page titled something else; the workflow checks the two and stops if
   they differ.

   ```shell
   git tag 1.2.7
   git push origin 1.2.7
   ```

   Pushing it starts the [Release artifacts](./.github/workflows/release.yml) workflow, which builds
   the desktop distributable on a macOS, a Windows and a Linux runner (there is no cross-compiling —
   each desktop is built on its own machine) and the signed Android APK and AAB, the ProGuard
   mapping and a debug APK on a fourth. Each job leaves its output as a downloadable workflow
   artifact, kept for 90 days.

   A last job then does two things with them. It draws the three desktop zips and the release APK
   into a **draft** release page, taking this version's changelog entry as the release notes — which
   is why step 2 comes before the tag: a version with no `## Version` heading in the changelog fails
   that job rather than producing a page with nothing written on it. And it uploads *everything*,
   the AAB, the mapping and the debug APK included, to the Nextcloud folder the
   `NEXTCLOUD_REMOTE_FOLDER` secret names, through the same `uploadReleasesToNextcloud` task a
   release has always used. What the page leaves off it leaves off deliberately: a bundle cannot be
   installed from a download, the mapping is the file that turns the obfuscated build back into
   readable names, and the debug APK is signed with a throwaway key the runner generates on the
   spot, so it will not install over a build from anywhere else.

4. **Publish the release page.** Open the draft under
   [Releases](https://github.com/sihamark/Conifer/releases), read the changelog entry as it renders,
   check the four files are attached, and press Publish.

To try the whole chain out without tagging anything, run the workflow by hand from the Actions tab
(**Release artifacts** → *Run workflow*). It builds everything the same way and assembles the same
page from `AppConfig`'s version, titled *rehearsal* and saying so in the notes. It does not upload:
a rehearsal has no business writing into the folder of a version nobody has released. A draft
creates no tag, so nothing is left behind but the draft itself — delete it when you have seen what
you needed.

### Building a release by hand

When the CI is not an option, the same artifacts can be produced locally — with the caveat that a
local run only builds the desktop for the machine it runs on:

```shell
./gradlew :desktopApp:buildDesktopRelease :androidApp:prepareAndroidRelease :androidApp:prepareAndroidDebug
```

That fills `./releases` with a `desktop/` and an `android/` folder, reading the signing passwords
from a git-ignored `androidApp/keystore/keystore.properties`. To upload it, copy
[nextcloud.properties.example](./nextcloud.properties.example) to `nextcloud.properties` (also
git-ignored), fill in the server, the account and an app password, and run:

```shell
./gradlew uploadReleasesToNextcloud
```

Everything under `./releases` goes up over WebDAV, mirroring the folder structure, into
`Conifer/<versionName>` unless `nextcloud.remoteFolder` says otherwise. APKs are wrapped in a `.zip`
on the way, because Android browsers refuse to download a raw `.apk`. The folder is git-ignored and
the upload sends *everything* under it, so clear out what an earlier version left behind first.

---

## Further reading

**About this project**

- [changelog.md](./changelog.md) — every version, written for whoever uses the app rather than for
  whoever wrote it.
- [docs/features.md](./docs/features.md) — the screen gone through element by element, with the code
  each part lives in.
- [docs/nexcloud_sync_spec.md](./docs/nexcloud_sync_spec.md) — the sync specification the
  implementation follows: layout on the server, data formats, algorithm, merge rule, garbage
  collection, acceptance criteria.
- [docs/sync_review.md](./docs/sync_review.md) and [docs/mockup_review.md](./docs/mockup_review.md)
  — reviews of the sync implementation and of the original mockup
  ([docs/conifer-mockup.html](./docs/conifer-mockup.html), and
  [docs/sync-guide.html](./docs/sync-guide.html) for the sync surface).
- [CLAUDE.md](./CLAUDE.md) — the same ground as this file, arranged for an agent working in the
  repository.

**The languages and frameworks**

- [Kotlin](https://kotlinlang.org/docs/home.html) ·
  [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) ·
  [Kotlin/Wasm](https://kotl.in/wasm/)
- [Compose Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-getting-started.html)
  · [Material 3](https://m3.material.io/) ·
  [adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive)

**The dependencies** (all versioned in [gradle/libs.versions.toml](./gradle/libs.versions.toml))

| Library                                                                                                                                                                                                             | What it does here                                                  |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| [Room 3](https://developer.android.com/kotlin/multiplatform/room) + [androidx.sqlite](https://developer.android.com/kotlin/multiplatform/sqlite)                                                                    | the database on all four targets, OPFS-backed in the browser       |
| [Koin](https://insert-koin.io/)                                                                                                                                                                                     | dependency injection, including the Compose view models            |
| [Ktor client](https://ktor.io/docs/client-create-new-application.html)                                                                                                                                              | WebDAV and the Nextcloud login flow (OkHttp / Darwin / JS engines) |
| [kotlinx.coroutines](https://kotlinlang.org/docs/coroutines-overview.html), [kotlinx.datetime](https://github.com/Kotlin/kotlinx-datetime), [kotlinx.serialization](https://kotlinlang.org/docs/serialization.html) | concurrency, dates without a time zone, the JSON on the server     |
| [AndroidX DataStore](https://developer.android.com/topic/libraries/architecture/datastore)                                                                                                                          | preferences — sync settings and the unsent draft                   |
| [KSafe](https://github.com/ioannisa/KSafe)                                                                                                                                                                          | the encrypted app password, keyed from the device's secure store   |
| [Napier](https://github.com/AAkira/Napier)                                                                                                                                                                          | logging, mirrored into this run's log file                         |
| [Testcontainers](https://testcontainers.com/)                                                                                                                                                                       | the real Nextcloud the WebDAV tests run against                    |

**Nextcloud, for the sync**

- [Login Flow v2](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html)
  · [WebDAV API](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/index.html)
