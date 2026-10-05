// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

private const val SPRING_DAMPING = 0.8f
private const val START_SCALE = 0.85f

/**
 * A menu that pops out of its anchor: put it inside the same `Box` as the control that opens it.
 * It springs open from its top-right corner. Its items are [IosMenuItem]s.
 */
@Composable
fun IosContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    val colors = IosTheme.colors
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn() + scaleIn(
                initialScale = START_SCALE,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = spring(
                    dampingRatio = SPRING_DAMPING,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            Column(
                modifier
                    .padding(IosSpacing.sm)
                    .width(IosSize.menuWidth)
                    .shadow(elevation = IosSpacing.md, shape = RoundedCornerShape(IosRadius.alert))
                    .clip(RoundedCornerShape(IosRadius.alert))
                    .background(colors.cell),
                content = content
            )
        }
    }
}

/** One item of an [IosContextMenu]: its label, and its icon at the trailing edge as iOS does. */
@Composable
fun IosMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: IosGlyph? = null,
    destructive: Boolean = false,
    showSeparator: Boolean = true
) {
    val colors = IosTheme.colors
    val color = if (destructive) colors.destructive else colors.label
    Column(modifier.fillMaxWidth()) {
        IosPressable(
            onClick,
            Modifier.fillMaxWidth(),
            role = Role.Button,
            pressedColor = colors.pressedCell
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(
                    min = IosSize.minTouch
                ).padding(horizontal = IosSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosText(label, modifier = Modifier.weight(1f), color = color)
                if (glyph != null) IosIcon(glyph, null, tint = color, size = 20.dp)
            }
        }
        if (showSeparator) Separator()
    }
}
