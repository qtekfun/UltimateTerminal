// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppIdentityTest {
    @Test
    fun applicationIdIsTheDocumentedOne() {
        assertEquals(
            "com.qtekfun.ultimateterminal",
            BuildConfig.APPLICATION_ID.removeSuffix(".debug")
        )
    }
}
