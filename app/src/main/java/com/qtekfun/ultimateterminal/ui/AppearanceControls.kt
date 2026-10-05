// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSegmentedControl
import com.qtekfun.ultimateterminal.ui.ios.IosSlider
import com.qtekfun.ultimateterminal.ui.ios.IosSpacing
import com.qtekfun.ultimateterminal.ui.ios.IosText
import com.qtekfun.ultimateterminal.ui.ios.IosTheme
import com.qtekfun.ultimateterminal.ui.ios.Separator

/** A switch row with a title and, under it, a line of explanation; the whole row is the touch target. */
@Composable
internal fun SettingSwitch(
    title: String,
    hint: String?,
    checked: Boolean,
    showSeparator: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    IosListRow(
        title = title,
        subtitle = hint,
        accessory = IosAccessory.Toggle(checked, onChange),
        showSeparator = showSeparator
    )
}

/** A row of a grouped list that holds a segmented control, with an optional [label] above it. */
@Composable
internal fun SegmentedRow(
    label: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    showSeparator: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = IosSpacing.md, vertical = IosSpacing.sm)) {
            if (label != null) {
                IosText(label, modifier = Modifier.padding(bottom = IosSpacing.xs))
            }
            IosSegmentedControl(options, selectedIndex, onSelect)
        }
        if (showSeparator) Separator(Modifier.padding(start = IosSpacing.md))
    }
}

/**
 * A slider with its label and current value, as a row of a grouped list. It is kept in the screen
 * while dragging and saved when the finger lifts, so the settings are written once, not on every
 * pixel.
 */
@Composable
internal fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    showSeparator: Boolean = true,
    onChange: (Float) -> Unit
) {
    var dragging by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = IosSpacing.md, vertical = IosSpacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IosText(label)
                IosText(valueText, color = IosTheme.colors.secondaryLabel)
            }
            IosSlider(
                value = dragging,
                onValueChange = { dragging = it },
                range = range,
                description = label,
                onValueChangeFinished = { onChange(dragging) }
            )
        }
        if (showSeparator) Separator(Modifier.padding(start = IosSpacing.md))
    }
}
