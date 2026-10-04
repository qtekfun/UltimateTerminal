// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.terminal.EdgeInsets

/** What the tab bar shows for one tab. */
data class TabItem(
    val id: SessionId,
    /** What the user typed; null shows the default name. */
    val title: String?,
    /** Place in the bar, counting from 1. */
    val position: Int,
    val running: Boolean,
    /** Name of the distro the tab was opened in; null for the Android shell. */
    val distroName: String?,
    val active: Boolean
)

fun tabItems(sessions: Sessions, distros: List<Distro>): List<TabItem> =
    sessions.items.mapIndexed { index, session ->
        TabItem(
            id = session.id,
            title = session.title,
            position = index + 1,
            running = session.state == SessionState.Running,
            distroName = distros.firstOrNull { it.id == session.distroId }?.name,
            active = session.id == sessions.activeId
        )
    }

/** A place a new tab can open in. [id] null is the Android shell, the only one before T07. */
data class DistroOption(val id: Long?, val name: String?)

/** The Android shell first, then the installed distros, the default one first. */
fun distroOptions(distros: List<Distro>): List<DistroOption> =
    listOf(DistroOption(id = null, name = null)) +
        distros.filter { it.state == DistroState.READY }
            .sortedByDescending { it.isDefault }
            .map { DistroOption(it.id, it.name) }

/** Where a new tab opens when the user does not choose: the default distro, if it is usable. */
fun defaultDistroId(distros: List<Distro>): Long? =
    distros.firstOrNull { it.isDefault && it.state == DistroState.READY }?.id

enum class TabBarPlacement { Top, Side }

/** From this window width on (the "medium" class of the Material guidelines) the bar is a column. */
const val WIDE_WINDOW_DP = 600

fun tabBarPlacement(windowWidthDp: Int): TabBarPlacement =
    if (windowWidthDp >= WIDE_WINDOW_DP) TabBarPlacement.Side else TabBarPlacement.Top

/** The insets with the tab bar added, so the terminal grid does not count the space it takes. */
fun EdgeInsets.reserveForTabBar(placement: TabBarPlacement, thicknessPx: Int): EdgeInsets =
    when (placement) {
        TabBarPlacement.Top -> copy(top = top + thicknessPx)
        TabBarPlacement.Side -> copy(left = left + thicknessPx)
    }

/**
 * The position a tab dragged by [dragOffsetPx] from position [from] lands on, given the size of
 * every tab along the bar ([sizesPx], in order): the one under the middle of the dragged tab.
 */
fun dropIndex(from: Int, dragOffsetPx: Float, sizesPx: List<Float>): Int {
    require(from in sizesPx.indices) { "no tab at position $from" }
    val middle = sizesPx.take(from).sum() + sizesPx[from] / 2f + dragOffsetPx
    val ends = sizesPx.runningFold(0f) { total, size -> total + size }.drop(1)
    return ends.indexOfFirst { middle < it }.takeIf { it >= 0 } ?: sizesPx.lastIndex
}
