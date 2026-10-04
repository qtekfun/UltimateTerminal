// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.qtekfun.ultimateterminal.R

/**
 * The rounded grey search field, with a magnifier and a button that clears it. The field is drawn at
 * 36 dp inside a 48 dp touch area.
 */
@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {}
) {
    val colors = IosTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = IosSize.minTouch),
        singleLine = true,
        textStyle = IosTheme.typography.body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.tint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { field ->
            SearchDecoration(value, placeholder, onClear = { onValueChange("") }, field)
        }
    )
}

@Composable
private fun SearchDecoration(
    value: String,
    placeholder: String,
    onClear: () -> Unit,
    field: @Composable () -> Unit
) {
    val colors = IosTheme.colors
    Box(Modifier.heightIn(min = IosSize.minTouch), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = IosSize.searchHeight)
                .clip(RoundedCornerShape(IosRadius.button))
                .background(colors.fill)
                .padding(start = IosSpacing.sm + IosSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosIcon(IosGlyph.SEARCH, null, tint = colors.secondaryLabel, size = SMALL_ICON)
            Box(Modifier.weight(1f).padding(horizontal = IosSpacing.sm)) {
                if (value.isEmpty()) {
                    IosText(
                        placeholder,
                        color = colors.secondaryLabel,
                        maxLines = 1
                    )
                }
                field()
            }
            if (value.isEmpty()) Spacer(Modifier.width(IosSpacing.sm)) else ClearButton(onClear)
        }
    }
}

@Composable
private fun ClearButton(onClear: () -> Unit) {
    IosPressable(onClick = onClear, haptic = false) {
        Box(Modifier.size(IosSize.minTouch), contentAlignment = Alignment.Center) {
            IosIcon(
                IosGlyph.CLOSE,
                stringResource(R.string.ios_search_clear),
                tint = IosTheme.colors.secondaryLabel,
                size = SMALL_ICON
            )
        }
    }
}

private val SMALL_ICON = IosSize.icon - IosSpacing.xs
