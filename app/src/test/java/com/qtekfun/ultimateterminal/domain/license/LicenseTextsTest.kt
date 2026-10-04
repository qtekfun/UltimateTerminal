// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.license

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LicenseTextsTest {
    private class MapSource(private val texts: Map<String, String>) : LicenseSource {
        override fun read(name: String): String? = texts[name]
    }

    /** The files as the app bundles them: `raw` resources and `assets`, read from the source tree. */
    private class BundledSource : LicenseSource {
        private val main = File("src/main")

        override fun read(name: String): String? = when {
            name.startsWith("raw/") -> File(main, "res/raw").listFiles()
                ?.firstOrNull { it.nameWithoutExtension == name.removePrefix("raw/") }

            else -> File(main, name)
        }?.takeIf { it.isFile }?.readText()
    }

    @Test
    fun everyListedNoticeIsBundledInTheSourceTree() {
        val texts = LicenseTexts(BundledSource())

        assertEquals(emptyList<LicenseEntry>(), texts.missing())
        texts.entries.forEach { assertTrue(texts.read(it.id)!!.length > 100, it.id) }
    }

    @Test
    fun theApacheTextCarriesEveryLibraryThatPublishesANotice() {
        val text = LicenseTexts(BundledSource()).read("apache-notices")!!

        listOf(
            "Apache Commons Compress",
            "Apache Commons IO",
            "Apache Commons Codec",
            "Apache Commons Lang",
            "Jakarta Dependency Injection"
        ).forEach { assertTrue(text.contains(it), it) }
        assertTrue(text.contains("Apache License"))
    }

    @Test
    fun anUnknownIdHasNoText() {
        assertNull(LicenseTexts(MapSource(emptyMap())).read("nope"))
    }

    @Test
    fun aMissingTextIsReportedAndStillListed() {
        val texts = LicenseTexts(MapSource(mapOf(LicenseTexts.APACHE_RESOURCE to "notice")))

        assertEquals("notice", texts.read("apache-notices"))
        assertNull(texts.read("inter-ofl"))
        assertTrue(texts.missing().any { it.id == "inter-ofl" })
        assertTrue(texts.entries.any { it.id == "inter-ofl" })
    }

    @Test
    fun aBlankTextCountsAsMissing() {
        val texts = LicenseTexts(MapSource(mapOf(LicenseTexts.APACHE_RESOURCE to "  \n")))

        assertTrue(texts.missing().any { it.id == "apache-notices" })
    }

    @Test
    fun entryIdsAreUniqueAndEveryOneHasALicense() {
        val entries = LicenseTexts(MapSource(emptyMap())).entries

        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        entries.forEach { assertNotNull(it.license.takeIf(String::isNotBlank)) }
    }
}
