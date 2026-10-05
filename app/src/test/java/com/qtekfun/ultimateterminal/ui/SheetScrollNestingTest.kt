// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The project has no Compose UI tests, so this guards by source the crash found on a Pixel 8: the
 * content of an `IosBottomSheet` already scrolls once, and a second vertical scroll or lazy list
 * inside it is measured with unbounded height and throws `IllegalStateException`. A file that opens
 * a sheet must not use any vertically scrolling container of its own.
 */
class SheetScrollNestingTest {
    private val root = File("src/main/java/com/qtekfun/ultimateterminal")
    private val sheet = File(root, "ui/ios/IosBottomSheet.kt")
    private val forbidden = listOf(
        "verticalScroll",
        "LazyColumn",
        "LazyVerticalGrid",
        "LazyVerticalStaggeredGrid"
    )

    private fun sheetUsers() = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" && it != sheet }
        .filter { "IosBottomSheet(" in it.readText() }
        .toList()

    @Test
    fun `sheets are found so the guard is not vacuous`() {
        val names = sheetUsers().map { it.name }
        assertTrue("ProfileEditorSheet.kt" in names)
        assertTrue("ShortcutSheet.kt" in names)
    }

    @Test
    fun `no file that opens a sheet nests a vertical scroll in it`() {
        val offenders = sheetUsers().flatMap { file ->
            val text = file.readText()
            forbidden.filter { it in text }.map { "${file.name}: $it" }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `the sheet itself scrolls its content once`() {
        assertEquals(1, Regex("\\.verticalScroll\\(").findAll(sheet.readText()).count())
    }
}
