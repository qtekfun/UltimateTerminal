// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.appearance.ChromeStyle
import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import java.util.Locale

/** The margin, the corners, the cursor and the style of the bars. */
@Composable
internal fun DesignSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val appearance = settings.appearance
    fun change(transform: (TerminalAppearance) -> TerminalAppearance) =
        update { it.copy(appearance = transform(it.appearance)) }

    val cursors = CursorShape.entries
    val chromes = ChromeStyle.entries
    IosSection(header = stringResource(R.string.appearance_section_design)) {
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
        SegmentedRow(
            label = stringResource(R.string.appearance_cursor),
            options = cursors.map { stringResource(cursorLabel(it)) },
            selectedIndex = cursors.indexOf(appearance.cursorShape),
            onSelect = { index -> change { it.copy(cursorShape = cursors[index]) } }
        )
        SettingSwitch(
            title = stringResource(R.string.appearance_cursor_blink),
            hint = null,
            checked = appearance.cursorBlink
        ) { checked -> change { it.copy(cursorBlink = checked) } }
        SegmentedRow(
            label = stringResource(R.string.appearance_chrome),
            options = chromes.map { stringResource(chromeLabel(it)) },
            selectedIndex = chromes.indexOf(appearance.chromeStyle),
            onSelect = { index -> change { it.copy(chromeStyle = chromes[index]) } }
        )
        IosListRow(
            title = stringResource(R.string.appearance_reset),
            destructive = true,
            showSeparator = false,
            onClick = { change { TerminalAppearance(fontId = it.fontId) } }
        )
    }
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
