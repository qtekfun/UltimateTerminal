// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import com.qtekfun.ultimateterminal.domain.appearance.CustomFont
import com.qtekfun.ultimateterminal.domain.appearance.FontCatalog
import com.qtekfun.ultimateterminal.platform.AndroidFontStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The faces of the font the appearance selects: an imported font, or the bundled JetBrains Mono when
 * it is not selected, is gone or no longer loads. Loaded fonts are kept, so changing the size or
 * the colors does not read the file again.
 */
@Singleton
class TerminalFontLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AndroidFontStore
) {
    private val loaded = HashMap<String, TerminalTypefaces>()
    private val bundled by lazy { TerminalTypefaces.load(context) }

    fun load(fontId: String, custom: List<CustomFont>): TerminalTypefaces =
        FontCatalog.resolve(fontId, custom)?.let { font ->
            loaded[font.fileName] ?: store.typefaceOf(font.fileName)?.let { typeface ->
                TerminalTypefaces.fromFamily(typeface).also { loaded[font.fileName] = it }
            }
        } ?: bundled
}
