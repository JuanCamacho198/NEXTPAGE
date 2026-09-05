plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false

    // Sentry Android Gradle Plugin (SAGP). Resolved to the latest stable
    // version on Maven Central at the time of PR 3 (sentry-cross-platform):
    // 6.21.0 (released 2026-09-03). The plugin auto-installs a compatible
    // io.sentry:sentry-android runtime via Gradle dependency resolution;
    // we additionally pin sentry-android:8.54.0 in app/build.gradle.kts
    // for explicit version control.
    id("io.sentry.android.gradle") version "6.21.0" apply false
}
