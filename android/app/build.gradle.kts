import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")

    // Sentry Android Gradle Plugin (declared in the root build.gradle.kts;
    // applied here so it participates in the app module's variant graph
    // and can upload R8 mapping artifacts for release builds).
    id("io.sentry.android.gradle")

    // SDD android-tooling-hygiene WS2a slice 4: Hilt foundation (catalog alias;
    // declared in the root build file, applied here).
    alias(libs.plugins.hilt)

    // SDD android-tooling-hygiene WS5 slice 7: Spotless formatting gate
    // (catalog alias; declared in the root build file, applied here).
    alias(libs.plugins.spotless)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// SDD android-tooling-hygiene WS5 slice 7: `spotless{kotlin{ktlint()}}` over
// **/*.kt + **/*.kts. ktlint runs at the Spotless-bundled default version
// (recorded in apply-progress #2750); detekt stays analysis-authoritative —
// see android/.editorconfig (ktlint no-wildcard-imports defers to detekt's
// WildcardImport excludeImports) and never enable detekt's formatting set.
spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        ktlint()
    }
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

val releaseMinifyEnabled =
    providers
        .gradleProperty("releaseMinify")
        .map { value ->
            when (value.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw GradleException("Invalid -PreleaseMinify value '$value'. Use true or false.")
            }
        }.orElse(true)
        .get()

fun String.escapeForBuildConfig(): String = replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.nextpage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nextpage"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val googleOAuthClientId = (localProperties.getProperty("google.oauth.client.id") ?: "").escapeForBuildConfig()
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleOAuthClientId\"")

        // Android OAuth client ID for the Drive authorization-code + PKCE flow.
        // Public client identifier (it ships inside the APK). Configured in
        // local.properties (google.oauth.android.client.id) like the web client —
        // must match the "Nextpage Android" OAuth client in Google Cloud Console
        // (package com.nextpage + the debug/release SHA-1 fingerprints).
        val googleOAuthAndroidClientId = (localProperties.getProperty("google.oauth.android.client.id") ?: "").escapeForBuildConfig()
        buildConfigField("String", "GOOGLE_OAUTH_ANDROID_CLIENT_ID", "\"$googleOAuthAndroidClientId\"")

        // Google Books API key (U2) — read from local.properties (gitignored).
        // When absent/blank the Google Books provider is omitted from the DI
        // list (fail-closed) and the app keeps working on Open Library +
        // Gutenberg. The real key has NOT been provided yet.
        val googleBooksKey = (localProperties.getProperty("GOOGLE_BOOKS_KEY") ?: "").escapeForBuildConfig()
        buildConfigField("String", "GOOGLE_BOOKS_KEY", "\"$googleBooksKey\"")

        // Drive OAuth redirect scheme injected into AndroidManifest.xml intent-filter.
        // Derived from the Android client ID (Google's reserved native-app pattern)
        // so the client ID literal never lives in the manifest or in Kotlin code.
        val driveRedirectScheme = "com.googleusercontent.apps.${googleOAuthAndroidClientId.removeSuffix(".apps.googleusercontent.com")}"
        manifestPlaceholders["driveRedirectScheme"] = driveRedirectScheme

        val supabaseUrl = (localProperties.getProperty("SUPABASE_URL") ?: "").escapeForBuildConfig()
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        val supabaseAnonKey = (localProperties.getProperty("SUPABASE_ANON_KEY") ?: "").escapeForBuildConfig()
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        // Sentry DSN — read from local.properties (gitignored). When empty,
        // SentryAndroid.init becomes a no-op (see NextPageApplication.onCreate).
        // Sentry auth token is read at Gradle config time from env vars below;
        // it never enters BuildConfig because it must not be shipped in the APK.
        val sentryDsn = (localProperties.getProperty("SENTRY_DSN") ?: "").escapeForBuildConfig()
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")

        // Git SHA (short=12) — injected at build time so every APK has a unique
        // fingerprint and matches the cross-platform release scheme from spec C1
        // (`nextpage-android@<VERSION_NAME>+<sha12>`). Truncated to 12 chars if a
        // shallow clone returns a shorter SHA; falls back to `unknown` on git
        // failure so debug builds never block.
        val gitSha =
            providers
                .exec {
                    commandLine("git", "rev-parse", "--short=12", "HEAD")
                    workingDir = rootProject.projectDir
                }.standardOutput.asText
                .get()
                .trim()
                .take(12)
                .ifEmpty { "unknown" }

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
                "proguard-rules.pro",
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // SDD android-tooling-hygiene WS3 slice 8: lint baseline gate. The
    // baseline freezes the post-reformat findings (see lint-baseline.xml);
    // only NEW findings fail the gate. `verifyStringParity` stays the
    // translation-parity gate (no double-gate on MissingTranslation).
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
    }

    // Robolectric reads schemas from the merged debug assets dir (AGP does not
    // merge test-sourceSet assets into it, and the Sentry asset-injection task
    // owns that directory). Copy Room schema exports there before unit tests.
    val copySchemasForUnitTest by tasks.registering(Copy::class) {
        from("$projectDir/schemas")
        into(layout.buildDirectory.dir("intermediates/assets/debug/injectSentryDebugMetaPropertiesIntoAssetsDebug"))
        mustRunAfter("injectSentryDebugMetaPropertiesIntoAssetsDebug")
    }

    tasks.matching { it.name == "packageDebugUnitTestForUnitTest" }.configureEach {
        dependsOn(copySchemasForUnitTest)
    }

    tasks.withType(Test::class).configureEach {
        dependsOn(copySchemasForUnitTest)
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
        getByName("test").assets.srcDirs("$projectDir/schemas")
    }
}

// Sentry Android Gradle Plugin extension. Out-of-android block per plugin docs.
// - autoInstallation: enabled → plugin auto-adds the Sentry Android SDK + a
//   Sentry OkHttp interceptor to the application.
// - org/projectName: bound to the Sentry project `nextpage-android` under the
//   organization slug the user creates in sentry.io before Phase 3.
// - authToken: read ONLY from env vars. The auth token is NEVER committed;
//   users set SENTRY_AUTH_TOKEN locally (or in CI secrets). When unset,
//   release builds will fail `verifySentryMappingUpload` (see below) — this
//   is intentional: silent mapping-upload failures are not acceptable.
// - telemetry=false: do not phone home with build-tool analytics.
//
// `SENTRY_AUTH_TOKEN` MUST be set for release builds. Documented in the
// `verifySentryMappingUpload` task below.
sentry {
    autoInstallation {
        enabled.set(true)
    }
    org.set(System.getenv("SENTRY_ORG") ?: "nextpage-android")
    projectName.set("nextpage-android")
    authToken.set(System.getenv("SENTRY_AUTH_TOKEN") ?: "")
    // Disable telemetry to avoid phoning home
    telemetry.set(false)
}

dependencies {
    val composeBom = platform(libs.compose.bom)

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splash)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.bundles.compose.ui)
    implementation(libs.compose.material.icons.extended)
    // Lottie for the Home streak widget (REQ-streak-widget-4). 6.6.6 verified
    // against Kotlin 1.9.24 (kotlin-stdlib 1.9.22 metadata); fallback 6.5.2.
    implementation(libs.lottie.compose)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.androidx.webkit)

    implementation(libs.androidx.navigation.compose)
    androidTestImplementation(libs.androidx.navigation.testing)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    // SDD android-tooling-hygiene WS2a slice 4: Hilt compiler via KSP ALONGSIDE
    // (not replacing) the Room compiler — one processor addition per the
    // KSP-ordering rule; both processors must run on assemble.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    androidTestImplementation(libs.room.testing)
    testImplementation(libs.room.testing)

    // Paging 3
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Google Drive REST API + Credential Manager
    implementation(libs.google.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.http.client.gson)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Ktor HTTP client (v3.x for supabase-kt compatibility)
    // CIO engine for the dedicated Discover catalog client (PR2: catalog-only,
    // keeps catalog traffic off the shared OkHttp stack used by Drive/Supabase).
    implementation(libs.bundles.ktor.client)

    // supabase-kt v3 — Supabase client for Android
    implementation(platform(libs.supabase.bom))
    implementation(libs.bundles.supabase)
    // kotlinx-datetime — supabase-kt 3.5+ uses its own unix serializer (no
    // InstantIso8601Serializer). Readium 3.2.0 requires 0.7+ (atStartOfDayIn).
    implementation(libs.kotlinx.datetime)

    // Security: encrypted storage (database encryption requires Kotlin 2.0+ upgrade)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.profileinstaller)

    // Readium Kotlin Toolkit (EPUB + PDF rendering)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.adapter.pdfium) {
        exclude(group = "androidx.appcompat")
    }

    // Sentry Android SDK (8.54.0 — latest stable on Maven Central at PR 3
    // landing time). Pinned explicitly so transitive resolution cannot
    // surprise us. The Gradle plugin's autoInstallation also adds this
    // dependency; declaring it here makes the version visible to reviewers.
    implementation(libs.sentry.android)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.hilt.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testing.json)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.testing.robolectric)
    testImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.junit.ext)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)
}

tasks.register("verifyAuthScreenNoHardcodedStrings") {
    group = "verification"
    description = "Fails if AuthScreen contains hardcoded user-facing strings"

    // Resolve the file at configuration time; RegularFile is a supported
    // configuration-cache type (Project.file() would capture the script).
    val authScreenFile =
        layout.projectDirectory.file(
            "src/main/java/com/nextpage/presentation/screen/AuthScreen.kt",
        )

    doLast {
        if (!authScreenFile.asFile.exists()) {
            throw GradleException("AuthScreen.kt not found: ${authScreenFile.asFile.path}")
        }

        val textCallLiteralPattern = Regex("\\bText\\(\\s*\"[^\"]+")
        val textArgLiteralPattern = Regex("\\btext\\s*=\\s*\"[^\"]+")

        val violations =
            authScreenFile.asFile.readLines().mapIndexedNotNull { index, line ->
                val hasViolation =
                    textCallLiteralPattern.containsMatchIn(line) ||
                        textArgLiteralPattern.containsMatchIn(line)
                if (hasViolation) "${index + 1}: ${line.trim()}" else null
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Hardcoded user-facing strings found in AuthScreen.kt:\n" + violations.joinToString("\n"),
            )
        }
    }
}

tasks.register("verifyStringParity") {
    group = "verification"
    description = "Fails if a string/plurals/string-array resource is missing from either locale"

    // Resolve at configuration time; RegularFile is configuration-cache safe.
    val defaultStrings = layout.projectDirectory.file("src/main/res/values/strings.xml")
    val spanishStrings = layout.projectDirectory.file("src/main/res/values-es/strings.xml")

    doLast {
        // Matches the declared resource name of <string>, <plurals> and
        // <string-array>. <item> children are intentionally excluded: their
        // names differ per locale inside a plural/array and are not resources.
        val namePattern = Regex("<(?:string|plurals|string-array)\\s+name=\"([^\"]+)\"")

        fun namesIn(
            file: java.io.File,
            label: String,
        ): Set<String> {
            if (!file.exists()) throw GradleException("$label not found: ${file.path}")
            return namePattern
                .findAll(file.readText())
                .map { it.groupValues[1] }
                .toSet()
        }

        val english = namesIn(defaultStrings.asFile, "values/strings.xml")
        val spanish = namesIn(spanishStrings.asFile, "values-es/strings.xml")

        val missingInSpanish = (english - spanish).sorted()
        val missingInEnglish = (spanish - english).sorted()

        if (missingInSpanish.isNotEmpty() || missingInEnglish.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Locale string parity failed.\n")
                    if (missingInSpanish.isNotEmpty()) {
                        append("Missing from values-es/strings.xml (${missingInSpanish.size}):\n")
                        missingInSpanish.forEach { append("  - $it\n") }
                    }
                    if (missingInEnglish.isNotEmpty()) {
                        append("Missing from values/strings.xml (${missingInEnglish.size}):\n")
                        missingInEnglish.forEach { append("  - $it\n") }
                    }
                },
            )
        }
    }
}

tasks.register("verifyNoReaderUiStateResidue") {
    group = "verification"
    description = "Fails if the deleted Reader uiState aggregate resurfaces (SDD reader-uiState-cleanup S7 gate)"

    // S7 deleted ReaderViewModel.uiState + the 5+1-way combine overlay +
    // mutableUiState + the 30 annotation delegates + the ReaderUiState type.
    // This gate fails the build if any of it resurfaces:
    //  - `ReaderUiState` (the deleted type) anywhere under app/src/main;
    //  - `.uiState` member access inside reader-owned sources (the six former
    //    consumers + the VM + holders). Other feature VMs legitimately expose
    //    their own `uiState` (auth, library, …), so the member-access scan is
    //    scoped to reader paths instead of an allowlist. Note:
    //    `presentation/debug/DebugPanel.kt` is intentionally NOT scanned — its
    //    reader half takes a `session: SessionUiState?` param (no member
    //    access possible) and its only `.uiState` read belongs to
    //    AuthViewModel.
    val readerRoots =
        listOf(
            "src/main/java/com/nextpage/presentation/viewmodel/ReaderViewModel.kt",
            "src/main/java/com/nextpage/presentation/viewmodel/reader",
            "src/main/java/com/nextpage/presentation/screen/reader",
            "src/main/java/com/nextpage/presentation/screen/ReaderScreen.kt",
            "src/main/java/com/nextpage/presentation/screen/ReadiumPdfReaderContent.kt",
            "src/main/java/com/nextpage/presentation/screen/readium",
            "src/main/java/com/nextpage/debug/DebugPanel.kt",
        )
    val typeBanPattern = Regex("\\bReaderUiState\\b")
    val memberPattern = Regex("\\.uiState\\b")

    // Resolve plain Files at configuration time (java.io.File is a supported
    // configuration-cache type; touching Project.layout inside doLast is not —
    // same pattern as verifyAuthScreenNoHardcodedStrings above).
    val projectDir = layout.projectDirectory.asFile
    val mainSrcDir = projectDir.resolve("src/main/java")
    val readerBases = readerRoots.map { projectDir.resolve(it) }

    doLast {
        val violations = mutableListOf<String>()

        // 1. Global type ban: no ReaderUiState anywhere in main sources.
        mainSrcDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (typeBanPattern.containsMatchIn(line)) {
                        violations += "${file.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                    }
                }
            }

        // 2. Scoped member ban: no `.uiState` access in reader-owned sources.
        readerBases.forEach { base ->
            if (!base.exists()) return@forEach
            val files =
                if (base.isFile) {
                    listOf(base)
                } else {
                    base
                        .walkTopDown()
                        .filter { it.isFile && it.extension == "kt" }
                        .toList()
                }
            files.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (memberPattern.containsMatchIn(line)) {
                        violations += "${file.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Deleted Reader uiState aggregate resurfaced (SDD reader-uiState-cleanup S7 gate):\n" +
                    violations.joinToString("\n"),
            )
        }
        logger.lifecycle("verifyNoReaderUiStateResidue: no residue found")
    }
}

tasks.register("verifyReleaseMapping") {
    group = "verification"
    description = "Verifies release mapping artifact when minify is enabled"

    // Resolve the provider at configuration time (configuration-cache safe).
    val mappingFileProvider = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")

    doLast {
        if (!releaseMinifyEnabled) {
            logger.lifecycle("Skipping mapping verification because -PreleaseMinify=false")
            return@doLast
        }

        val mappingFile = mappingFileProvider.get().asFile
        if (!mappingFile.exists()) {
            throw GradleException("Release mapping file not found: ${mappingFile.path}")
        }
    }
}

// Mirrors verifyReleaseMapping for the Sentry R8 mapping upload step.
// We don't read the SAGP upload log to "prove" the upload succeeded (that
// would be brittle across plugin versions). Instead, we fail release builds
// when SENTRY_AUTH_TOKEN is missing — because without a token, the upload
// cannot succeed. Skipped with -PreleaseMinify=false (same convention as
// verifyReleaseMapping) so debug builds aren't gated by a token requirement.
//
// Configuration-cache safe: capture the script-level `releaseMinifyEnabled`
// provider AND `logger` into task-local vals so the doLast closure doesn't
// reference the Gradle script object. Same pattern as `verifyReleaseMapping`.
tasks.register("verifySentryMappingUpload") {
    group = "verification"
    description = "Fails if Sentry mapping upload is not configured for release builds"

    val taskLogger = logger
    val minifyEnabled = releaseMinifyEnabled

    doLast {
        if (!minifyEnabled) {
            taskLogger.lifecycle("Skipping Sentry mapping verification because -PreleaseMinify=false")
            return@doLast
        }
        val sentryToken = System.getenv("SENTRY_AUTH_TOKEN") ?: ""
        if (sentryToken.isEmpty()) {
            throw GradleException(
                "SENTRY_AUTH_TOKEN is not set. Release builds require Sentry mapping upload. " +
                    "Set it as an env var or in gradle.properties (gitignored).",
            )
        }
    }
}
