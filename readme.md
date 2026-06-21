This is a Kotlin Multiplatform project targeting Android, iOS, and Desktop (JVM).

The modules follow
the [new KMP default structure](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/):
a single `shared` library consumed by per-platform application modules.

* [/shared](./shared/src) is the Kotlin Multiplatform library containing all shared code (including
  the
  Compose Multiplatform UI).
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other
    folders ([androidMain](./shared/src/androidMain/kotlin), [iosMain](./shared/src/iosMain/kotlin),
    [jvmMain](./shared/src/jvmMain/kotlin)) hold the platform-specific implementations of the shared
    interfaces (`Platform`, `DatabaseInitializer`, `ClipboardController`).

* [/androidApp](./androidApp) is the Android application (the `Application`/`Activity` entry point).
* [/desktopApp](./desktopApp) is the Desktop (JVM) application entry point and packaging
  configuration.
* [/iosApp](./iosApp/iosApp) is the iOS application (Xcode project + SwiftUI entry point). Even if
  you’re
  sharing your UI with Compose Multiplatform, you need this entry point for your iOS app.

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

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).