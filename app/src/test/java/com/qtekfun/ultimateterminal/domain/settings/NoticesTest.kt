// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoticesTest {
    private val sample = """
        # Third-party notices

        UltimateTerminal is **free** software under the GPL (see `LICENSE`).

        ## Used now

        | Component | License |
        |---|---|
        | [Kotlin](https://kotlinlang.org), © JetBrains | Apache-2.0 |
        | AndroidX, © AOSP | Apache-2.0 |

        ## Planned

        - Something later, `soon`
    """.trimIndent()

    @Test
    fun splitsAtHeadingsAndKeepsTheIntroUntitled() {
        val sections = Notices.parse(sample)

        assertEquals(listOf("", "Used now", "Planned"), sections.map { it.title })
    }

    @Test
    fun marksAreDroppedAndTableRowsBecomeOneLine() {
        val used = Notices.parse(sample).first { it.title == "Used now" }

        assertEquals(
            listOf(
                "Component · License",
                "Kotlin, © JetBrains · Apache-2.0",
                "AndroidX, © AOSP · Apache-2.0"
            ),
            used.lines
        )
        assertEquals(
            listOf("UltimateTerminal is free software under the GPL (see LICENSE)."),
            Notices.parse(sample).first().lines
        )
        assertEquals(listOf("Something later, soon"), Notices.parse(sample).last().lines)
    }

    @Test
    fun aSectionWithNothingInItIsLeftOut() {
        assertTrue(Notices.parse("## Empty\n\n## Also empty\n").isEmpty())
        assertTrue(Notices.parse("").isEmpty())
    }

    @Test
    fun theRealCreditsFileGivesSectionsThatTheAboutPageCanShow() {
        // The build copies this very file into the APK, so what Settings shows is what this reads.
        val text = java.io.File("../THIRD_PARTY_NOTICES.md").readText()

        val sections = Notices.parse(text)

        assertTrue(sections.size >= 2, "the file has several sections")
        assertTrue(sections.any { it.title == "Used now" })
        assertTrue(sections.flatMap { it.lines }.any { "Apache-2.0" in it })
        assertTrue(sections.flatMap { it.lines }.none { "](" in it || "**" in it })
    }
}
