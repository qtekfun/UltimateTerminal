// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Validation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultKeyNameTest {
    private fun name(display: String?, existing: List<String> = emptyList()) =
        DefaultKeyName.from(display, existing)

    @Test
    fun aPlainNameIsUsedAsIs() = assertEquals("id_ed25519_homelab", name("id_ed25519_homelab"))

    @Test
    fun surroundingWhitespaceIsTrimmed() = assertEquals("work key", name("  work key \n"))

    @Test
    fun pemKeyAndTxtExtensionsAreDroppedInAnyCase() {
        assertEquals("deploy", name("deploy.pem"))
        assertEquals("deploy", name("deploy.KEY"))
        assertEquals("deploy", name("deploy.txt"))
    }

    @Test
    fun onlyOneTrailingExtensionIsDropped() = assertEquals("a.key", name("a.key.pem"))

    @Test
    fun otherExtensionsStayAndPubIsNotStripped() {
        assertEquals("id_rsa.pub", name("id_rsa.pub"))
        assertEquals("key.old", name("key.old"))
    }

    @Test
    fun aFileNamedJustLikeAnExtensionKeepsItsName() = assertEquals(".pem", name(".pem"))

    @Test
    fun noUsableNameGivesNoSuggestion() {
        assertEquals("", name(null))
        assertEquals("", name(""))
        assertEquals("", name("   "))
        assertEquals("", name("\u0000\n"))
    }

    @Test
    fun aTakenNameGetsTheNextFreeNumber() {
        assertEquals("homelab 2", name("homelab.pem", listOf("homelab")))
        assertEquals("homelab 3", name("homelab", listOf("homelab", "Homelab 2")))
    }

    @Test
    fun uniquenessIgnoresCase() = assertEquals("Homelab 2", name("Homelab", listOf("homelab")))

    @Test
    fun aVeryLongNameIsCutToTheLimitAndStillValid() {
        val long = "k".repeat(200)
        val first = name(long)
        assertEquals(Validation.MAX_NAME_LENGTH, first.length)
        val second = name(long, listOf(first))
        assertEquals(Validation.MAX_NAME_LENGTH, second.length)
        assertTrue(second.endsWith(" 2"))
        assertTrue(Validation.name(second) is Outcome.Success)
    }
}
