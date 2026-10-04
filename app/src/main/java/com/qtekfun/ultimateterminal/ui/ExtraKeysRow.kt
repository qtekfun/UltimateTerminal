// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKey
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeyAction
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.LatchState
import com.qtekfun.ultimateterminal.domain.terminal.StickyState

/** The touch target size: 48 dp is the accessibility minimum. */
internal val ExtraKeyRowHeight = 48.dp

/** The extra-keys row (Esc, Tab, Ctrl, arrows...) above the keyboard. Not validated on a device. */
@Composable
fun ExtraKeysRow(
    config: ExtraKeysConfig,
    sticky: StickyState,
    onKey: (ExtraKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        config.resolved().forEach { row ->
            Row(Modifier.fillMaxWidth().height(ExtraKeyRowHeight)) {
                row.forEach { key ->
                    ExtraKeyButton(key, latchOf(key, sticky), onKey, Modifier.weight(1f))
                }
            }
        }
    }
}

private fun latchOf(key: ExtraKey, sticky: StickyState): LatchState =
    (key.action as? ExtraKeyAction.Modifier)?.let { sticky[it.key] } ?: LatchState.OFF

@Composable
private fun ExtraKeyButton(
    key: ExtraKey,
    latch: LatchState,
    onKey: (ExtraKey) -> Unit,
    modifier: Modifier
) {
    val name = extraKeyDescription(key.id)?.let { stringResource(it) } ?: key.symbol
    val state = when (latch) {
        LatchState.OFF -> null
        LatchState.ARMED -> stringResource(R.string.extra_key_armed)
        LatchState.LOCKED -> stringResource(R.string.extra_key_locked)
    }
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (latch) {
        LatchState.OFF -> Color.Transparent to colors.onSurface
        LatchState.ARMED -> colors.primaryContainer to colors.onPrimaryContainer
        LatchState.LOCKED -> colors.primary to colors.onPrimary
    }
    Box(
        modifier
            .fillMaxHeight()
            .background(background)
            .semantics {
                contentDescription = name
                if (state != null) stateDescription = state
            }
            .clickable(role = Role.Button) { onKey(key) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key.symbol,
            color = foreground,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
    }
}
