// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.terminal.LatchState
import com.qtekfun.ultimateterminal.domain.theme.ColorMath

/**
 * How the extra-keys row (Esc, Tab, Ctrl, the arrows...) is drawn. The user picks it in the
 * appearance settings; [FLAT] is the default.
 */
enum class ExtraKeyStyle(
    /** A thin line between the keys, and between the rows. */
    val separators: Boolean,
    /** Each key has its own filled cap; without one only the label is drawn. */
    val filledCaps: Boolean,
    /** The cap, and the pressed oval, are fully rounded instead of using the corner radius. */
    val pill: Boolean,
    /** A line under each cap, as the edge of a keyboard key. */
    val edge: Boolean,
    /** Air around each key, in dp: two neighbours are twice this far apart. */
    val insetDp: Int
) {
    /** No cap: the symbol alone, with hairlines between keys; a pressed key shows a tinted oval. */
    FLAT(separators = true, filledCaps = false, pill = true, edge = false, insetDp = 4),

    /** Soft translucent capsules, with no edge and more air between them. */
    CAPSULE(separators = false, filledCaps = true, pill = true, edge = false, insetDp = 3),

    /** The keyboard-key look: a rounded cap with an edge, in colors taken from the scheme. */
    CLASSIC(separators = false, filledCaps = true, pill = false, edge = true, insetDp = 3);

    companion object {
        val DEFAULT = FLAT

        /** The style stored as [name]; anything else, or nothing, is the default. */
        fun parse(name: String?): ExtraKeyStyle = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The colors the keys are derived from, as opaque ARGB ints: the terminal scheme's own
 * [background] and [foreground], and the colors of the bars around it. They come from the scheme or
 * from the system theme, so the same style works with either.
 */
data class KeyChromeInputs(
    val background: Int,
    val foreground: Int,
    val surface: Int,
    val onSurface: Int,
    val accent: Int,
    val onAccent: Int
)

/** The colors of the row for one [style], as opaque ARGB ints (0 when the style draws nothing). */
data class ExtraKeyPalette(
    val style: ExtraKeyStyle,
    /** The bar the keys sit on. */
    val tray: Int,
    /** The cap of a key at rest; 0 when [ExtraKeyStyle.filledCaps] is off. */
    val fill: Int,
    /** What is behind a key while it is pressed. */
    val pressed: Int,
    val label: Int,
    /** The hairlines; 0 when [ExtraKeyStyle.separators] is off. */
    val separator: Int,
    /** The line under a cap; 0 when [ExtraKeyStyle.edge] is off. */
    val edge: Int,
    /** A sticky key (Ctrl, Alt) that is armed for the next key only. */
    val armed: Int,
    val onArmed: Int,
    /** A sticky key that is locked. */
    val locked: Int,
    val onLocked: Int
)

/**
 * What one key looks like at a moment: the [cap] behind it (0 for none), its [label] color, and
 * whether it carries the underline that tells a locked key from an armed one.
 */
data class KeyLook(val cap: Int, val label: Int, val indicator: Boolean)

/** The look of a key that is [latch]ed (Ctrl and Alt) and perhaps [pressed] right now. */
fun ExtraKeyPalette.look(latch: LatchState, pressed: Boolean): KeyLook = when (latch) {
    LatchState.OFF -> KeyLook(if (pressed) this.pressed else fill, label, indicator = false)

    LatchState.ARMED -> KeyLook(armed, onArmed, indicator = false)

    // Without a cap of their own, the flat keys show a locked key by an underline as well as by
    // the accent, so the difference from an armed one does not rest on a color alone.
    LatchState.LOCKED -> KeyLook(locked, onLocked, indicator = style == ExtraKeyStyle.FLAT)
}

/** Derives the palette of a style from the colors of the scheme (or of the system theme). */
object ExtraKeyPaletteFor {
    const val TEXT_MINIMUM = 4.5

    private const val FLAT_TRAY_MIX = 0.04f
    private const val TRAY_MIX = 0.07f
    private const val SEPARATOR_MIX = 0.20f
    private const val FLAT_PRESSED_MIX = 0.18f
    private const val CAPSULE_FILL_MIX = 0.10f
    private const val CAPSULE_PRESSED_MIX = 0.20f
    private const val CAPSULE_ARMED_MIX = 0.28f
    private const val CLASSIC_KEY_MIX = 0.12f
    private const val CLASSIC_PRESSED_MIX = 0.22f
    private const val CLASSIC_EDGE_MIX = 0.40f
    private const val CLASSIC_ARMED_MIX = 0.40f
    private const val BLACK = -0x1000000 // 0xff000000

    fun of(inputs: KeyChromeInputs, style: ExtraKeyStyle): ExtraKeyPalette = when (style) {
        ExtraKeyStyle.FLAT -> flat(inputs)
        ExtraKeyStyle.CAPSULE -> capsule(inputs)
        ExtraKeyStyle.CLASSIC -> classic(inputs)
    }

    private fun flat(i: KeyChromeInputs): ExtraKeyPalette {
        // Almost the terminal's own background: with no caps the bar should read as part of it.
        val tray = ColorMath.blend(i.background, i.foreground, FLAT_TRAY_MIX)
        val pressed = ColorMath.blend(tray, i.accent, FLAT_PRESSED_MIX)
        val locked = i.accent
        return ExtraKeyPalette(
            style = ExtraKeyStyle.FLAT,
            tray = tray,
            fill = 0,
            pressed = pressed,
            label = ColorMath.readableOn(i.foreground, listOf(tray, pressed), TEXT_MINIMUM),
            separator = ColorMath.blend(tray, i.foreground, SEPARATOR_MIX),
            edge = 0,
            // An armed key is a filled capsule in the accent, and a locked one is the same: the
            // locked one is told apart by an underline the row draws under its label.
            armed = locked,
            onArmed = ColorMath.readableOn(i.onAccent, listOf(locked), TEXT_MINIMUM),
            locked = locked,
            onLocked = ColorMath.readableOn(i.onAccent, listOf(locked), TEXT_MINIMUM)
        )
    }

    private fun capsule(i: KeyChromeInputs): ExtraKeyPalette {
        val tray = ColorMath.blend(i.background, i.foreground, TRAY_MIX)
        val fill = ColorMath.blend(tray, i.foreground, CAPSULE_FILL_MIX)
        val pressed = ColorMath.blend(tray, i.foreground, CAPSULE_PRESSED_MIX)
        val armed = ColorMath.blend(tray, i.accent, CAPSULE_ARMED_MIX)
        return ExtraKeyPalette(
            style = ExtraKeyStyle.CAPSULE,
            tray = tray,
            fill = fill,
            pressed = pressed,
            label = ColorMath.readableOn(i.foreground, listOf(fill, pressed), TEXT_MINIMUM),
            separator = 0,
            edge = 0,
            armed = armed,
            onArmed = ColorMath.readableOn(i.foreground, listOf(armed), TEXT_MINIMUM),
            locked = i.accent,
            onLocked = ColorMath.readableOn(i.onAccent, listOf(i.accent), TEXT_MINIMUM)
        )
    }

    private fun classic(i: KeyChromeInputs): ExtraKeyPalette {
        val tray = ColorMath.blend(i.background, i.foreground, TRAY_MIX)
        // The cap comes from the scheme's own background and foreground, not from a gray of the
        // system theme that belongs to neither the scheme nor the keyboard below.
        val fill = ColorMath.blend(i.background, i.foreground, CLASSIC_KEY_MIX)
        val backgroundIsLighter =
            ColorMath.luminance(i.background) > ColorMath.luminance(i.foreground)
        val darker = if (backgroundIsLighter) i.foreground else i.background
        val pressed = ColorMath.blend(fill, darker, CLASSIC_PRESSED_MIX)
        val armed = ColorMath.blend(fill, i.accent, CLASSIC_ARMED_MIX)
        return ExtraKeyPalette(
            style = ExtraKeyStyle.CLASSIC,
            tray = tray,
            fill = fill,
            pressed = pressed,
            label = ColorMath.readableOn(i.foreground, listOf(fill, pressed), TEXT_MINIMUM),
            separator = 0,
            edge = ColorMath.blend(i.background, BLACK, CLASSIC_EDGE_MIX),
            armed = armed,
            onArmed = ColorMath.readableOn(i.foreground, listOf(armed), TEXT_MINIMUM),
            locked = i.accent,
            onLocked = ColorMath.readableOn(i.onAccent, listOf(i.accent), TEXT_MINIMUM)
        )
    }
}
