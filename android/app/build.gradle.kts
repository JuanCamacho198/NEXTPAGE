import java.util.Properties
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val releaseMinifyEnabled = providers.gradleProperty("releaseMinify")
    .map { value ->
        when (value.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw GradleException("Invalid -PreleaseMinify value '$value'. Use true or false.")
        }
    }
    .orElse(true)
    .get()

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.nextpage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nextpage"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val googleOAuthClientId = (localProperties.getProperty("google.oauth.client.id") ?: "").escapeForBuildConfig()
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleOAuthClientId\"")

        val supabaseUrl = (localProperties.getProperty("SUPABASE_URL") ?: "").escapeForBuildConfig()
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        val supabaseAnonKey = (localProperties.getProperty("SUPABASE_ANON_KEY") ?: "").escapeForBuildConfig()
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        // Git SHA (short) — injected at build time so every APK has a unique fingerprint
        val gitSha = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            workingDir = rootProject.projectDir
        }.standardOutput.asText.get().trim()

        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = releaseMinifyEnabled
            isShrinkResources = releaseMinifyEnabled
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    val coilVersion = "2.7.0"

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil:$coilVersion")
    implementation("io.coil-kt:coil-compose:$coilVersion")
    implementation("androidx.webkit:webkit:1.12.1")

    implementation("androidx.navigation:navigation-compose:2.8.2")
    androidTestImplementation("androidx.navigation:navigation-testing:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-paging:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    // Google Drive REST API + Credential Manager
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.api-client:google-api-client-android:2.7.2")
    implementation("com.google.apis:google-api-services-drive:v3-rev20260428-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.3")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Ktor HTTP client (v3.x for supabase-kt compatibility)
    implementation("io.ktor:ktor-client-core:3.1.2")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")

    // supabase-kt v3 — Supabase client for Android
    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    // kotlinx-datetime — supabase-kt 3.5+ uses its own unix serializer (no
    // InstantIso8601Serializer). Readium 3.2.0 requires 0.7+ (atStartOfDayIn).
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    // Security: encrypted storage (database encryption requires Kotlin 2.0+ upgrade)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Readium Kotlin Toolkit (EPUB + PDF rendering)
    implementation("org.readium.kotlin-toolkit:readium-shared:3.2.0")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.2.0")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.2.0")
    implementation("org.readium.kotlin-toolkit:readium-adapter-pdfium:3.2.0") {
        exclude(group = "androidx.appcompat")
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.json:json:20231013")
    testImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.register("verifyAuthScreenNoHardcodedStrings") {
    group = "verification"
    description = "Fails if AuthScreen contains hardcoded user-facing strings"

    doLast {
        val authScreenFile = file("src/main/java/com/nextpage/presentation/screen/AuthScreen.kt")
        if (!authScreenFile.exists()) {
            throw GradleException("AuthScreen.kt not found: ${authScreenFile.path}")
        }

        val textCallLiteralPattern = Regex("\\bText\\(\\s*\"[^\"]+")
        val textArgLiteralPattern = Regex("\\btext\\s*=\\s*\"[^\"]+")

        val violations = authScreenFile.readLines().mapIndexedNotNull { index, line ->
            val hasViolation =
                textCallLiteralPattern.containsMatchIn(line) ||
                    textArgLiteralPattern.containsMatchIn(line)
            if (hasViolation) "${index + 1}: ${line.trim()}" else null
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Hardcoded user-facing strings found in AuthScreen.kt:\n" + violations.joinToString("\n")
            )
        }
    }
}

tasks.register("verifyReleaseMapping") {
    group = "verification"
    description = "Verifies release mapping artifact when minify is enabled"

    doLast {
        if (!releaseMinifyEnabled) {
            logger.lifecycle("Skipping mapping verification because -PreleaseMinify=false")
            return@doLast
        }

        val mappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt").get().asFile
        if (!mappingFile.exists()) {
            throw GradleException("Release mapping file not found: ${mappingFile.path}")
        }
    }
}

