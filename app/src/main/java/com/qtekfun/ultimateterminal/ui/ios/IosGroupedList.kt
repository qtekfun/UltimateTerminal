// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R

/**
 * A group of rows on rounded white (or dark) cells over the grey page, as in iOS Settings. Put
 * [IosListRow]s in it; the last one takes `showSeparator = false`. [header] is the small caps
 * label above the group and [footer] the explanation under it.
 */
@Composable
fun IosSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    val colors = IosTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = IosSpacing.md).padding(top = IosSpacing.lg)
    ) {
        if (header != null) {
            IosText(
                text = header.uppercase(),
                modifier = Modifier.padding(
                    start = IosSpacing.md,
                    bottom =
                        IosSpacing.sm - IosSpacing.xxs
                ),
                style = IosTheme.typography.footnote,
                color = colors.secondaryLabel
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(IosRadius.card)).background(colors.cell)
        ) {
            content()
        }
        if (footer != null) {
            IosText(
                text = footer,
                modifier = Modifier.padding(horizontal = IosSpacing.md, vertical = IosSpacing.sm),
                style = IosTheme.typography.footnote,
                color = colors.secondaryLabel
            )
        }
    }
}

/**
 * One row of a grouped list: [title] with an optional [subtitle] and leading [glyph], and an
 * [accessory] at the end. It highlights while pressed. A row with a [IosAccessory.Toggle] flips it
 * when the row is tapped. [destructive] draws it in red.
 */
@Composable
fun IosListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyph: IosGlyph? = null,
    accessory: IosAccessory = IosAccessory.None,
    destructive: Boolean = false,
    enabled: Boolean = true,
    showSeparator: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val colors = IosTheme.colors
    val toggle = accessory as? IosAccessory.Toggle
    val action = toggle?.let { { it.onChange(!it.checked) } } ?: onClick
    val inset = if (glyph !=
        null
    ) {
        IosSize.rowIcon + IosSpacing.md + IosSpacing.md
    } else {
        IosSpacing.md
    }
    val state = toggle?.let { switchStateDescription(it.checked) }
    val content = @Composable {
        RowContent(
            title,
            subtitle,
            glyph,
            accessory,
            RowLook(destructive, enabled, separatorInset = if (showSeparator) inset else null)
        )
    }
    if (action == null) {
        Box(modifier) { content() }
    } else {
        IosPressable(
            onClick = action,
            modifier = modifier.semantics(mergeDescendants = true) {
                state?.let { stateDescription = it }
            },
            enabled = enabled,
            role = if (toggle != null) Role.Switch else Role.Button,
            pressedColor = colors.pressedCell,
            haptic = toggle == null
        ) { content() }
    }
}

/** How a row looks beyond its text: red, dimmed, and where its separator starts (null for none). */
private class RowLook(val destructive: Boolean, val enabled: Boolean, val separatorInset: Dp?)

@Composable
private fun RowContent(
    title: String,
    subtitle: String?,
    glyph: IosGlyph?,
    accessory: IosAccessory,
    look: RowLook
) {
    val colors = IosTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = IosSize.rowMinHeight)
            .rowSeparator(look.separatorInset, colors.separator)
            .padding(horizontal = IosSpacing.md, vertical = IosSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyph != null) {
            val tint = if (look.destructive) colors.destructive else colors.tint
            IosIcon(glyph, null, tint = tint, size = IosSize.rowIcon)
            Spacer(Modifier.width(IosSpacing.md))
        }
        RowLabels(title, subtitle, look, Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(IosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) { Accessory(accessory, look.enabled) }
    }
}

@Composable
private fun RowLabels(title: String, subtitle: String?, look: RowLook, modifier: Modifier) {
    val colors = IosTheme.colors
    Column(modifier) {
        val color = when {
            !look.enabled -> colors.secondaryLabel
            look.destructive -> colors.destructive
            else -> colors.label
        }
        IosText(text = title, style = IosTheme.typography.body, color = color)
        if (subtitle != null) {
            IosText(subtitle, style = IosTheme.typography.footnote, color = colors.secondaryLabel)
        }
    }
}

/** A hairline along the bottom edge, starting [inset] from the start; none when [inset] is null. */
private fun Modifier.rowSeparator(inset: Dp?, color: Color): Modifier = if (inset == null) {
    this
} else {
    drawBehind {
        val thickness = IosSize.hairline.toPx()
        val y = size.height - thickness / 2
        drawLine(color, Offset(inset.toPx(), y), Offset(size.width, y), thickness)
    }
}

@Composable
private fun Accessory(accessory: IosAccessory, enabled: Boolean) {
    val colors = IosTheme.colors
    when (accessory) {
        IosAccessory.None -> Unit

        IosAccessory.Chevron -> Chevron()

        IosAccessory.Check -> IosIcon(IosGlyph.CHECK, null, tint = colors.tint)

        is IosAccessory.Value -> {
            IosText(
                accessory.text,
                style = IosTheme.typography.body,
                color = colors.secondaryLabel,
                maxLines = 1
            )
            if (accessory.chevron) Chevron()
        }

        is IosAccessory.Toggle -> IosSwitch(
            accessory.checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

@Composable
private fun Chevron() {
    IosIcon(IosGlyph.CHEVRON_RIGHT, null, tint = IosTheme.colors.secondaryLabel, size = 18.dp)
}

/** The state a screen reader announces for a switch row. */
@Composable
internal fun switchStateDescription(checked: Boolean): String =
    stringResource(if (checked) R.string.ios_state_on else R.string.ios_state_off)
