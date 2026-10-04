// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import com.qtekfun.ultimateterminal.domain.ios.ColorAdjust

private const val PRESSED_FILLED_ALPHA = 0.8f
private const val PRESSED_ALPHA_PLAIN = 0.5f
private const val TINTED_BACKGROUND_ALPHA = 0.15f
private const val DISABLED_ALPHA = 0.4f

/** A full-width iOS button, 50 dp tall. [destructive] makes it red. */
@Composable
fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IosButtonStyle = IosButtonStyle.FILLED,
    destructive: Boolean = false,
    enabled: Boolean = true,
    glyph: IosGlyph? = null
) {
    val colors = IosTheme.colors
    val accent = if (destructive) colors.destructive else colors.tint
    val onAccent = if (destructive) {
        Color(
            ColorAdjust.readableOn(accent.toArgb())
        )
    } else {
        colors.onTint
    }
    val (background, foreground) = when (style) {
        IosButtonStyle.FILLED -> accent to onAccent
        IosButtonStyle.TINTED -> accent.copy(alpha = TINTED_BACKGROUND_ALPHA) to accent
        IosButtonStyle.PLAIN -> Color.Transparent to accent
    }
    IosPressable(onClick, modifier.fillMaxWidth(), enabled = enabled) { pressed ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = IosSize.buttonHeight)
                .clip(RoundedCornerShape(IosRadius.button))
                .background(background)
                .alpha(
                    when {
                        !enabled -> DISABLED_ALPHA
                        pressed && style == IosButtonStyle.FILLED -> PRESSED_FILLED_ALPHA
                        pressed -> PRESSED_ALPHA_PLAIN
                        else -> 1f
                    }
                )
                .padding(horizontal = IosSpacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (glyph != null) {
                IosIcon(glyph, null, tint = foreground)
                Spacer(Modifier.width(IosSpacing.sm))
            }
            IosText(
                text = text,
                style = IosTheme.typography.headline,
                color = foreground,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
