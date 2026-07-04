-keep class kotlinx.datetime.** { *; }
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }
-keep class okio.** { *; }
-keep class sun.misc.Unsafe { *; }

-keep class * extends androidx.room.RoomDatabase { *; }

# Preserve native methods in BundledSQLiteDriverKt
-keepclasseswithmembers class androidx.sqlite.driver.bundled.** {
    native <methods>;
}

-dontwarn kotlinx.datetime.**
-dontwarn okio.**