// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real MainActivity with UI Automator (skipped when the first-run setup shows
 * instead): Back from Settings returns to the terminal, and
 * the Profiles and Keyboard shortcuts screens (and the sheets they open) do not crash. Labels come
 * from the app's own string resources, so the tests run in any language. Nothing here changes
 * data: it only opens and closes screens (the profile editor is never saved).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val pkg = context.packageName
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launch() {
        device.wakeUp()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // On a device with no distro the first-run setup (RF-15) is shown instead of a terminal:
        // these tests need the terminal, so they are skipped there, not failed.
        Assume.assumeFalse(
            "the first-run setup is showing: this device has no distro",
            device.wait(Until.hasObject(text(R.string.setup_skip)), SETUP_WAIT_MS)
        )
        // Android 13+ may ask for the notification permission when the first session starts.
        device.wait(Until.findObject(By.res(Pattern.compile(".*permission_deny_button"))), 2_000)
            ?.click()
        // The "keep sessions alive" prompt (notifications or battery) is a modal alert shown over
        // the terminal on a fresh install; while it is up, the screen below is hidden from UI
        // Automator. "Not now" changes no setting.
        device.wait(Until.findObject(text(R.string.prompt_not_now)), PROMPT_WAIT_MS)?.click()
        assertNotNull("the terminal screen did not appear", awaitDesc(R.string.settings_open))
    }

    @After
    fun close() = scenario.close()

    private fun text(res: Int) = By.text(context.getString(res)).pkg(pkg)

    private fun desc(res: Int) = By.desc(context.getString(res)).pkg(pkg)

    private fun awaitDesc(res: Int) = device.wait(Until.findObject(desc(res)), WAIT_MS)

    private fun awaitText(res: Int) = device.wait(Until.findObject(text(res)), WAIT_MS)

    private fun openSettings() {
        requireNotNull(awaitDesc(R.string.settings_open)).click()
        assertNotNull("Settings did not open", awaitText(R.string.settings_done))
    }

    /** Presses Back, at most [MAX_BACKS] times, until [reached] says the wanted screen is there. */
    private fun backUntil(reached: () -> Boolean): Boolean {
        repeat(MAX_BACKS) {
            if (reached()) return true
            device.pressBack()
            Thread.sleep(BACK_PAUSE_MS)
        }
        return reached()
    }

    private fun onSettings() = device.hasObject(text(R.string.settings_done))

    private fun onTerminal() = device.hasObject(desc(R.string.settings_open))

    private fun assertAppIsAlive() {
        assertEquals("the app is not in front: it crashed or left", pkg, device.currentPackageName)
    }

    @Test
    fun backFromSettingsReturnsToTheTerminal() {
        openSettings()

        device.pressBack()

        assertNotNull("the terminal is not back", awaitDesc(R.string.settings_open))
        assertNull(device.findObject(text(R.string.settings_done)))
        assertAppIsAlive()
    }

    @Test
    fun theProfilesScreenAndItsEditorSheetOpenWithoutCrashing() {
        openSettings()

        requireNotNull(awaitText(R.string.settings_profiles)).click()
        assertNotNull("Profiles did not open", awaitText(R.string.profiles_title))
        requireNotNull(awaitDesc(R.string.profiles_add)).click()
        assertNotNull("the editor sheet did not open", awaitText(R.string.profiles_save))
        assertAppIsAlive()

        // Back closes the sheet, then the screen, and Settings is underneath.
        assertTrue("Settings is not back", backUntil(::onSettings))
        assertAppIsAlive()
    }

    @Test
    fun theShortcutsScreenAndItsSheetOpenWithoutCrashing() {
        openSettings()

        requireNotNull(awaitText(R.string.settings_section_keyboard)).click()
        requireNotNull(awaitText(R.string.settings_shortcuts)).click()
        // The first row of the list; the Restore button is far below the fold.
        requireNotNull(awaitText(R.string.shortcut_new_tab)).click()
        assertAppIsAlive()

        assertTrue("the terminal is not back", backUntil(::onTerminal))
        assertAppIsAlive()
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val PROMPT_WAIT_MS = 3_000L
        const val SETUP_WAIT_MS = 2_000L
        const val MAX_BACKS = 6
        const val BACK_PAUSE_MS = 400L
    }
}
