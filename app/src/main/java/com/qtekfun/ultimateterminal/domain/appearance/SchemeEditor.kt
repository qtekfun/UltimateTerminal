// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.ColorMath
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme

/** Which color of a scheme is being edited. */
sealed interface ColorSlot {
    data class Ansi(val index: Int) : ColorSlot
    data object Foreground : ColorSlot
    data object Background : ColorSlot
    data object Cursor : ColorSlot
    data object Selection : ColorSlot
}

/** A color that is hard to read on the scheme's background. A hint for the user: it never blocks. */
data class ContrastIssue(val slot: ColorSlot, val ratio: Double, val minimum: Double)

/** Creates and edits the user's own schemes. Everything is pure; saving is up to the caller. */
object SchemeEditor {
    private const val TEXT_MINIMUM = 4.5
    private const val ACCENT_MINIMUM = 3.0
    private const val BLACK_INDEX = 0
    private const val BRIGHT_BLACK_INDEX = 8

    /** The ANSI colors that are meant to blend in with the background (black and bright black). */
    private val QUIET_ANSI = setOf(BLACK_INDEX, BRIGHT_BLACK_INDEX)

    /** A copy of [source] to edit: a fresh name, never the name of an existing scheme. */
    fun duplicate(source: TerminalColorScheme, copyOf: String, all: List<TerminalColorScheme>) =
        source.copy(
            id = "",
            name = uniqueName("$copyOf ${source.name}", all),
            builtIn = false
        ).withId()

    /** A new scheme from the default one, named [name] or the nearest free name. */
    fun blank(name: String, base: TerminalColorScheme, all: List<TerminalColorScheme>) =
        base.copy(id = "", name = uniqueName(name, all), builtIn = false).withId()

    fun withColor(scheme: TerminalColorScheme, slot: ColorSlot, argb: Int): TerminalColorScheme {
        val color = argb or TerminalColorScheme.BLACK
        return when (slot) {
            is ColorSlot.Ansi -> scheme.copy(
                ansi = scheme.ansi.mapIndexed { index, old ->
                    if (index ==
                        slot.index
                    ) {
                        color
                    } else {
                        old
                    }
                }
            )

            ColorSlot.Foreground -> scheme.copy(foreground = color)

            ColorSlot.Background -> scheme.copy(background = color)

            ColorSlot.Cursor -> scheme.copy(cursor = color)

            ColorSlot.Selection -> scheme.copy(selection = color)
        }
    }

    /** [scheme] renamed; the id follows the name, as it does for imported schemes. */
    fun renamed(scheme: TerminalColorScheme, name: String): Outcome<TerminalColorScheme> {
        val clean = name.trim()
        return if (clean.isEmpty() || clean.length > SchemeCodec.MAX_NAME_LENGTH ||
            clean.any { it.isISOControl() }
        ) {
            Outcome.Failure(DomainError.InvalidName(clean))
        } else {
            Outcome.Success(scheme.copy(name = clean).withId())
        }
    }

    /**
     * [edited] stored among [custom], replacing the scheme that had [previousId] (or adding it when
     * it is new). Another scheme, built-in or not, may not have the same name.
     */
    fun save(
        custom: List<TerminalColorScheme>,
        previousId: String?,
        edited: TerminalColorScheme
    ): Outcome<List<TerminalColorScheme>> {
        val others = custom.filterNot { it.id == previousId }
        val clash = (others + BuiltInSchemes.all).any {
            it.name.equals(edited.name, ignoreCase = true) || it.id == edited.id
        }
        return when {
            clash -> Outcome.Failure(DomainError.NameTaken(edited.name))

            previousId == null && custom.size >= SchemeCatalog.MAX_CUSTOM_SCHEMES ->
                Outcome.Failure(DomainError.InvalidValue("customSchemes"))

            else -> Outcome.Success(
                if (previousId != null && custom.any { it.id == previousId }) {
                    custom.map { if (it.id == previousId) edited else it }
                } else {
                    custom + edited
                }
            )
        }
    }

    /** The colors of [scheme] that are hard to read on its background, worst first. */
    fun contrastIssues(scheme: TerminalColorScheme): List<ContrastIssue> {
        val issues = mutableListOf<ContrastIssue>()
        fun check(slot: ColorSlot, color: Int, minimum: Double) {
            val ratio = ColorMath.contrast(color, scheme.background)
            if (ratio < minimum) issues += ContrastIssue(slot, ratio, minimum)
        }
        check(ColorSlot.Foreground, scheme.foreground, TEXT_MINIMUM)
        check(ColorSlot.Cursor, scheme.cursor, ACCENT_MINIMUM)
        scheme.ansi.forEachIndexed { index, color ->
            if (index !in QUIET_ANSI) check(ColorSlot.Ansi(index), color, ACCENT_MINIMUM)
        }
        return issues.sortedBy { it.ratio }
    }

    private fun TerminalColorScheme.withId() = copy(id = SchemeCodec.idFor(name))

    private fun uniqueName(wanted: String, all: List<TerminalColorScheme>): String {
        val base = wanted.trim().take(SchemeCodec.MAX_NAME_LENGTH).trim()
        if (all.none { it.name.equals(base, ignoreCase = true) }) return base
        var n = 2
        while (true) {
            val suffix = " $n"
            val candidate = base.take(SchemeCodec.MAX_NAME_LENGTH - suffix.length).trim() + suffix
            if (all.none { it.name.equals(candidate, ignoreCase = true) }) return candidate
            n++
        }
    }
}
