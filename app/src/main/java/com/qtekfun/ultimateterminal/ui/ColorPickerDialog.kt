// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.appearance.ColorHex
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosRadius
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosSpacing
import com.qtekfun.ultimateterminal.ui.ios.IosTextField

private const val CHANNEL_MAX = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
private const val OPAQUE = 0xFF000000.toInt()
private val PreviewHeight = 48.dp

/**
 * An iOS sheet that picks a color with a hex field and three sliders (red, green, blue), kept in
 * step with each other. There is no color-wheel library: it would be one more dependency for a
 * control that these three do just as well.
 */
@Composable
internal fun ColorPickerDialog(
    title: String,
    initial: Int,
    onPicked: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var color by remember { mutableIntStateOf(initial or OPAQUE) }
    var hex by remember { mutableStateOf(ColorHex.format(initial)) }
    val valid = ColorHex.parse(hex) != null

    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column(Modifier.imePadding()) {
            IosSheetHeader(
                title = stringResource(R.string.appearance_picker_title, title),
                cancelLabel = stringResource(R.string.appearance_cancel),
                confirmLabel = stringResource(R.string.appearance_ok),
                onCancel = onDismiss,
                onConfirm = {
                    onPicked(color)
                    onDismiss()
                },
                confirmEnabled = valid
            )
            Box(
                Modifier
                    .padding(horizontal = IosSpacing.md)
                    .padding(top = IosSpacing.md)
                    .fillMaxWidth()
                    .height(PreviewHeight)
                    .background(Color(color), RoundedCornerShape(IosRadius.card))
            )
            IosSection(
                footer = if (valid) null else stringResource(R.string.appearance_picker_hex_invalid)
            ) {
                IosTextField(
                    value = hex,
                    onValueChange = { typed ->
                        hex = typed
                        ColorHex.parse(typed)?.let { color = it }
                    },
                    label = stringResource(R.string.appearance_picker_hex),
                    isError = !valid,
                    showSeparator = false
                )
            }
            IosSection {
                RgbSliders(color) { newColor ->
                    color = newColor
                    hex = ColorHex.format(newColor)
                }
            }
        }
    }
}

/** Red, green and blue as three sliders over [color]; each change reports the whole new color. */
@Composable
private fun RgbSliders(color: Int, onChange: (Int) -> Unit) {
    val channels = listOf(
        R.string.appearance_picker_red to RED_SHIFT,
        R.string.appearance_picker_green to GREEN_SHIFT,
        R.string.appearance_picker_blue to 0
    )
    channels.forEachIndexed { index, (label, shift) ->
        val value = (color shr shift and CHANNEL_MASK).toFloat()
        LabeledSlider(
            label = stringResource(label),
            valueText = value.toInt().toString(),
            value = value,
            range = 0f..CHANNEL_MAX.toFloat(),
            showSeparator = index != channels.lastIndex
        ) { picked ->
            val cleared = color and (CHANNEL_MASK shl shift).inv()
            onChange(cleared or (picked.toInt().coerceIn(0, CHANNEL_MAX) shl shift))
        }
    }
}
