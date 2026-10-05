// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.appearance.ColorHex
import com.qtekfun.ultimateterminal.domain.appearance.ColorSlot
import com.qtekfun.ultimateterminal.domain.appearance.SchemeEditor
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosRadius
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosSpacing
import com.qtekfun.ultimateterminal.ui.ios.IosText
import com.qtekfun.ultimateterminal.ui.ios.IosTextField
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

/** Every color of a scheme, in the order the editor lists them. */
private val EditableSlots: List<ColorSlot> = listOf(
    ColorSlot.Foreground,
    ColorSlot.Background,
    ColorSlot.Cursor,
    ColorSlot.Selection
) + (0 until TerminalColorScheme.ANSI_COLORS).map { ColorSlot.Ansi(it) }

/**
 * An iOS sheet that edits one scheme: its name, its 20 colors with a picker, and a hint for each
 * color that is hard to read on the background. A scheme that is refused (a name already in use)
 * keeps the editor open with the reason.
 */
@Composable
internal fun SchemeEditorDialog(
    edit: SchemeEdit,
    onSave: (TerminalColorScheme, (DomainError?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var scheme by remember { mutableStateOf(edit.scheme) }
    var name by remember { mutableStateOf(edit.scheme.name) }
    var problem by remember { mutableStateOf<DomainError?>(null) }
    var picking by remember { mutableStateOf<ColorSlot?>(null) }
    val ansiNames = stringArrayResource(R.array.appearance_ansi_names)
    val title = if (edit.previousId == null) {
        R.string.appearance_editor_new
    } else {
        R.string.appearance_editor_edit
    }

    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column(Modifier.imePadding()) {
            IosSheetHeader(
                title = stringResource(title),
                cancelLabel = stringResource(R.string.appearance_cancel),
                confirmLabel = stringResource(R.string.appearance_save),
                onCancel = onDismiss,
                onConfirm = {
                    when (val renamed = SchemeEditor.renamed(scheme, name)) {
                        is Outcome.Failure -> problem = renamed.error

                        is Outcome.Success -> onSave(renamed.value) { error ->
                            if (error == null) onDismiss() else problem = error
                        }
                    }
                }
            )
            IosSection(footer = problem?.let { problemText(it, name) }) {
                IosTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        problem = null
                    },
                    label = stringResource(R.string.appearance_editor_name),
                    isError = problem != null,
                    showSeparator = false
                )
            }
            SchemeSample(scheme)
            ContrastHints(scheme, ansiNames)
            ColorList(scheme, ansiNames) { picking = it }
        }
    }
    picking?.let { slot ->
        ColorPickerDialog(
            title = slotLabel(slot, ansiNames),
            initial = colorOf(scheme, slot),
            onPicked = { scheme = SchemeEditor.withColor(scheme, slot, it) },
            onDismiss = { picking = null }
        )
    }
}

/** Every color of the scheme as a row with its chip and hex code; a tap opens the picker. */
@Composable
private fun ColorList(
    scheme: TerminalColorScheme,
    ansiNames: Array<String>,
    onPick: (ColorSlot) -> Unit
) {
    IosSection {
        EditableSlots.forEachIndexed { index, slot ->
            val argb = colorOf(scheme, slot)
            IosListRow(
                title = slotLabel(slot, ansiNames),
                accessory = IosAccessory.Swatch(argb, ColorHex.format(argb)),
                showSeparator = index != EditableSlots.lastIndex,
                onClick = { onPick(slot) }
            )
        }
    }
}

@Composable
private fun problemText(error: DomainError, typed: String): String = when (error) {
    is DomainError.NameTaken -> stringResource(
        R.string.appearance_msg_scheme_name_taken,
        typed.trim()
    )

    is DomainError.InvalidName ->
        stringResource(R.string.appearance_editor_name_invalid, SchemeCodec.MAX_NAME_LENGTH)

    else -> stringResource(R.string.appearance_msg_scheme_limit)
}

/** The scheme's own foreground on its background, with the 16 colors under it. */
@Composable
private fun SchemeSample(scheme: TerminalColorScheme) {
    Column(
        Modifier
            .padding(horizontal = IosSpacing.md)
            .padding(top = IosSpacing.lg)
            .fillMaxWidth()
            .background(Color(scheme.background), RoundedCornerShape(IosRadius.card))
            .padding(IosSpacing.sm)
    ) {
        IosText(
            "user@host:~$ echo hello",
            style = IosTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
            color = Color(scheme.foreground)
        )
        AnsiSwatches(scheme.ansi, Modifier.padding(top = IosSpacing.sm))
    }
}

@Composable
private fun ContrastHints(scheme: TerminalColorScheme, ansiNames: Array<String>) {
    val issues = remember(scheme) { SchemeEditor.contrastIssues(scheme).take(MAX_HINTS) }
    if (issues.isEmpty()) return
    IosSection(header = stringResource(R.string.appearance_contrast_title)) {
        issues.forEachIndexed { index, issue ->
            IosListRow(
                title = stringResource(
                    R.string.appearance_contrast_item,
                    slotLabel(issue.slot, ansiNames),
                    decimal(issue.ratio.toFloat(), 1),
                    decimal(issue.minimum.toFloat(), 1)
                ),
                destructive = true,
                showSeparator = index != issues.lastIndex
            )
        }
    }
}

private fun colorOf(scheme: TerminalColorScheme, slot: ColorSlot): Int = when (slot) {
    is ColorSlot.Ansi -> scheme.ansi[slot.index]
    ColorSlot.Foreground -> scheme.foreground
    ColorSlot.Background -> scheme.background
    ColorSlot.Cursor -> scheme.cursor
    ColorSlot.Selection -> scheme.selection
}

@Composable
private fun slotLabel(slot: ColorSlot, ansiNames: Array<String>): String = when (slot) {
    is ColorSlot.Ansi -> ansiNames[slot.index]
    ColorSlot.Foreground -> stringResource(R.string.appearance_slot_foreground)
    ColorSlot.Background -> stringResource(R.string.appearance_slot_background)
    ColorSlot.Cursor -> stringResource(R.string.appearance_slot_cursor)
    ColorSlot.Selection -> stringResource(R.string.appearance_slot_selection)
}

private const val MAX_HINTS = 5
