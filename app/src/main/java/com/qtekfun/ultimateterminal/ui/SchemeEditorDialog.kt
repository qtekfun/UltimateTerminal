// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.appearance.ColorHex
import com.qtekfun.ultimateterminal.domain.appearance.ColorSlot
import com.qtekfun.ultimateterminal.domain.appearance.SchemeEditor
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme

private val MinTouch = 48.dp

/** Every color of a scheme, in the order the editor lists them. */
private val EditableSlots: List<ColorSlot> = listOf(
    ColorSlot.Foreground,
    ColorSlot.Background,
    ColorSlot.Cursor,
    ColorSlot.Selection
) + (0 until TerminalColorScheme.ANSI_COLORS).map { ColorSlot.Ansi(it) }

/**
 * A full-screen editor for one scheme: its name, its 20 colors with a picker, and a hint for each
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val title = if (edit.previousId == null) {
                    R.string.appearance_editor_new
                } else {
                    R.string.appearance_editor_edit
                }
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                NameField(name, problem) {
                    name = it
                    problem = null
                }
                SchemeSample(scheme)
                ContrastHints(scheme, ansiNames)
                EditableSlots.forEach { slot ->
                    ColorRow(colorOf(scheme, slot), slotLabel(slot, ansiNames)) { picking = slot }
                }
                EditorButtons(onDismiss) {
                    when (val renamed = SchemeEditor.renamed(scheme, name)) {
                        is Outcome.Failure -> problem = renamed.error

                        is Outcome.Success -> onSave(renamed.value) { error ->
                            if (error == null) onDismiss() else problem = error
                        }
                    }
                }
            }
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

@Composable
private fun NameField(name: String, problem: DomainError?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.appearance_editor_name)) },
        isError = problem != null,
        supportingText = { problem?.let { Text(problemText(it, name)) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EditorButtons(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = MinTouch)) {
            Text(stringResource(R.string.appearance_cancel))
        }
        Button(onClick = onSave, modifier = Modifier.heightIn(min = MinTouch)) {
            Text(stringResource(R.string.appearance_save))
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
            .fillMaxWidth()
            .background(Color(scheme.background), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            "user@host:~$ echo hello",
            color = Color(scheme.foreground),
            fontFamily = FontFamily.Monospace
        )
        AnsiSwatches(scheme.ansi, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ContrastHints(scheme: TerminalColorScheme, ansiNames: Array<String>) {
    val issues = remember(scheme) { SchemeEditor.contrastIssues(scheme).take(MAX_HINTS) }
    if (issues.isEmpty()) return
    Text(
        stringResource(R.string.appearance_contrast_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.error
    )
    issues.forEach { issue ->
        Text(
            stringResource(
                R.string.appearance_contrast_item,
                slotLabel(issue.slot, ansiNames),
                decimal(issue.ratio.toFloat(), 1),
                decimal(issue.minimum.toFloat(), 1)
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ColorRow(argb: Int, label: String, onClick: () -> Unit) {
    val description = stringResource(R.string.appearance_color_row, label, ColorHex.format(argb))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(Color(argb), RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
        )
        Text(label, modifier = Modifier.weight(1f))
        Text(ColorHex.format(argb), fontFamily = FontFamily.Monospace)
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
