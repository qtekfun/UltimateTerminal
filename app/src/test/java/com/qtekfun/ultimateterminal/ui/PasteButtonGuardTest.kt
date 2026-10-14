// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The project has no Compose UI tests, so this guards by source a gap found in use: Copy had a
 * button and paste only had keyboard shortcuts, so on a touch screen there was no way to paste.
 */
class PasteButtonGuardTest {
    private val main = File("src/main")

    private fun source(path: String) =
        File(main, "java/com/qtekfun/ultimateterminal/$path").readText()

    @Test
    fun `the selection bar offers Paste next to Copy`() {
        val screen = source("ui/TerminalScreen.kt")
        val bar = source("ui/SelectionActions.kt")
        assertTrue("viewModel.selection::copy" in screen)
        assertTrue("viewModel::paste" in screen)
        assertTrue("R.string.terminal_copy" in bar)
        assertTrue("R.string.terminal_paste" in bar)
        assertTrue("R.string.terminal_select_all" in bar)
    }

    @Test
    fun `Paste clears the selection and goes through the broadcast-aware output`() {
        val model = source("terminal/TerminalViewModel.kt")
        val paste = model.substringAfter("fun paste() {").substringBefore("}")
        assertTrue("selection.clear()" in paste)
        assertTrue("pasteFromClipboard()" in paste)
    }

    @Test
    fun `the button label is translated`() {
        listOf("values", "values-es").forEach {
            val strings = File(main, "res/$it/strings.xml").readText()
            assertTrue("name=\"terminal_paste\"" in strings, it)
        }
    }
}
