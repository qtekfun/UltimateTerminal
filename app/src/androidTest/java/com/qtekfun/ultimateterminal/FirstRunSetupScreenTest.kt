// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test of the first-run setup screen (RF-15). It only looks at the screen: nothing is
 * installed, restored or skipped. A device that already has a distro never shows the screen, so
 * there the test is skipped (an assumption, not a failure); run it on a fresh install.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunSetupScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launch() {
        device.wakeUp()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Assume.assumeTrue(
            "this device already has a distro, so the first-run setup is not shown",
            device.wait(Until.hasObject(text(R.string.setup_title)), WAIT_MS)
        )
    }

    @After
    fun close() = scenario.close()

    private fun text(res: Int) = By.text(context.getString(res)).pkg(context.packageName)

    @Test
    fun theSetupOffersTheDistributionsWithAlpineRecommended() {
        assertNotNull(device.findObject(text(R.string.family_alpine)))
        assertNotNull(device.findObject(text(R.string.setup_note_alpine)))
        assertNotNull(device.findObject(text(R.string.family_debian)))
    }

    @Test
    fun theTerminalIsNotUnderTheSetup() {
        val settingsIcon = By.desc(context.getString(R.string.settings_open))
        assertFalse("a terminal is showing behind the setup", device.hasObject(settingsIcon))
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
