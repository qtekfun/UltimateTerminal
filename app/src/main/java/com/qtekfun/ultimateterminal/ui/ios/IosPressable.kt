// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

/**
 * A touch target that highlights while pressed, the way iOS rows and buttons do, instead of a
 * Material ripple. [content] is told whether it is pressed so it can fade or tint itself.
 */
@Composable
fun IosPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    pressedColor: Color = Color.Transparent,
    haptic: Boolean = true,
    content: @Composable (pressed: Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberIosHaptics()
    Box(
        modifier
            .background(if (pressed && enabled) pressedColor else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = role,
                onClickLabel = onClickLabel
            ) {
                if (haptic) haptics.selection()
                onClick()
            }
    ) { content(pressed && enabled) }
}
