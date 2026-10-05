// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.setup

import com.qtekfun.ultimateterminal.domain.distro.DistroPaths
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirstRunSetupTest {
    private fun distro(id: Long, state: DistroState, isDefault: Boolean = false) = Distro(
        id = id,
        name = "d$id",
        type = DistroType.ALPINE,
        release = "3",
        directory = DistroPaths.distroDirectory("t$id"),
        defaultUser = "root",
        state = state,
        sizeBytes = 1L,
        installedAt = Instant.EPOCH,
        isDefault = isDefault
    )

    private fun inputs(
        loaded: Boolean = true,
        distros: List<Distro> = emptyList(),
        hasSessions: Boolean = false,
        skipped: Boolean = false,
        restoring: Boolean = false
    ) = SetupInputs(loaded, distros, hasSessions, skipped, restoring)

    @Test
    fun nothingIsDecidedBeforeTheDistrosAreRead() {
        assertEquals(
            SetupGate.UNDECIDED,
            FirstRunSetup.next(SetupGate.UNDECIDED, inputs(loaded = false))
        )
    }

    @Test
    fun withNoDistroAndNoTabTheSetupShows() {
        assertEquals(SetupGate.SHOWING, FirstRunSetup.next(SetupGate.UNDECIDED, inputs()))
    }

    @Test
    fun aLeftoverThatIsNotReadyDoesNotCountAsADistro() {
        val leftovers = listOf(distro(1, DistroState.FAILED), distro(2, DistroState.INSTALLING))

        assertEquals(
            SetupGate.SHOWING,
            FirstRunSetup.next(SetupGate.UNDECIDED, inputs(distros = leftovers))
        )
    }

    @Test
    fun aReadyDistroOrAnOpenTabOrASkipKeepTheSetupAway() {
        val ready = listOf(distro(1, DistroState.READY))

        assertEquals(
            SetupGate.CLOSED,
            FirstRunSetup.next(SetupGate.UNDECIDED, inputs(distros = ready))
        )
        assertEquals(
            SetupGate.CLOSED,
            FirstRunSetup.next(SetupGate.UNDECIDED, inputs(hasSessions = true))
        )
        assertEquals(
            SetupGate.CLOSED,
            FirstRunSetup.next(SetupGate.UNDECIDED, inputs(skipped = true))
        )
    }

    @Test
    fun onceShownItStaysUpWhileTheInstallRunsAndEvenIfATabAppears() {
        val installing = listOf(distro(1, DistroState.INSTALLING))

        assertEquals(
            SetupGate.SHOWING,
            FirstRunSetup.next(SetupGate.SHOWING, inputs(distros = installing))
        )
        assertEquals(
            SetupGate.SHOWING,
            FirstRunSetup.next(SetupGate.SHOWING, inputs(hasSessions = true))
        )
    }

    @Test
    fun itClosesWhenADistroIsReadyOrTheUserSkips() {
        assertEquals(
            SetupGate.CLOSED,
            FirstRunSetup.next(
                SetupGate.SHOWING,
                inputs(distros = listOf(distro(1, DistroState.READY)))
            )
        )
        assertEquals(
            SetupGate.CLOSED,
            FirstRunSetup.next(SetupGate.SHOWING, inputs(skipped = true))
        )
    }

    @Test
    fun onceClosedItNeverComesBackOverATab() {
        assertEquals(SetupGate.CLOSED, FirstRunSetup.next(SetupGate.CLOSED, inputs()))
    }

    @Test
    fun alpineIsPreselectedAndEveryFamilyOfTheCatalogIsOfferedOnce() {
        assertEquals(DistroFamily.ALPINE, FirstRunSetup.recommended)
        assertEquals(DistroFamily.entries.toSet(), FirstRunSetup.families.toSet())
        assertEquals(DistroFamily.entries.size, FirstRunSetup.families.size)
    }

    @Test
    fun aReadyDistroIsMadeTheDefaultOnlyWhenNoneIs() {
        val first = distro(1, DistroState.READY)
        val second = distro(2, DistroState.READY)

        assertEquals(second, FirstRunSetup.distroToMakeDefault(listOf(first, second)))
        assertNull(FirstRunSetup.distroToMakeDefault(listOf(first.copy(isDefault = true), second)))
        assertNull(FirstRunSetup.distroToMakeDefault(emptyList()))
        // A default that is not ready cannot open a tab.
        val failedDefault = distro(3, DistroState.FAILED, isDefault = true)
        assertEquals(first, FirstRunSetup.distroToMakeDefault(listOf(first, failedDefault)))
    }

    @Test
    fun theFormNeedsANameAndAUserThatUseraddAccepts() {
        assertTrue(FirstRunSetup.canInstall("Alpine", "root"))
        assertTrue(FirstRunSetup.canInstall("Alpine", " alice "))
        assertFalse(FirstRunSetup.canInstall("  ", "root"))
        assertFalse(FirstRunSetup.canInstall("Alpine", ""))
        assertFalse(FirstRunSetup.canInstall("Alpine", "-rf"))
    }

    @Test
    fun theSetupStaysWhileABackupIsBeingRestoredEvenWithADistroAlreadyReady() {
        val ready = listOf(distro(1, DistroState.READY))

        assertEquals(
            SetupGate.SHOWING,
            FirstRunSetup.next(SetupGate.SHOWING, inputs(distros = ready, restoring = true))
        )
        assertEquals(
            SetupGate.CLOSED,
            FirstRunSetup.next(SetupGate.SHOWING, inputs(distros = ready, restoring = false))
        )
        assertEquals(
            SetupGate.CLOSED,
            FirstRunSetup.next(SetupGate.SHOWING, inputs(skipped = true, restoring = true))
        )
    }
}
