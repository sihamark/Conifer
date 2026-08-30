[![Coverage](https://raw.githubusercontent.com/sihamark/Conifer/main/.github/badges/jacoco.svg)](https://github.com/sihamark/Conifer/actions/workflows/coverage.yml)
[![Branches](https://raw.githubusercontent.com/sihamark/Conifer/main/.github/badges/branches.svg)](https://github.com/sihamark/Conifer/actions/workflows/coverage.yml)

This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM), and Web (wasmJs).

The modules follow
the [new KMP default structure](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/):
a single `shared` library consumed by per-platform application modules.

* [/shared](./shared/src) is the Kotlin Multiplatform library containing all shared code (including
  the
  Compose Multiplatform UI).
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other
    folders ([androidMain](./shared/src/androidMain/kotlin), [iosMain](./shared/src/iosMain/kotlin),
    [jvmMain](./shared/src/jvmMain/kotlin), [wasmJsMain](./shared/src/wasmJsMain/kotlin)) hold the
    platform-specific implementations of the shared interfaces (`Platform`, `DatabaseInitializer`,
    `PreferencesInitializer`, `ClipboardController`).

* [/androidApp](./androidApp) is the Android application (the `Application`/`Activity` entry point).
* [/desktopApp](./desktopApp) is the Desktop (JVM) application entry point and packaging
  configuration.
* [/iosApp](./iosApp/iosApp) is the iOS application (Xcode project + SwiftUI entry point). Even if
  you’re
  sharing your UI with Compose Multiplatform, you need this entry point for your iOS app.
* [/webApp](./webApp) is the Web (wasmJs) application entry point; it mounts the shared Compose UI
  via `ComposeViewport`.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :androidApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :androidApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :desktopApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :desktopApp:run
  ```

To package a release distributable into `./releases`:

```shell
./gradlew :desktopApp:buildDesktopRelease
```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Build and Run Web (wasmJs) Application

To run the development version of the web app in the browser:

- on macOS/Linux
  ```shell
  ./gradlew :webApp:wasmJsBrowserDevelopmentRun
  ```
- on Windows
  ```shell
  .\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun
  ```

To build the production bundle:

```shell
./gradlew :webApp:wasmJsBrowserDistribution
```

Note: the web app persists its database to the Origin Private File System (OPFS), which requires
cross-origin isolation. The dev server already sends the needed COOP/COEP headers (see
[webApp/webpack.config.d](./webApp/webpack.config.d)); a production host must send the same headers.

### Tests

Tests are written with [kotlin-test](https://kotlinlang.org/api/core/kotlin-test/) and
[kotlinx-coroutines-test](https://github.com/Kotlin/kotlinx.coroutines/tree/master/kotlinx-coroutines-test)
(`runTest`). Shared tests live in [shared/src/commonTest](./shared/src/commonTest/kotlin) and run on
every target; JVM-only tests in [shared/src/jvmTest](./shared/src/jvmTest/kotlin) additionally use
Room’s testing artifact (`room3-testing`, `MigrationTestHelper`) with the bundled SQLite driver to
verify database migrations against the exported schemas in [shared/schemas](./shared/schemas).

Run them with:

```shell
# all targets (JVM, iOS simulator, wasmJs browser)
./gradlew :shared:allTests
# JVM only (fastest; includes the Room migration tests)
./gradlew :shared:jvmTest
# a single test class
./gradlew :shared:jvmTest --tests "*MergePolicyTest"
```

### Releasing

A release is a version tag. Everything the tag sets off is done by GitHub Actions; dating the
changelog, pushing the tag and pressing Publish are what is left by hand.

1. **Check the version.** `versionName` and `versionCode` in
   [buildSrc/src/main/kotlin/AppConfig.kt](./buildSrc/src/main/kotlin/AppConfig.kt) are bumped when
   the work on a version starts, not at release time — so at this point they should already be
   right.

2. **Date the changelog.** The newest heading in [changelog.md](./changelog.md) carries no date
   while
   the version is in progress; add it now, in the same `dd.MM.yyyy` form as the entries below it:

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
   release has always used. What the page leaves
   off it leaves off deliberately: a bundle cannot be installed from a download, the mapping is the
   file that turns the obfuscated build back into readable names, and the debug APK is signed with a
   throwaway key the runner generates on the spot, so it will not install over a build from anywhere
   else.

   Seven repository secrets are needed. `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_PASSWORD` and
   `ANDROID_KEY_ALIAS` sign the Android build — the keystore itself is in the repository, only those
   three are not — and `NEXTCLOUD_URL`, `NEXTCLOUD_USERNAME`, `NEXTCLOUD_PASSWORD` and
   `NEXTCLOUD_REMOTE_FOLDER` reach the upload, the password best being a Nextcloud *app password*
   rather than the account one. Each is written into a git-ignored properties file for the build and
   deleted again afterwards. The remote folder is a secret like the others and required like them:
   `NextcloudConfig` would otherwise fall back to `Conifer/<versionName>`, which is not where these
   releases go, and an upload to the wrong folder succeeds without complaining.

   Not built by the workflow: iOS, which needs an Xcode signing identity of its own, and the web
   bundle.

4. **Publish the release page.** Open the draft under
   [Releases](https://github.com/sihamark/Conifer/releases), read the changelog entry as it renders,
   check the four files are attached, and press Publish.

To try the whole chain out without tagging anything, run the workflow by hand from the Actions tab
(**Release artifacts** → *Run workflow*). It builds everything the same way and assembles the same
page from `AppConfig`'s version, titled *rehearsal* and saying so in the notes. It does not upload:
a rehearsal has no business writing into the folder of a version nobody has released. A draft
creates no tag, so nothing is left behind but the draft itself — delete it when you have seen what
you needed.

#### Building a release by hand

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

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).