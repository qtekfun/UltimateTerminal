// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.rootfs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RootfsModelsTest {
    @Test
    fun architectureFollowsTheDevicesAbiPreferenceOrder() {
        assertEquals(Architecture.ARM64, Architecture.fromAbis(listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals(Architecture.ARMV7, Architecture.fromAbis(listOf("armeabi", "armeabi-v7a")))
        assertEquals(Architecture.X86_64, Architecture.fromAbis(listOf("x86_64", "x86")))
    }

    @Test
    fun noSupportedAbiGivesNoArchitecture() {
        assertNull(Architecture.fromAbis(listOf("x86", "mips")))
        assertNull(Architecture.fromAbis(emptyList()))
    }

    @Test
    fun progressFractionIsKnownOnlyWithATotal() {
        assertEquals(0.25f, DownloadProgress(25, 100).fraction)
        assertNull(DownloadProgress(25, null).fraction)
        assertNull(DownloadProgress(25, 0).fraction)
    }

    @Test
    fun progressFractionNeverLeavesZeroToOne() {
        assertEquals(1f, DownloadProgress(150, 100).fraction)
        assertEquals(0f, DownloadProgress(-5, 100).fraction)
    }
}
