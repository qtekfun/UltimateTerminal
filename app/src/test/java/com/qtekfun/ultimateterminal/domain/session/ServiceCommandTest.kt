// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServiceCommandTest {
    @Test
    fun theNotificationActionsMapToTheirCommands() {
        assertEquals(ServiceCommand.EXIT, ServiceCommand.of(ServiceCommand.ACTION_EXIT))
        assertEquals(
            ServiceCommand.NEW_SESSION,
            ServiceCommand.of(ServiceCommand.ACTION_NEW_SESSION)
        )
    }

    @Test
    fun aMissingOrUnknownActionAsksForNothing() {
        assertEquals(ServiceCommand.NONE, ServiceCommand.of(null))
        assertEquals(ServiceCommand.NONE, ServiceCommand.of("something.else"))
    }
}
