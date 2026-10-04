// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKey
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeyAction
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.LatchState
import com.qtekfun.ultimateterminal.domain.terminal.StickyState
import com.qtekfun.ultimateterminal.ui.ios.IosPressable
import com.qtekfun.ultimateterminal.ui.ios.IosSize
import com.qtekfun.ultimateterminal.ui.ios.IosText
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

/** The touch target size: 48 dp is the accessibility minimum. */
internal val ExtraKeyRowHeight = 48.dp

private val KeyInset = 3.dp
private val KeyEdge = 1.dp
private const val ARMED_ALPHA = 0.4f

/**
 * The extra-keys row (Esc, Tab, Ctrl, arrows...) above the keyboard: rounded caps on a tray, in the
 * colors of the scheme, as the keys of a phone keyboard. Not validated on a device (T22b).
 */
@Composable
fun ExtraKeysRow(
    config: ExtraKeysConfig,
    sticky: StickyState,
    onKey: (ExtraKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val chrome = currentChrome()
    val hairline = IosSize.hairline
    Column(
        modifier
            .fillMaxWidth()
            .background(chrome.surface)
            .drawBehind {
                drawLine(chrome.outline, Offset(0f, 0f), Offset(size.width, 0f), hairline.toPx())
            }
    ) {
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
    val chrome = currentChrome()
    // The whole cell is the touch target; the cap is inset a little so the keys read as keys.
    IosPressable(
        onClick = { onKey(key) },
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                contentDescription = name
                if (state != null) stateDescription = state
            },
        role = Role.Button
    ) { pressed ->
        val (cap, text) = when (latch) {
            LatchState.OFF -> (if (pressed) chrome.keyPressed else chrome.key) to chrome.onKey
            LatchState.ARMED -> chrome.accent.copy(alpha = ARMED_ALPHA) to chrome.onSurface
            LatchState.LOCKED -> chrome.accent to chrome.onAccent
        }
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(KeyInset)
                .keyCap(cap, chrome.outline, chrome.corner),
            contentAlignment = Alignment.Center
        ) {
            IosText(
                text = key.symbol,
                style = IosTheme.typography.footnote.copy(fontWeight = FontWeight.Medium),
                color = text,
                maxLines = 1
            )
        }
    }
}

/** A key cap: the [color] with a thin [edge] under it, like the shadow line of a keyboard key. */
private fun Modifier.keyCap(color: Color, edge: Color, cornerRadius: Dp): Modifier = drawBehind {
    val corner = CornerRadius(cornerRadius.toPx())
    val line = KeyEdge.toPx()
    drawRoundRect(edge, Offset(0f, line), Size(size.width, size.height), corner)
    drawRoundRect(color, Offset.Zero, Size(size.width, size.height - line), corner)
}
