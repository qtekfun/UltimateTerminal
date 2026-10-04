// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/**
 * Guards the activity settings the adaptive terminal depends on (SPEC RF-03): the activity must not
 * be recreated when the window is resized, rotated, folded or when the keyboard appears, or the
 * terminal view would be rebuilt (and the pty resized) each time.
 */
class ManifestWindowConfigTest {
    private val activity: Element = run {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
        val activities = document.getElementsByTagName("activity")
        (0 until activities.length).map { activities.item(it) as Element }
            .single { it.getAttributeNS(ANDROID, "name") == ".MainActivity" }
    }

    private fun attribute(name: String) = activity.getAttributeNS(ANDROID, name)

    @Test
    fun theActivityHandlesEveryChangeThatResizesTheWindow() {
        val handled = attribute("configChanges").split("|").toSet()
        val required = setOf(
            "orientation",
            "screenSize",
            "smallestScreenSize",
            "screenLayout",
            "keyboard",
            "keyboardHidden",
            "density"
        )
        assertTrue(handled.containsAll(required), "missing: ${required - handled}")
    }

    @Test
    fun theActivityIsExplicitlyResizeable() {
        assertEquals("true", attribute("resizeableActivity"))
    }

    @Test
    fun theKeyboardResizesTheWindow() {
        assertEquals("adjustResize", attribute("windowSoftInputMode"))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
