// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import kotlinx.coroutines.flow.first

/**
 * The actions of the appearance screen that touch more than one setting: importing and removing
 * fonts, and saving, removing and importing color schemes. Each keeps the settings consistent, so a
 * font or a scheme that goes away is never left selected.
 */
class AppearanceManager(
    private val settings: SettingsRepository,
    private val importer: FontImporter,
    private val fonts: FontFileStore
) {
    /** Imports the font in [bytes]; it is stored but not selected. */
    suspend fun importFont(bytes: ByteArray, displayName: String): Outcome<CustomFont> {
        val current = settings.observe().first()
        return when (val result = importer.import(bytes, displayName, current.customFonts)) {
            is Outcome.Failure -> result

            is Outcome.Success -> {
                val font = result.value.font
                settings.update { stored ->
                    when (val added = FontCatalog.add(stored.customFonts, font)) {
                        is Outcome.Success -> stored.copy(customFonts = added.value)
                        is Outcome.Failure -> stored
                    }
                }
                Outcome.Success(font)
            }
        }
    }

    /** Removes an imported font and its file; if it was the one in use, the bundled one takes over. */
    suspend fun deleteFont(id: String) {
        val font = FontCatalog.resolve(id, settings.observe().first().customFonts) ?: return
        settings.update { stored ->
            stored.copy(
                customFonts = FontCatalog.remove(stored.customFonts, id),
                appearance = if (stored.appearance.fontId == id) {
                    stored.appearance.copy(fontId = FontCatalog.BUNDLED_ID)
                } else {
                    stored.appearance
                }
            )
        }
        fonts.delete(font.fileName)
    }

    /**
     * Stores [edited] as a custom scheme, replacing the one with [previousId] (null for a new
     * scheme). The scheme in use follows an edit, even one that renames it.
     */
    suspend fun saveScheme(previousId: String?, edited: TerminalColorScheme): Outcome<Unit> {
        var failure: DomainError? = null
        settings.update { stored ->
            when (val saved = SchemeEditor.save(stored.customSchemes, previousId, edited)) {
                is Outcome.Failure -> {
                    failure = saved.error
                    stored
                }

                is Outcome.Success -> stored.copy(
                    customSchemes = saved.value,
                    terminalSchemeId = if (previousId != null &&
                        stored.terminalSchemeId == previousId
                    ) {
                        edited.id
                    } else {
                        stored.terminalSchemeId
                    }
                )
            }
        }
        return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    /** Removes a custom scheme; if it was the one in use, the default scheme takes over. */
    suspend fun deleteScheme(id: String) {
        settings.update { stored ->
            stored.copy(
                customSchemes = SchemeCatalog.remove(stored.customSchemes, id),
                terminalSchemeId = if (stored.terminalSchemeId == id) {
                    BuiltInSchemes.DEFAULT_ID
                } else {
                    stored.terminalSchemeId
                }
            )
        }
    }

    /** Imports a scheme from its JSON text and selects it. */
    suspend fun importScheme(text: String): Outcome<TerminalColorScheme> =
        when (val decoded = SchemeCodec.decode(text)) {
            is Outcome.Failure -> decoded
            is Outcome.Success -> select(decoded.value)
        }

    /** Stores [scheme] as a new custom scheme and makes it the one in use. */
    private suspend fun select(scheme: TerminalColorScheme): Outcome<TerminalColorScheme> =
        when (val saved = saveScheme(null, scheme)) {
            is Outcome.Failure -> saved

            is Outcome.Success -> {
                settings.update { it.copy(terminalSchemeId = scheme.id) }
                Outcome.Success(scheme)
            }
        }
}
