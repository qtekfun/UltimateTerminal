// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A font the user imported: [fileName] is its file in the app's private font folder. */
@Serializable
data class CustomFont(val id: String, val name: String, val fileName: String)

/** The bundled font plus the ones the user imported. */
object FontCatalog {
    /** The id of the font that ships in the app (JetBrains Mono). */
    const val BUNDLED_ID = "jetbrains-mono"
    const val BUNDLED_NAME = "JetBrains Mono"
    const val MAX_CUSTOM_FONTS = 20
    const val MAX_NAME_LENGTH = 40

    private val json = Json { ignoreUnknownKeys = true }

    /** The imported font with [id], or null for the bundled one or one that is gone. */
    fun resolve(id: String, custom: List<CustomFont>): CustomFont? =
        custom.firstOrNull { it.id == id }

    /** [id] if it still names a font, otherwise the bundled font. */
    fun idOrBundled(id: String, custom: List<CustomFont>): String =
        if (id == BUNDLED_ID || custom.any { it.id == id }) id else BUNDLED_ID

    /** [custom] with [font] added. Names are unique among all fonts, ignoring case. */
    fun add(custom: List<CustomFont>, font: CustomFont): Outcome<List<CustomFont>> {
        val taken = font.name.equals(BUNDLED_NAME, ignoreCase = true) ||
            custom.any { it.name.equals(font.name, ignoreCase = true) || it.id == font.id }
        return when {
            taken -> Outcome.Failure(DomainError.NameTaken(font.name))

            custom.size >= MAX_CUSTOM_FONTS ->
                Outcome.Failure(DomainError.InvalidValue("customFonts"))

            else -> Outcome.Success(custom + font)
        }
    }

    fun remove(custom: List<CustomFont>, id: String): List<CustomFont> =
        custom.filterNot { it.id == id }

    /**
     * A readable name from a file name: no extension and no control characters, trimmed and cut at
     * [MAX_NAME_LENGTH]. A blank result falls back to "Font".
     */
    fun nameFromFile(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringBeforeLast('.')
        val clean = base.filterNot { it.isISOControl() }.replace('_', ' ').trim()
            .take(MAX_NAME_LENGTH).trim()
        return clean.ifEmpty { "Font" }
    }

    /** A file-name-safe id for [name]. */
    fun idFor(name: String): String {
        val slug = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return "font-" + slug.ifEmpty { "custom" }
    }

    fun encodeList(fonts: List<CustomFont>): String =
        json.encodeToString(ListSerializer(CustomFont.serializer()), fonts)

    /** The fonts stored by [encodeList]; a damaged value gives no fonts, an unsafe entry is dropped. */
    fun decodeList(text: String): List<CustomFont> = try {
        json.decodeFromString(ListSerializer(CustomFont.serializer()), text).filter { it.isSafe() }
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    /** A font file name is a plain name in the font folder: never a path. */
    private fun CustomFont.isSafe() = id.isNotBlank() && name.isNotBlank() &&
        fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName && fileName != ".." &&
        fileName != "."
}
