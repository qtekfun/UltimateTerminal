// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.storage

import org.junit.jupiter.api.Assertions.assertEquals
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
}
