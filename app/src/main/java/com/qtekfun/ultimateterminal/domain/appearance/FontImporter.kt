// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome

/** Where the imported font files live (the app's private storage) and how to probe one. */
interface FontFileStore {
    /** Writes [bytes] as [fileName], replacing a file of that name; false if it could not be written. */
    suspend fun write(fileName: String, bytes: ByteArray): Boolean

    suspend fun delete(fileName: String)

    /** A probe of the font in [fileName], or null if the file is not there. */
    fun probe(fileName: String): FontProbe?
}

/** Why a font was not imported; the UI turns each into a message. */
enum class FontRejection { TOO_LARGE, NOT_A_FONT, UNREADABLE, NOT_MONOSPACED, MISSING_GLYPHS, IO }

/** A font that passed every check, ready to be stored in the settings. */
data class ImportedFont(val font: CustomFont)

/**
 * Imports a font the user picked. The bytes are not trusted: they must look like a font file, fit
 * in [MAX_BYTES], load, be monospaced and cover the basics. A file that fails any check is deleted,
 * so nothing is left behind. The font is the user's own to use: it is never redistributed.
 */
class FontImporter(
    private val store: FontFileStore,
    /** A random suffix, injected so tests are deterministic. */
    private val suffix: () -> String
) {
    suspend fun import(
        bytes: ByteArray,
        displayName: String,
        existing: List<CustomFont>
    ): Outcome<ImportedFont> {
        val extension = extensionOf(bytes)
        return when {
            bytes.size > MAX_BYTES -> rejected(FontRejection.TOO_LARGE)
            extension == null -> rejected(FontRejection.NOT_A_FONT)
            else -> admit(bytes, displayName, extension, existing)
        }
    }

    /** The bytes look like a font: name it, check the name is free, then store and test it. */
    private suspend fun admit(
        bytes: ByteArray,
        displayName: String,
        extension: String,
        existing: List<CustomFont>
    ): Outcome<ImportedFont> {
        val name = FontCatalog.nameFromFile(displayName)
        val id = FontCatalog.idFor(name) + "-" + suffix()
        val font = CustomFont(id = id, name = name, fileName = "$id.$extension")
        return when (val added = FontCatalog.add(existing, font)) {
            is Outcome.Failure -> added
            is Outcome.Success -> store(font, bytes)
        }
    }

    private suspend fun store(font: CustomFont, bytes: ByteArray): Outcome<ImportedFont> {
        val rejection = if (store.write(font.fileName, bytes)) {
            rejectionOf(store.probe(font.fileName))
        } else {
            FontRejection.IO
        }
        if (rejection != null) store.delete(font.fileName)
        return if (rejection == null) Outcome.Success(ImportedFont(font)) else rejected(rejection)
    }

    /** Why the font in the store is not usable, or null when it is. */
    private fun rejectionOf(probe: FontProbe?): FontRejection? =
        when (probe?.let(FontChecker::check) ?: FontCheck.Unreadable) {
            FontCheck.Usable -> null
            FontCheck.Unreadable -> FontRejection.UNREADABLE
            FontCheck.NotMonospaced -> FontRejection.NOT_MONOSPACED
            FontCheck.MissingGlyphs -> FontRejection.MISSING_GLYPHS
        }

    private fun rejected(reason: FontRejection): Outcome<ImportedFont> =
        Outcome.Failure(DomainError.InvalidValue(reason.name))

    companion object {
        /** A terminal font is far smaller than this; the cap keeps a wrong file from filling storage. */
        const val MAX_BYTES = 8 * 1024 * 1024

        private const val TRUETYPE_SIGNATURE = 0x00010000
        private const val HEADER_BYTES = 4
        private const val BYTE_MASK = 0xFF
        private const val BITS_PER_BYTE = 8

        /** "ttf" or "otf" from the first four bytes, or null when it is neither. */
        internal fun extensionOf(bytes: ByteArray): String? {
            if (bytes.size < HEADER_BYTES) return null
            val head = bytes.take(HEADER_BYTES).fold(0) { word, byte ->
                (word shl BITS_PER_BYTE) or (byte.toInt() and BYTE_MASK)
            }
            val tag = String(bytes, 0, HEADER_BYTES, Charsets.ISO_8859_1)
            return when {
                head == TRUETYPE_SIGNATURE || tag == "true" -> "ttf"
                tag == "OTTO" -> "otf"
                else -> null
            }
        }
    }
}
