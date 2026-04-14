# Room runtime/generated wiring
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Kotlin serialization models and generated serializers
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# App navigation routes used by Compose NavHost
-keep class com.nextpage.presentation.navigation.NextPageDestination { *; }
-keep class com.nextpage.presentation.navigation.NextPageDestination$* { *; }

# Supabase integration entry points and wrappers
-keep class com.nextpage.data.remote.supabase.** { *; }
-keep class io.github.jan.supabase.** { *; }

# Optional SLF4J backend not packaged on Android
-dontwarn org.slf4j.impl.StaticLoggerBinder
