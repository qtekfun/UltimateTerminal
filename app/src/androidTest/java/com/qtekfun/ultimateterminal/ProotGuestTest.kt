// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.support.GuestTerminal
import com.qtekfun.ultimateterminal.support.TestStorage
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * proot on a real pty, with the app's own libproot.so and an Alpine root filesystem that the
 * app's installer downloads from the official source and verifies (SHA-256). Needs network (the
 * tests are skipped without it) and a device or emulator; see docs/TESTING.md.
 *
 * Alpine is installed once for the class, in a private directory that is deleted afterwards; the
 * distros the user installed are never touched.
 */
@RunWith(AndroidJUnit4::class)
class ProotGuestTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var terminal: GuestTerminal

    @Before
    fun openTerminal() {
        terminal = GuestTerminal(instrumentation.targetContext, instrumentation)
        terminal.start(storage.launchOf(distro), GridSize(INITIAL_COLUMNS, INITIAL_ROWS))
    }

    @After
    fun closeTerminal() = terminal.close()

    @Test
    fun theGuestRunsCommandsAndReportsItsOwnKernelAndDistro() {
        terminal.type("uname -a")
        terminal.type("echo ok")
        terminal.type("cat /etc/os-release")

        assertTrue("no uname output", terminal.awaitLine { it.startsWith("Linux ") })
        // The typed line is "... # echo ok"; only the command's output is exactly "ok".
        assertTrue("no echo output", terminal.awaitLine { it == "ok" })
        assertTrue("not Alpine", terminal.awaitLine { it == "ID=alpine" })
    }

    @Test
    fun theGuestSeesTheSizeOfThePtyAndFollowsItsResizes() {
        terminal.type("stty size")
        assertTrue("initial size", terminal.awaitLine { it == "$INITIAL_ROWS $INITIAL_COLUMNS" })

        terminal.resize(GridSize(columns = 100, rows = 30))
        terminal.type("stty size")
        assertTrue("after the first resize", terminal.awaitLine { it == "30 100" })

        terminal.resize(GridSize(columns = 60, rows = 12))
        terminal.type("stty size")
        assertTrue("after the second resize", terminal.awaitLine { it == "12 60" })
    }

    companion object {
        private const val INITIAL_COLUMNS = 80
        private const val INITIAL_ROWS = 24
        private var storageOrNull: TestStorage? = null
        private var distroOrNull: Distro? = null
        private val storage get() = checkNotNull(storageOrNull)
        private val distro get() = checkNotNull(distroOrNull)

        @BeforeClass
        @JvmStatic
        fun installAlpine() {
            val created = TestStorage(InstrumentationRegistry.getInstrumentation().targetContext)
            storageOrNull = created
            distroOrNull = created.installAlpine()
        }

        @AfterClass
        @JvmStatic
        fun deleteEverything() {
            storageOrNull?.close()
            storageOrNull = null
        }
    }
}
