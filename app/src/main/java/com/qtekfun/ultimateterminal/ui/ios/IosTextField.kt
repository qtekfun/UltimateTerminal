// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

private const val LABEL_WEIGHT = 0.4f
private const val FIELD_WEIGHT = 0.6f

/**
 * A row of a grouped list that holds text: the [label] at the start and the field at the end, as in
 * the iOS forms. Put it in an [IosSection]; the last one takes `showSeparator = false`. A screen
 * reader reads the label and the text as one item. [isError] draws the label in red. A tap anywhere
 * on the row, the label included, focuses the field (D-FIX-5).
 */
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    showSeparator: Boolean = true
) {
    val colors = IosTheme.colors
    val body = IosTheme.typography.body
    val focus = remember { FocusRequester() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = IosSize.rowMinHeight)
            .focusOnTap(focus)
            .fieldSeparator(if (showSeparator) IosSpacing.md else null, colors.separator)
            .padding(horizontal = IosSpacing.md)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosText(
            label,
            Modifier.weight(LABEL_WEIGHT),
            style = body,
            color = if (isError) colors.destructive else colors.label
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(FIELD_WEIGHT)
                .padding(start = IosSpacing.sm)
                .focusRequester(focus),
            singleLine = true,
            textStyle = body.copy(color = colors.label, textAlign = TextAlign.End),
            cursorBrush = SolidColor(colors.tint),
            visualTransformation = if (secure) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secure) KeyboardType.Password else keyboardType,
                autoCorrectEnabled = false
            )
        )
    }
}

/**
 * A row of a grouped list for text of several lines (a pasted key, say): the [label] above and the
 * field under it, from the start. It grows to [maxLines] and then scrolls.
 */
@Composable
fun IosTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 3,
    maxLines: Int = 6,
    isError: Boolean = false,
    showSeparator: Boolean = true
) {
    val colors = IosTheme.colors
    val focus = remember { FocusRequester() }
    Column(
        modifier
            .fillMaxWidth()
            .focusOnTap(focus)
            .fieldSeparator(if (showSeparator) IosSpacing.md else null, colors.separator)
            .padding(horizontal = IosSpacing.md, vertical = IosSpacing.sm)
            .semantics(mergeDescendants = true) {}
    ) {
        IosText(
            label,
            style = IosTheme.typography.footnote,
            color = if (isError) colors.destructive else colors.secondaryLabel
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = IosSpacing.xs).focusRequester(focus),
            minLines = minLines,
            maxLines = maxLines,
            textStyle = IosTheme.typography.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.tint),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
        )
    }
}

/** A tap on the whole row gives the focus to its field and shows the keyboard, without a ripple. */
@Composable
private fun Modifier.focusOnTap(focus: FocusRequester): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    return clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) {
        focus.requestFocus()
        keyboard?.show()
    }
}

/** A hairline along the bottom edge, starting [inset] from the start; none when [inset] is null. */
private fun Modifier.fieldSeparator(inset: Dp?, color: Color): Modifier = if (inset == null) {
    this
} else {
    drawBehind {
        val thickness = IosSize.hairline.toPx()
        val y = size.height - thickness / 2
        drawLine(color, Offset(inset.toPx(), y), Offset(size.width, y), thickness)
    }
}
