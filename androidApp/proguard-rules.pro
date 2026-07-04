# Conifer ProGuard / R8 rules
#
# This file is applied on top of `proguard-android-optimize.txt` (see androidApp/build.gradle.kts).
# Most libraries used here (AndroidX, Compose, Room, kotlinx) ship their own consumer rules,
# so the rules below only cover the cases R8 cannot infer on its own.

# Keep generic signatures, annotations and exceptions so reflection-based code keeps working.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, Exceptions

# ---------------------------------------------------------------------------
# Room + bundled SQLite
# ---------------------------------------------------------------------------
# Room's KSP processor emits consumer ProGuard rules, but keep the entities, DAOs and the
# generated database implementation explicitly to be safe against schema/reflection access.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# The bundled SQLite driver loads a native library and calls into it via JNI.
-keep class androidx.sqlite.driver.bundled.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class androidx.sqlite.** {
    native <methods>;
}

# ---------------------------------------------------------------------------
# Kotlin / Coroutines
# ---------------------------------------------------------------------------
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
# Coroutines uses an internal volatile field updated via AtomicReferenceFieldUpdater.
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# kotlinx-datetime
-dontwarn kotlinx.datetime.**

# ---------------------------------------------------------------------------
# Napier (logging)
# ---------------------------------------------------------------------------
-dontwarn io.github.aakira.napier.**

# ---------------------------------------------------------------------------
# Compose
# ---------------------------------------------------------------------------
# Compose ships its own rules; silence warnings from optional desktop/preview-only paths.
-dontwarn androidx.compose.**