// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.appearance.ExtraKeyPalette
import com.qtekfun.ultimateterminal.domain.appearance.KeyLook
import com.qtekfun.ultimateterminal.domain.appearance.look
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKey
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeyAction
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeyFit
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.LatchState
import com.qtekfun.ultimateterminal.domain.terminal.StickyState
import com.qtekfun.ultimateterminal.ui.ios.IosPressable
import com.qtekfun.ultimateterminal.ui.ios.IosSize
import com.qtekfun.ultimateterminal.ui.ios.IosText
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

/** The touch target size: 48 dp is the accessibility minimum. */
internal val ExtraKeyRowHeight = 48.dp

private val EdgeLine = 1.dp
private val IndicatorWidth = 14.dp
private val IndicatorHeight = 2.dp
private val IndicatorLift = 6.dp
private const val SEPARATOR_INSET = 0.25f
private const val HALF = 0.5f

/** How one key is dressed: the palette, the corner of a rounded cap, and whether a hairline follows it. */
private class RowLook(val palette: ExtraKeyPalette, val corner: Dp, val divider: Boolean)

/**
 * The extra-keys row (Esc, Tab, Ctrl, arrows...) above the keyboard, in the style the user picked
 * (see `ExtraKeyStyle`): flat symbols with hairlines, soft capsules or keyboard-like caps, always in
 * the colors of the scheme. Every row is 48 dp tall, the touch target a finger needs.
 */
@Composable
fun ExtraKeysRow(
    config: ExtraKeysConfig,
    sticky: StickyState,
    onKey: (ExtraKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val chrome = currentChrome()
    // The rows keep their 48 dp, so the drawn labels stop following the system font at a cap.
    val density = LocalDensity.current
    val capped = Density(density.density, ExtraKeyFit.labelFontScale(density.fontScale))
    CompositionLocalProvider(LocalDensity provides capped) {
        ExtraKeysColumn(config, sticky, onKey, modifier, chrome)
    }
}

@Composable
private fun ExtraKeysColumn(
    config: ExtraKeysConfig,
    sticky: StickyState,
    onKey: (ExtraKey) -> Unit,
    modifier: Modifier,
    chrome: ChromePalette
) {
    val palette = chrome.keys
    val hairline = IosSize.hairline
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(palette.tray))
            .drawBehind {
                drawLine(chrome.outline, Offset(0f, 0f), Offset(size.width, 0f), hairline.toPx())
            }
    ) {
        config.resolved().forEachIndexed { rowIndex, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ExtraKeyRowHeight)
                    .rowSeparator(palette, rowIndex > 0)
            ) {
                row.forEachIndexed { index, key ->
                    ExtraKeyButton(
                        key = key,
                        latch = latchOf(key, sticky),
                        look = RowLook(
                            palette,
                            chrome.corner,
                            divider = palette.style.separators && index < row.lastIndex
                        ),
                        onKey = onKey,
                        modifier = Modifier.weight(1f)
                    )
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
    look: RowLook,
    onKey: (ExtraKey) -> Unit,
    modifier: Modifier
) {
    val palette = look.palette
    val name = extraKeyDescription(key.id)?.let { stringResource(it) } ?: key.symbol
    val state = when (latch) {
        LatchState.OFF -> null
        LatchState.ARMED -> stringResource(R.string.extra_key_armed)
        LatchState.LOCKED -> stringResource(R.string.extra_key_locked)
    }
    // The whole cell is the touch target; the cap is inset so the keys do not touch.
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
        val keyLook = palette.look(latch, pressed)
        Box(
            Modifier
                .fillMaxSize()
                .keySeparator(palette, look.divider)
                .padding(palette.style.insetDp.dp)
                .keyCap(keyLook, palette, look.corner),
            contentAlignment = Alignment.Center
        ) {
            KeyLabel(key.symbol, keyLook)
        }
    }
}

@Composable
private fun KeyLabel(symbol: String, look: KeyLook) {
    val color = Color(look.label)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IosText(
            text = symbol,
            style = IosTheme.typography.footnote.copy(fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1
        )
        if (look.indicator) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = IndicatorLift)
                    .size(IndicatorWidth, IndicatorHeight)
                    .background(color, RoundedCornerShape(IndicatorHeight / 2))
            )
        }
    }
}

/** The cap behind a key: a pill or a rounded rectangle, with the edge under it when the style has one. */
private fun Modifier.keyCap(look: KeyLook, palette: ExtraKeyPalette, corner: Dp): Modifier =
    if (look.cap == 0) {
        this
    } else {
        drawBehind {
            val radius = CornerRadius(if (palette.style.pill) size.height / 2f else corner.toPx())
            val line = if (palette.edge != 0) EdgeLine.toPx() else 0f
            if (line > 0f) {
                drawRoundRect(
                    Color(palette.edge),
                    Offset(0f, line),
                    Size(size.width, size.height),
                    radius
                )
            }
            drawRoundRect(
                Color(look.cap),
                Offset.Zero,
                Size(size.width, size.height - line),
                radius
            )
        }
    }

/** A hairline at the right edge of a key, over the middle half of its height. */
private fun Modifier.keySeparator(palette: ExtraKeyPalette, draw: Boolean): Modifier =
    if (!draw || palette.separator == 0) {
        this
    } else {
        drawBehind {
            val x = size.width - HALF
            val width = IosSize.hairline.toPx()
            val inset = size.height * SEPARATOR_INSET
            drawLine(
                Color(palette.separator),
                Offset(x, inset),
                Offset(x, size.height - inset),
                width
            )
        }
    }

/** A hairline across the top of a row, between it and the one above. */
private fun Modifier.rowSeparator(palette: ExtraKeyPalette, draw: Boolean): Modifier =
    if (!draw || palette.separator == 0) {
        this
    } else {
        drawBehind {
            drawLine(
                Color(palette.separator),
                Offset(0f, 0f),
                Offset(size.width, 0f),
                IosSize.hairline.toPx()
            )
        }
    }
