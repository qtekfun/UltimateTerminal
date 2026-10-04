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
 * Guards what the foreground service that keeps the shells alive needs (SPEC RF-07): without the
 * declared type or the permissions Android refuses to start it, and a service that anyone could
 * start would let other apps open shells.
 */
class ManifestServiceTest {
    private val document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))

    private val service: Element = run {
        val services = document.getElementsByTagName("service")
        (0 until services.length).map { services.item(it) as Element }
            .single { it.getAttributeNS(ANDROID, "name") == ".terminal.SessionService" }
    }

    private val permissions: Set<String> = run {
        val nodes = document.getElementsByTagName("uses-permission")
        (0 until nodes.length).map {
            (nodes.item(it) as Element).getAttributeNS(ANDROID, "name")
        }.toSet()
    }

    @Test
    fun theServiceIsPrivateToTheApp() {
        assertEquals("false", service.getAttributeNS(ANDROID, "exported"))
    }

    @Test
    fun theServiceDeclaresItsForegroundType() {
        assertEquals("specialUse", service.getAttributeNS(ANDROID, "foregroundServiceType"))
        val property = service.getElementsByTagName("property").item(0) as Element
        assertEquals(
            "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
            property.getAttributeNS(ANDROID, "name")
        )
        assertTrue(property.getAttributeNS(ANDROID, "value").isNotBlank())
    }

    @Test
    fun theAppDeclaresThePermissionsTheServiceNeeds() {
        val required = setOf(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.WAKE_LOCK"
        )
        assertTrue(permissions.containsAll(required), "missing: ${required - permissions}")
    }

    @Test
    fun theBatteryExemptionIsAskedWithTheSystemDialogAndNothingBroaderIsDeclared() {
        // D-T08c-1 (replaces D-T08-4): the app opens the system dialog that asks to run without
        // battery optimisation, which needs this permission. It changes nothing unless the user accepts.
        assertTrue("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in permissions)
        // Nothing that would let the app change power or all-files access on its own.
        val broader = setOf(
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.WRITE_SETTINGS",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.SYSTEM_ALERT_WINDOW"
        )
        assertTrue(permissions.none { it in broader }, "declared: ${permissions intersect broader}")
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
