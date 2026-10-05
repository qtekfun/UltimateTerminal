// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.appearance.FontCatalog
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.ProfileRepository
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.session.PaneEditor
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import kotlinx.coroutines.flow.first

/** Where a pane opened with a profile goes. */
enum class OpenAs { NEW_TAB, SPLIT_RIGHT, SPLIT_DOWN }

/** What opening a pane with a profile did. */
sealed interface OpenResult {
    /** It is open. [notices] say what had to change for it to open (a gone font, no ready distro). */
    data class Opened(val notices: List<PaneNotice>) : OpenResult

    /** The profile cannot open as it is (its distro is gone, its command is not one line...). */
    data class Rejected(val problem: ProfileProblem) : OpenResult

    /** A split was asked for and there is no tab to split. */
    data object NothingToSplit : OpenResult
}

/** What opening a saved layout did. */
sealed interface RestoreResult {
    /** The layout is open as a new tab; [notices] say where it differs from what was saved. */
    data class Opened(val notices: List<LayoutNotice>) : RestoreResult

    /** The layout is not a layout to trust (SPEC RF-12): too many panes or too deep. */
    data class Refused(val reason: LayoutRefusal) : RestoreResult
}

/**
 * Opens panes from profiles and layouts: it works out what each pane is ([PaneSpecResolver],
 * [LayoutRestorePlanner]) from what exists now, and hands the plan to the session owner. The
 * decisions are in those pure classes; this only reads the current distros, profiles and settings
 * and connects them, so it is tested with the real [SessionController][com.qtekfun.ultimateterminal.domain.session.SessionController]
 * and an in-memory repository.
 */
class PaneOpener(
    private val editor: PaneEditor,
    private val profiles: ProfileRepository,
    private val distros: DistroRepository,
    private val settings: SettingsRepository
) {
    /** Opens [profile] (null: the defaults) as [how]. */
    suspend fun open(profile: Profile?, how: OpenAs): OpenResult =
        when (val resolution = resolver().resolve(profile)) {
            is PaneResolution.Rejected -> OpenResult.Rejected(resolution.problem)

            is PaneResolution.Ready -> if (carryOut(resolution.spec, how)) {
                OpenResult.Opened(resolution.notices)
            } else {
                OpenResult.NothingToSplit
            }
        }

    private fun carryOut(spec: PaneSpec, how: OpenAs): Boolean {
        val opening = PaneOpening(spec)
        return when (how) {
            OpenAs.NEW_TAB -> {
                editor.openTab(PlannedNode.Pane(spec))
                true
            }

            OpenAs.SPLIT_RIGHT -> editor.splitActive(SplitOrientation.VERTICAL, opening) != null

            OpenAs.SPLIT_DOWN -> editor.splitActive(SplitOrientation.HORIZONTAL, opening) != null
        }
    }

    /** Opens [layout] as a new tab, every pane as saved or, if it cannot, plainly with a notice. */
    suspend fun restore(layout: Layout): RestoreResult {
        val known = profiles.observeAll().first().associateBy { it.id }
        return when (val restore = LayoutRestorePlanner(resolver(), known).plan(layout)) {
            is LayoutRestore.Refused -> RestoreResult.Refused(restore.reason)

            is LayoutRestore.Ready -> {
                editor.openTab(restore.plan.root)
                RestoreResult.Opened(restore.plan.notices)
            }
        }
    }

    /** A resolver for what exists right now: distros, color schemes and fonts. */
    private suspend fun resolver(): PaneSpecResolver {
        val app = settings.observe().first()
        return PaneSpecResolver(
            distros = distros.observeAll().first(),
            knownSchemeIds = (BuiltInSchemes.all + app.customSchemes).map { it.id }.toSet(),
            knownFontIds = app.customFonts.map { it.id }.toSet() + FontCatalog.BUNDLED_ID,
            defaultScrollbackLines = app.defaultScrollbackLines
        )
    }
}
