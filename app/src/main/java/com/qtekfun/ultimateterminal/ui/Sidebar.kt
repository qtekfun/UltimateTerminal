// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.session.SidebarEvent
import com.qtekfun.ultimateterminal.domain.session.SidebarMode
import com.qtekfun.ultimateterminal.domain.session.SidebarState
import com.qtekfun.ultimateterminal.domain.session.SidebarWidths

private const val SIDEBAR_ANIMATION_MILLIS = 200

/** The width of the side tab bar once it has stopped moving, open or as a rail. */
internal fun sidebarWidth(open: Boolean): Dp = SidebarWidths.targetDp(open).dp

/**
 * Whether the side tab bar is open, and what asks to change that. The rules are in the domain
 * ([SidebarState]); this keeps the value across rotations and gives the events a place to go.
 */
internal class SidebarHandle(
    val open: Boolean,
    val mode: SidebarMode,
    private val set: (Boolean) -> Unit
) {
    /** In the always-open mode there is nothing to collapse, so no control for it is shown. */
    val collapsible: Boolean get() = mode == SidebarMode.AUTO_COLLAPSE

    fun send(event: SidebarEvent) = set(SidebarState.expandedAfter(event, mode))
}

@Composable
internal fun rememberSidebar(mode: SidebarMode): SidebarHandle {
    var expanded by rememberSaveable { mutableStateOf(true) }
    // Leaving the always-open mode must not bring back a collapsed state from before it.
    LaunchedEffect(mode) { expanded = SidebarState.onMode(expanded, mode) }
    return SidebarHandle(SidebarState.onMode(expanded, mode), mode) { expanded = it }
}

/**
 * The width of the bar as it animates to [open]'s width, in 200 ms; at once when the system has
 * its animations removed (Settings > Accessibility > Remove animations, or the developer scale
 * set to off).
 */
@Composable
internal fun animatedSidebarWidth(open: Boolean): Dp {
    val resolver = LocalContext.current.contentResolver
    val animate = remember(open) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val width by animateDpAsState(
        targetValue = sidebarWidth(open),
        animationSpec = if (animate) tween(SIDEBAR_ANIMATION_MILLIS) else snap(),
        label = "sidebar width"
    )
    return width
}

/**
 * What the side bar can do about its own width. [collapsed]: it is drawn as a rail of initials.
 * [onCollapse] is null when the user chose the always-open mode, and then no control collapses it.
 */
class SidebarControl(
    val collapsed: Boolean,
    val onExpand: () -> Unit,
    val onCollapse: (() -> Unit)?
) {
    companion object {
        /** The top bar has none of this. */
        val None = SidebarControl(collapsed = false, onExpand = {}, onCollapse = null)
    }
}

/** The label and the effect of the screen reader action that opens or closes the side bar. */
internal class SidebarAction(val label: String, val run: () -> Unit)

@Composable
internal fun sidebarActionFor(vertical: Boolean, sidebar: SidebarControl): SidebarAction? = when {
    !vertical -> null
    sidebar.collapsed -> SidebarAction(stringResource(R.string.sidebar_expand), sidebar.onExpand)
    else -> sidebar.onCollapse?.let { SidebarAction(stringResource(R.string.sidebar_collapse), it) }
}
