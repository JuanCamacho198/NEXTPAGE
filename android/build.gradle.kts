plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false

    // Sentry Android Gradle Plugin (SAGP). Resolved to the latest stable
    // version on Maven Central at the time of PR 3 (sentry-cross-platform):
    // 6.21.0 (released 2026-09-03). The plugin auto-installs a compatible
    // io.sentry:sentry-android runtime via Gradle dependency resolution;
    // we additionally pin sentry-android:8.54.0 in app/build.gradle.kts
    // for explicit version control.
    alias(libs.plugins.sentry.gradle) apply false
}
