// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The file format of an exported color scheme (and of the stored custom schemes): a small JSON
 * document with a version, so the format can change later without breaking old files.
 *
 * ```
 * {"version":1,"name":"My scheme","ansi":["#000000", ... 16 colors],
 *  "foreground":"#ffffff","background":"#000000","cursor":"#ffffff","selection":"#333333"}
 * ```
 */
object SchemeCodec {
    const val FORMAT_VERSION = 1
    const val MAX_NAME_LENGTH = 40
    private const val HEX_DIGITS = 6
    private const val HEX_RADIX = 16
    private const val RGB_MASK = 0xFFFFFF

    private val hexColor = Regex("^#[0-9a-fA-F]{6}$")
    private val json = Json {
        prettyPrint = true
        // `version` has a default value; without this it would be left out of the exported file.
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class SchemeDto(
        val version: Int = FORMAT_VERSION,
        val name: String,
        val ansi: List<String>,
        val foreground: String,
        val background: String,
        val cursor: String,
        val selection: String
    )

    fun encode(scheme: TerminalColorScheme): String =
        json.encodeToString(SchemeDto.serializer(), scheme.toDto())

    fun decode(text: String): Outcome<TerminalColorScheme> = parse {
        json.decodeFromString(SchemeDto.serializer(), text)
    }

    /** The custom schemes as one JSON array, for the settings. */
    fun encodeList(schemes: List<TerminalColorScheme>): String =
        json.encodeToString(ListSerializer(SchemeDto.serializer()), schemes.map { it.toDto() })

    /** The schemes stored by [encodeList]; entries that no longer validate are dropped. */
    fun decodeList(text: String): List<TerminalColorScheme> {
        val dtos = try {
            json.decodeFromString(ListSerializer(SchemeDto.serializer()), text)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        return dtos.mapNotNull { (build(it) as? Outcome.Success)?.value }
    }

    /** The id of an imported scheme: derived from its name, and never equal to a built-in id. */
    fun idFor(name: String): String {
        val slug = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return "custom-$slug"
    }

    private fun parse(read: () -> SchemeDto): Outcome<TerminalColorScheme> = try {
        build(read())
    } catch (_: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException: not JSON, or not this format.
        Outcome.Failure(DomainError.InvalidValue("scheme"))
    }

    private fun build(dto: SchemeDto): Outcome<TerminalColorScheme> {
        val name = dto.name.trim()
        return when {
            dto.version != FORMAT_VERSION -> Outcome.Failure(DomainError.InvalidValue("version"))

            name.isEmpty() || name.length > MAX_NAME_LENGTH || name.any { it.isISOControl() } ->
                Outcome.Failure(DomainError.InvalidName(name))

            dto.ansi.size != TerminalColorScheme.ANSI_COLORS ->
                Outcome.Failure(DomainError.InvalidValue("ansi"))

            else -> try {
                Outcome.Success(
                    TerminalColorScheme(
                        id = idFor(name),
                        name = name,
                        ansi = dto.ansi.map(::color),
                        foreground = color(dto.foreground),
                        background = color(dto.background),
                        cursor = color(dto.cursor),
                        selection = color(dto.selection)
                    )
                )
            } catch (_: IllegalArgumentException) {
                Outcome.Failure(DomainError.InvalidValue("color"))
            }
        }
    }

    /** An opaque ARGB int from "#rrggbb"; anything else is rejected. */
    private fun color(text: String): Int {
        require(hexColor.matches(text)) { "Not a #rrggbb color" }
        return text.substring(1, 1 + HEX_DIGITS).toInt(HEX_RADIX) or TerminalColorScheme.BLACK
    }

    private fun TerminalColorScheme.toDto() = SchemeDto(
        name = name,
        ansi = ansi.map(::hex),
        foreground = hex(foreground),
        background = hex(background),
        cursor = hex(cursor),
        selection = hex(selection)
    )

    private fun hex(argb: Int) = "#%06x".format(argb and RGB_MASK)
}
