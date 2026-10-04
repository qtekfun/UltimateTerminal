// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

private const val SPRING_DAMPING = 0.75f
private const val DISABLED_ALPHA = 0.4f
private val Thumb = Color.White

/**
 * The iOS switch: a green track with a white thumb that springs across. With a null
 * [onCheckedChange] it only shows the state, for a row that is itself the touch target.
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = IosTheme.colors
    val haptics = rememberIosHaptics()
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = SPRING_DAMPING, stiffness = Spring.StiffnessMedium),
        label = "switch"
    )
    val description = switchStateDescription(checked)
    val touch = if (onCheckedChange != null) {
        Modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    if (it) haptics.toggleOn() else haptics.toggleOff()
                    onCheckedChange(it)
                }
            )
            .semantics { stateDescription = description }
    } else {
        Modifier
    }
    Box(
        modifier
            .then(touch)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .height(IosSize.minTouch)
            .width(IosSize.switchWidth),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(IosSize.switchWidth)
                .height(IosSize.switchHeight)
                .clip(CircleShape)
                .background(lerp(colors.fill, colors.switchOn, progress))
        ) {
            val travel = IosSize.switchWidth - IosSize.switchThumb - IosSize.switchPadding * 2
            Box(
                Modifier
                    .offset {
                        val x = IosSize.switchPadding + lerp(0.dp, travel, progress)
                        IntOffset(x.roundToPx(), IosSize.switchPadding.roundToPx())
                    }
                    .size(IosSize.switchThumb)
                    .shadow(elevation = 2.dp, shape = CircleShape)
                    .background(Thumb, CircleShape)
            )
        }
    }
}
