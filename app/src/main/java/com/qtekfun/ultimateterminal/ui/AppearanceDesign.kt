// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.appearance.ChromeStyle
import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import java.util.Locale

private val MinTouch = 48.dp

/** The margin, the corners, the cursor and the style of the bars. */
@Composable
internal fun DesignSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val appearance = settings.appearance
    fun change(transform: (TerminalAppearance) -> TerminalAppearance) =
        update { it.copy(appearance = transform(it.appearance)) }

    SectionTitle(stringResource(R.string.appearance_section_design))
    LabeledSlider(
        label = stringResource(R.string.appearance_margin),
        valueText = "${appearance.marginDp} dp",
        value = appearance.marginDp.toFloat(),
        range = TerminalAppearance.MARGIN_RANGE.asFloats()
    ) { value -> change { it.copy(marginDp = value.toInt()) } }
    LabeledSlider(
        label = stringResource(R.string.appearance_corner),
        valueText = "${appearance.cornerRadiusDp} dp",
        value = appearance.cornerRadiusDp.toFloat(),
        range = TerminalAppearance.CORNER_RANGE.asFloats()
    ) { value -> change { it.copy(cornerRadiusDp = value.toInt()) } }

    Text(stringResource(R.string.appearance_cursor))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CursorShape.entries.forEach { shape ->
            FilterChip(
                selected = appearance.cursorShape == shape,
                onClick = { change { it.copy(cursorShape = shape) } },
                label = { Text(stringResource(cursorLabel(shape))) },
                modifier = Modifier.heightIn(min = MinTouch)
            )
        }
    }
    SettingSwitch(
        title = stringResource(R.string.appearance_cursor_blink),
        hint = null,
        checked = appearance.cursorBlink
    ) { checked -> change { it.copy(cursorBlink = checked) } }

    Text(stringResource(R.string.appearance_chrome))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChromeStyle.entries.forEach { style ->
            FilterChip(
                selected = appearance.chromeStyle == style,
                onClick = { change { it.copy(chromeStyle = style) } },
                label = { Text(stringResource(chromeLabel(style))) },
                modifier = Modifier.heightIn(min = MinTouch)
            )
        }
    }
    OutlinedButton(
        onClick = { change { TerminalAppearance(fontId = it.fontId) } },
        modifier = Modifier.heightIn(min = MinTouch)
    ) { Text(stringResource(R.string.appearance_reset)) }
}

private fun cursorLabel(shape: CursorShape): Int = when (shape) {
    CursorShape.BLOCK -> R.string.appearance_cursor_block
    CursorShape.UNDERLINE -> R.string.appearance_cursor_underline
    CursorShape.BAR -> R.string.appearance_cursor_bar
}

private fun chromeLabel(style: ChromeStyle): Int = when (style) {
    ChromeStyle.SCHEME -> R.string.appearance_chrome_scheme
    ChromeStyle.SYSTEM -> R.string.appearance_chrome_system
}

/** A number with its unit, for a slider's value. */
internal fun decimal(value: Float, digits: Int = 2): String =
    String.format(Locale.getDefault(), "%.${digits}f", value)

private fun IntRange.asFloats(): ClosedFloatingPointRange<Float> = first.toFloat()..last.toFloat()
