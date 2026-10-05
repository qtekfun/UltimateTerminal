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
    fun marksAreDroppedAndEachTableRowBecomesOneItemWithItsDetails() {
        val used = Notices.parse(sample).first { it.title == "Used now" }

        assertEquals(
            listOf(
                NoticeItem("Kotlin, © JetBrains", "Apache-2.0"),
                NoticeItem("AndroidX, © AOSP", "Apache-2.0")
            ),
            used.items
        )
        assertEquals(
            listOf(NoticeItem("UltimateTerminal is free software under the GPL (see LICENSE).")),
            Notices.parse(sample).first().items
        )
        assertEquals(
            listOf(NoticeItem("Something later, soon")),
            Notices.parse(sample).last().items
        )
    }

    @Test
    fun theColumnsAfterTheNameAreTheDetailOfTheRow() {
        val table = """
            ## Used

            | Component | License | Notes |
            |---|---|---|
            | OkHttp, © Square | Apache-2.0 | HTTP client |
        """.trimIndent()

        assertEquals(
            listOf(NoticeItem("OkHttp, © Square", "Apache-2.0\nHTTP client")),
            Notices.parse(table).single().items
        )
    }

    @Test
    fun hardWrappedLinesJoinIntoOneParagraphAndBlankLinesSeparateThem() {
        val wrapped = """
            ## Notes

            The color schemes are credited
            with their copyright lines
            in the bundled file.

            A second paragraph,
            also wrapped.
        """.trimIndent()

        assertEquals(
            listOf(
                NoticeItem(
                    "The color schemes are credited with their copyright lines in the bundled file."
                ),
                NoticeItem("A second paragraph, also wrapped.")
            ),
            Notices.parse(wrapped).single().items
        )
    }

    @Test
    fun aListItemKeepsItsContinuationLinesAndEachBulletIsItsOwnItem() {
        val list = """
            ## Sources

            - **Alpine** minirootfs, from the CDN.
            - **Debian** built with the tool
              published as an image.
        """.trimIndent()

        assertEquals(
            listOf(
                NoticeItem("Alpine minirootfs, from the CDN."),
                NoticeItem("Debian built with the tool published as an image.")
            ),
            Notices.parse(list).single().items
        )
    }

    @Test
    fun theMaintenanceSentenceIsDroppedButTheNoticeAroundItStays() {
        val intro = """
            It is not affiliated with anyone.

            This file must be updated in the same change that adds
            a library. Original notices are kept.

            ## Used

            | A | B |
            |---|---|
            | Lib | MIT |
        """.trimIndent()

        val sections = Notices.parse(intro)

        val notice = NoticeItem("It is not affiliated with anyone.")
        assertEquals(listOf(notice), sections.first().items)
        assertEquals(listOf(NoticeItem("Lib", "MIT")), sections.last().items)
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
        assertTrue(sections.flatMap { it.items }.any { it.detail?.contains("Apache-2.0") == true })
        val shown = sections.flatMap { it.items }.flatMap { listOfNotNull(it.title, it.detail) }
        assertTrue(shown.none { "](" in it || "**" in it })
        assertTrue(shown.none { it.startsWith("This file must be updated") })
        val titles = sections.flatMap { it.items }.map { it.title }
        assertTrue(titles.any { it.startsWith("Apache Commons Compress 1.28.0") })
    }
}
