// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartupCommandTest {
    @Test
    fun nothingOrBlankIsNoCommand() {
        assertEquals(StartupCommand.Check.Valid(null), StartupCommand.check(null))
        assertEquals(StartupCommand.Check.Valid(null), StartupCommand.check(" \t"))
    }

    @Test
    fun aPlainLineIsKeptTrimmedAndTypedWithEnter() {
        val check = StartupCommand.check("  ssh -V  ")

        assertEquals(StartupCommand.Check.Valid("ssh -V"), check)
        assertEquals("ssh -V\r", StartupCommand.inputFor("ssh -V"))
    }

    @Test
    fun eachKindOfFaultIsNamed() {
        assertEquals(
            StartupCommand.Check.Invalid(StartupCommandFault.MULTIPLE_LINES),
            StartupCommand.check("a\rb")
        )
        assertEquals(
            StartupCommand.Check.Invalid(StartupCommandFault.CONTROL_CHARACTER),
            StartupCommand.check("a\u001bb")
        )
        assertEquals(
            StartupCommand.Check.Invalid(StartupCommandFault.TOO_LONG),
            StartupCommand.check("x".repeat(StartupCommand.MAX_LENGTH + 1))
        )
        assertEquals(
            StartupCommand.Check.Valid("x".repeat(StartupCommand.MAX_LENGTH)),
            StartupCommand.check("x".repeat(StartupCommand.MAX_LENGTH))
        )
    }
}
