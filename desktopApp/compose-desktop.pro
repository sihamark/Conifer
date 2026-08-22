-keep class kotlinx.datetime.** { *; }
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }
-keep class okio.** { *; }
-keep class sun.misc.Unsafe { *; }

-keep class * extends androidx.room.RoomDatabase { *; }

# We build our clients as `HttpClient()` without naming an engine, so Ktor picks one at
# runtime via ServiceLoader. Nothing then references the engine statically, and shrinking
# drops it while the META-INF/services entry naming it survives -- the packaged app dies
# with "Provider io.ktor.client.engine.okhttp.OkHttpEngineContainer not found". Keyed off
# the interface so a future engine swap stays covered.
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }
-keep class io.ktor.client.engine.okhttp.** { *; }

# Preserve native methods in BundledSQLiteDriverKt
-keepclasseswithmembers class androidx.sqlite.driver.bundled.** {
    native <methods>;
}

-dontwarn kotlinx.datetime.**
-dontwarn okio.**

# OkHttp reaches for TLS providers that are optional at runtime and are not on our
# classpath (we use the JDK's own provider), plus GraalVM native-image hooks we never
# run. The GraalVM ones happen to resolve when the build runs on a GraalVM JDK and not
# on the JetBrains Runtime, so silence them here rather than depend on the build JDK.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**
