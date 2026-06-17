# Room runtime/generated wiring
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# App navigation routes used by Compose NavHost
-keep class com.nextpage.presentation.navigation.NextPageDestination { *; }
-keep class com.nextpage.presentation.navigation.NextPageDestination$* { *; }

# Google Drive API client — uses reflection for REST serialization
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }

# Gson models used by Google Drive Sync
-keep class com.nextpage.data.remote.sync.BookStateJson { *; }
-keep class com.nextpage.data.remote.sync.ProgressStateJson { *; }
-keep class com.nextpage.data.remote.sync.HighlightStateJson { *; }
-keep class com.nextpage.data.remote.sync.BookmarkStateJson { *; }

# Optional SLF4J backend not packaged on Android
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Security: strip debug logs from release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
