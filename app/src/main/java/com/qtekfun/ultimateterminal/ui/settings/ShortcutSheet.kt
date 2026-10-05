// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.settings.ShortcutDisplay
import com.qtekfun.ultimateterminal.domain.settings.ShortcutEditing
import com.qtekfun.ultimateterminal.domain.settings.ShortcutPreview
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.KeyChord
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutConflict
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import com.qtekfun.ultimateterminal.settings.SettingsViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosTextField

/**
 * The key combinations of one action. The combination is pressed on a keyboard or typed (`ctrl+alt+k`);
 * before it is added the sheet says what it would take from another action and from the programs in
 * the terminal (the conflicts are warnings, D-T12b-6). [map] is read from the settings, so the sheet
 * shows the result of each change.
 */
@Composable
internal fun ShortcutSheet(
    shortcut: AppShortcut,
    map: ShortcutMap,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<KeyChord?>(null) }
    val preview = ShortcutEditing.preview(map, shortcut, text)
    val addable = preview is ShortcutPreview.Ready && !preview.alreadyBound
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState())) {
            IosSheetHeader(
                title = shortcutLabel(shortcut),
                cancelLabel = stringResource(R.string.shortcuts_done),
                confirmLabel = stringResource(R.string.shortcuts_add),
                onCancel = onDismiss,
                onConfirm = {
                    val typed = text
                    viewModel.editShortcuts { ShortcutEditing.add(it, shortcut, typed) }
                    text = ""
                },
                confirmEnabled = addable
            )
            CurrentChords(ShortcutEditing.chordsOf(map, shortcut)) { removing = it }
            AddChordSection(text, preview) { text = it }
            PreviewNote(preview)
        }
    }
    removing?.let { chord ->
        val name = ShortcutDisplay.pretty(chord).orEmpty()
        IosActionSheet(
            actions = listOf(
                IosAction(
                    stringResource(R.string.shortcuts_remove, name),
                    IosActionRole.DESTRUCTIVE
                ) {
                    viewModel.editShortcuts { ShortcutEditing.remove(it, chord) }
                }
            ),
            cancelLabel = stringResource(R.string.dialog_cancel),
            onDismiss = { removing = null },
            title = name
        )
    }
}

/** The box where the combination is pressed or typed; a key press with Ctrl or Alt fills it. */
@Composable
private fun AddChordSection(text: String, preview: ShortcutPreview, onText: (String) -> Unit) {
    IosSection(
        header = stringResource(R.string.shortcuts_add_header),
        footer = stringResource(R.string.shortcuts_field_hint)
    ) {
        IosTextField(
            value = text,
            onValueChange = onText,
            label = stringResource(R.string.shortcuts_field),
            modifier = Modifier.onPreviewKeyEvent { event ->
                val typed = if (event.type == KeyEventType.KeyDown) captured(event) else null
                if (typed != null) onText(typed)
                typed != null
            },
            isError = preview == ShortcutPreview.Unreadable ||
                preview == ShortcutPreview.NeedsModifier,
            showSeparator = false
        )
    }
}

/** The text of the combination a key press made, or null while only modifiers are held. */
private fun captured(event: androidx.compose.ui.input.key.KeyEvent): String? {
    val typed = ShortcutEditing.captured(
        event.key.nativeKeyCode,
        ctrl = event.isCtrlPressed,
        alt = event.isAltPressed,
        shift = event.isShiftPressed
    )
    // Plain typing goes into the box as text; only a chord with Ctrl or Alt is captured.
    return typed?.takeIf { event.isCtrlPressed || event.isAltPressed }
}

@Composable
private fun CurrentChords(chords: List<KeyChord>, onTap: (KeyChord) -> Unit) {
    IosSection(header = stringResource(R.string.shortcuts_current_header)) {
        if (chords.isEmpty()) {
            IosListRow(title = stringResource(R.string.shortcuts_none), showSeparator = false)
        }
        chords.forEachIndexed { index, chord ->
            IosListRow(
                title = ShortcutDisplay.pretty(chord).orEmpty(),
                glyph = IosGlyph.TRASH,
                showSeparator = index != chords.lastIndex,
                onClick = { onTap(chord) }
            )
        }
    }
}

/** What adding the typed combination would do: what it takes, from whom, and what it costs. */
@Composable
private fun PreviewNote(preview: ShortcutPreview) {
    val lines = when (preview) {
        ShortcutPreview.Empty -> emptyList()

        ShortcutPreview.Unreadable -> listOf(stringResource(R.string.shortcuts_preview_unreadable))

        ShortcutPreview.NeedsModifier ->
            listOf(stringResource(R.string.shortcuts_preview_needs_modifier))

        is ShortcutPreview.Ready -> readyLines(preview)
    }
    if (lines.isNotEmpty()) {
        IosSection {
            lines.forEachIndexed { index, line ->
                IosListRow(title = line, showSeparator = index != lines.lastIndex)
            }
        }
    }
}

@Composable
private fun readyLines(preview: ShortcutPreview.Ready): List<String> {
    val name = ShortcutDisplay.pretty(preview.chord).orEmpty()
    return listOfNotNull(
        if (preview.alreadyBound) stringResource(R.string.shortcuts_preview_exists, name) else null,
        preview.replaced?.let {
            stringResource(R.string.shortcuts_preview_replaces, name, shortcutLabel(it))
        },
        when (preview.conflict) {
            is ShortcutConflict.StealsControlKey ->
                stringResource(R.string.shortcuts_preview_steals_control, name)

            is ShortcutConflict.StealsReadlineMeta ->
                stringResource(R.string.shortcuts_preview_steals_meta, name)

            else -> null
        }
    )
}
