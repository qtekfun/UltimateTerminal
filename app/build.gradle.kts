// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverReportFilter
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.licensee)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The app version lives in one place, `appVersion` in gradle.properties (SemVer, optionally
 * `-rc.N`). The version code is derived from it, so it never depends on dates or the machine:
 * MAJOR.MINOR.PATCH-rc.N -> (MAJOR*10000 + MINOR*100 + PATCH) * 100 + N, and 99 for a final
 * release, which therefore sorts after its release candidates.
 */
val appVersion = providers.gradleProperty("appVersion").get()

fun versionCodeOf(version: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?""").matchEntire(version)
        ?: error("appVersion must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N: $version")
    val (major, minor, patch, rc) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100 && (rc.isEmpty() || rc.toInt() in 1..98))
    val base = major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
    return base * 100 + (rc.toIntOrNull() ?: 99)
}

/** Release signing from the environment (CI secrets); without it the release APK is unsigned. */
val releaseKeystore: String? = System.getenv("UT_KEYSTORE_FILE")

android {
    namespace = "com.qtekfun.ultimateterminal"
    compileSdk = 37
    // Pinned for reproducible native builds (proot and talloc are compiled from source).
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.qtekfun.ultimateterminal"
        minSdk = 26
        // Deliberately 28 (SPEC §2): from targetSdk 29 Android forbids executing binaries from the
        // app's private storage, which proot needs. Do not raise without asking.
        targetSdk = 28
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion

        externalNativeBuild {
            cmake {
                // Reproducible builds: the checkout path must not end up in the native binaries
                // (__FILE__, debug info), or two machines would produce different .so files.
                cFlags += "-ffile-prefix-map=$rootDir=."
            }
        }

        ndk {
            // proot needs a 64-bit or ARM host; x86_64 is for emulators.
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("UT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UT_KEY_ALIAS")
                keyPassword = System.getenv("UT_KEY_PASSWORD")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    packaging {
        jniLibs {
            // proot and its loader are executables shipped as lib*.so: they must be extracted to
            // nativeLibraryDir to be run (SPEC §2, targetSdk 28).
            useLegacyPackaging = true
        }
    }

    // Reproducible builds (F-Droid): no Google-encrypted dependency blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // The git commit is not part of the APK: a build from a source tarball must match.
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        // The emulator library touches a few android.* classes (as its own tests do).
        unitTests.isReturnDefaultValues = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        // Intentional (SPEC §2): the app is distributed on F-Droid/GitHub, never on Google Play.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }

    androidResources {
        generateLocaleConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

tasks.withType<Detekt>().configureEach {
    // Match the project bytecode level; detekt defaults to the JDK running Gradle.
    jvmTarget = "17"
}

ktlint {
    version.set(libs.versions.ktlint)
}

// Coverage policy (CLAUDE.md): >= 85% over domain/data, 100% on rootfs verification and the
// backup format. Generated code and pure Compose UI are excluded. The critical packages are
// created by T06 and T15; until they exist the "critical" rule has nothing to measure and is
// not enforced.
val coveredPackages = listOf(
    "com.qtekfun.ultimateterminal.domain",
    "com.qtekfun.ultimateterminal.data"
)
val criticalPackages = listOf(
    "com.qtekfun.ultimateterminal.data.rootfs.verify",
    "com.qtekfun.ultimateterminal.data.backup"
)

/**
 * Generated code and pure Compose UI, excluded from coverage (CLAUDE.md). Applied to each report
 * variant: variant filters replace the global ones instead of adding to them.
 */
fun KoverReportFilter.generatedAndUiCode() {
    packages("com.qtekfun.ultimateterminal.ui", "dagger.hilt.internal", "hilt_aggregated_deps")
    classes(
        "*.R",
        "*.R$*",
        "*.BuildConfig",
        "*Hilt_*",
        "*_HiltModules*",
        "*_Factory",
        "*_Factory$*",
        "*_MembersInjector",
        // Room
        "*_Impl",
        "*_Impl$*",
        // Kotlin compatibility bridges for interface default methods
        "*\$DefaultImpls",
        "*ComposableSingletons*"
    )
    annotatedBy(
        "androidx.compose.ui.tooling.preview.Preview",
        "dagger.Module",
        "dagger.hilt.android.HiltAndroidApp",
        "*Generated*"
    )
}

kover {
    currentProject {
        createVariant("critical") {
            add("debug")
        }
    }

    reports {
        total {
            filters {
                excludes { generatedAndUiCode() }
                includes { packages(coveredPackages) }
            }
            verify {
                rule("domain and data") {
                    minBound(85)
                }
            }
        }

        variant("critical") {
            filters {
                excludes { generatedAndUiCode() }
                includes { packages(criticalPackages) }
            }
            verify {
                rule("rootfs verification and backup format") {
                    minBound(100, CoverageUnit.LINE)
                    minBound(100, CoverageUnit.BRANCH)
                }
            }
        }
    }
}

tasks.named("koverVerify") {
    dependsOn("koverVerifyCritical")
}

tasks.named("check") {
    dependsOn("koverVerify")
}

// Only GPL-3.0-compatible free licenses may ship in the APK. Anything else,
// including dependencies without a declared license, fails the build.
// Add other GPL-3.0-compatible SPDX ids (MIT, BSD-2-Clause, ISC...) only when a dependency
// needs them, and credit it in THIRD_PARTY_NOTICES.md.
licensee {
    allow("Apache-2.0")
}

// Google Play Services, Firebase and Crashlytics are banned outright (F-Droid
// rules in CLAUDE.md), regardless of what license they declare.
val checkForbiddenDependencies = tasks.register("checkForbiddenDependencies") {
    group = "verification"
    description =
        "Fails if a runtime classpath contains Google Play Services, Firebase or Crashlytics."
    val forbiddenGroupPrefixes = listOf(
        "com.google.android.gms",
        "com.google.firebase",
        "com.crashlytics",
        "io.fabric"
    )
    val runtimeModules = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").map { name ->
        configurations.named(name).flatMap { it.incoming.resolutionResult.rootComponent }
    }
    doLast {
        val modules = mutableSetOf<String>()
        val seen = mutableSetOf<ResolvedComponentResult>()
        val pending = ArrayDeque(runtimeModules.map { it.get() })
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (seen.add(component)) {
                (component.id as? ModuleComponentIdentifier)?.let {
                    modules.add(it.moduleIdentifier.toString())
                }
                component.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .forEach { pending.add(it.selected) }
            }
        }
        val offenders = modules
            .filter { module -> forbiddenGroupPrefixes.any { module.startsWith(it) } }
            .sorted()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Forbidden non-free dependencies found: ${offenders.joinToString()}"
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkForbiddenDependencies)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(project(":terminal-emulator"))

    // Used directly (flows, delay); declared instead of relying on what lifecycle brings in.
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.mockwebserver.junit5)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    // Host JVM build of the bundled SQLite, so Room runs in local unit tests.
    testImplementation(libs.sqlite.bundled.jvm)
}
