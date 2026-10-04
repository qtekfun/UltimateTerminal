// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.qtekfun.ultimateterminal.R

/** The four faces of the terminal font, so bold and italic text use real glyphs, not fake ones. */
class TerminalTypefaces(
    val regular: Typeface,
    private val bold: Typeface,
    private val italic: Typeface,
    private val boldItalic: Typeface
) {
    fun of(bold: Boolean, italic: Boolean): Typeface = when {
        bold && italic -> boldItalic
        bold -> this.bold
        italic -> this.italic
        else -> regular
    }

    companion object {
        /** The faces of a font family; a family without a bold or italic gets the system's match. */
        fun fromFamily(family: Typeface) = TerminalTypefaces(
            regular = family,
            bold = Typeface.create(family, Typeface.BOLD),
            italic = Typeface.create(family, Typeface.ITALIC),
            boldItalic = Typeface.create(family, Typeface.BOLD_ITALIC)
        )

        /** The bundled JetBrains Mono, or the system monospace font if it cannot be loaded. */
        fun load(context: Context): TerminalTypefaces = fromFamily(
            ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        )
    }
}
