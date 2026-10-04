// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageToggleTest {
    @Test
    fun `turning it on without the permission asks for the permission`() {
        assertEquals(StorageToggleAction.REQUEST_PERMISSION, StorageToggle.onToggled(true, false))
    }

    @Test
    fun `turning it on with the permission just turns it on`() {
        assertEquals(StorageToggleAction.ENABLE, StorageToggle.onToggled(true, true))
    }

    @Test
    fun `turning it off never asks for anything`() {
        assertEquals(StorageToggleAction.DISABLE, StorageToggle.onToggled(false, false))
        assertEquals(StorageToggleAction.DISABLE, StorageToggle.onToggled(false, true))
    }

    @Test
    fun `the feature turns on only if the permission dialog was accepted`() {
        assertEquals(StorageToggleAction.ENABLE, StorageToggle.onPermissionResult(true))
        assertEquals(StorageToggleAction.DISABLE, StorageToggle.onPermissionResult(false))
    }

    @Test
    fun `an empty permission result is not a grant`() {
        // `all` on an empty map is true: an interrupted request must not turn the feature on.
        assertFalse(StorageToggle.allGranted(emptyMap()))
    }

    @Test
    fun `a result is a grant only if every permission was granted`() {
        assertTrue(StorageToggle.allGranted(mapOf("read" to true, "write" to true)))
        assertFalse(StorageToggle.allGranted(mapOf("read" to true, "write" to false)))
        assertFalse(StorageToggle.allGranted(mapOf("read" to false)))
    }

    @Test
    fun `the switch shows on only when the setting is on and the permission is held`() {
        assertTrue(StorageToggle.isShownOn(enabled = true, permissionGranted = true))
        assertFalse(StorageToggle.isShownOn(enabled = true, permissionGranted = false))
        assertFalse(StorageToggle.isShownOn(enabled = false, permissionGranted = true))
        assertFalse(StorageToggle.isShownOn(enabled = false, permissionGranted = false))
    }
}
