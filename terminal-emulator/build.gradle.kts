// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

// Vendored third-party code (see terminal-emulator/README.md and THIRD_PARTY_NOTICES.md): the Java
// sources under src/ are kept exactly as upstream; only this build file and CMakeLists.txt are ours.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.qtekfun.ultimateterminal.emulator"
    compileSdk = 37
    // Same NDK as :app (D-004), so one toolchain builds every native library.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                // The file-prefix-map keeps the checkout path out of the binary (reproducible builds).
                cFlags += setOf(
                    "-std=c11", "-Wall", "-Wextra", "-Os", "-fno-stack-protector",
                    "-ffile-prefix-map=${rootDir}=."
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Upstream's tests touch a few android.* classes.
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Third-party code that we deliberately do not modify: its Lint findings are not ours to fix.
        warningsAsErrors = false
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    testImplementation(libs.junit4)
}
