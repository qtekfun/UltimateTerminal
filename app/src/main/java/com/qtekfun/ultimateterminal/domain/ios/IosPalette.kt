// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.domain.theme.ThemeDecision

/**
 * The system colors of the iOS-style screens, opaque ARGB. The names follow iOS's own ("grouped
 * background", "label", "separator", "tint") so the components read the same as the design they
 * are inspired by. Text colors are nudged to at least 4.5:1 against what they sit on: iOS's own
 * secondary label and system blue fall short of that in light mode.
 */
data class IosPalette(
    /** Behind plain content. */
    val background: Int,
    /** Behind a grouped list: the page the rounded groups sit on. */
    val groupedBackground: Int,
    /** A row of a grouped list, and sheets and alerts. */
    val cell: Int,
    /** A pressed row. */
    val pressedCell: Int,
    val label: Int,
    val secondaryLabel: Int,
    val separator: Int,
    val tint: Int,
    /** Text and icons on a [tint]-filled control. */
    val onTint: Int,
    val destructive: Int,
    val switchOn: Int,
    val switchOffTrack: Int,
    val dark: Boolean,
    val oled: Boolean
)

private const val BLUE_ANSI_INDEX = 4

private const val LIGHT_BLUE = 0xFF007AFF.toInt()
private const val DARK_BLUE = 0xFF0A84FF.toInt()
private const val LIGHT_RED = 0xFFFF3B30.toInt()
private const val DARK_RED = 0xFFFF453A.toInt()

private val Light = IosPalette(
    background = 0xFFFFFFFF.toInt(),
    groupedBackground = 0xFFF2F2F7.toInt(),
    cell = 0xFFFFFFFF.toInt(),
    pressedCell = 0xFFD1D1D6.toInt(),
    label = 0xFF000000.toInt(),
    secondaryLabel = 0xFF6C6C70.toInt(),
    separator = 0xFFC6C6C8.toInt(),
    tint = LIGHT_BLUE,
    onTint = 0xFFFFFFFF.toInt(),
    destructive = LIGHT_RED,
    switchOn = 0xFF34C759.toInt(),
    switchOffTrack = 0xFFE9E9EA.toInt(),
    dark = false,
    oled = false
)

private val Dark = Light.copy(
    background = 0xFF000000.toInt(),
    groupedBackground = 0xFF000000.toInt(),
    cell = 0xFF1C1C1E.toInt(),
    pressedCell = 0xFF2C2C2E.toInt(),
    label = 0xFFFFFFFF.toInt(),
    secondaryLabel = 0xFF98989D.toInt(),
    separator = 0xFF38383A.toInt(),
    tint = DARK_BLUE,
    destructive = DARK_RED,
    switchOn = 0xFF30D158.toInt(),
    switchOffTrack = 0xFF39393D.toInt(),
    dark = true
)

/** Dark with the cells nearly black too, so most pixels of an OLED panel stay off. */
private val Oled = Dark.copy(
    cell = 0xFF111113.toInt(),
    pressedCell = 0xFF1E1E20.toInt(),
    separator = 0xFF2A2A2D.toInt(),
    oled = true
)

/**
 * The palette for [decision], with the tint taken from the blue of [scheme] when there is one, so
 * the chrome follows the terminal's colors. The tint, the destructive red and the secondary label
 * are moved just far enough to be legible on the cell and the page behind it.
 */
fun iosPalette(decision: ThemeDecision, scheme: TerminalColorScheme? = null): IosPalette {
    val base = when {
        decision.oled -> Oled
        decision.dark -> Dark
        else -> Light
    }
    val wanted = scheme?.ansi?.get(BLUE_ANSI_INDEX) ?: base.tint
    val tint = legibleOn(wanted, base)
    return base.copy(
        tint = tint,
        onTint = ColorAdjust.readableOn(tint),
        destructive = legibleOn(base.destructive, base),
        secondaryLabel = legibleOn(base.secondaryLabel, base)
    )
}

/** [color] as legible on both the cell and the page behind it. */
private fun legibleOn(color: Int, base: IosPalette): Int =
    ColorAdjust.ensureContrast(ColorAdjust.ensureContrast(color, base.cell), base.groupedBackground)
