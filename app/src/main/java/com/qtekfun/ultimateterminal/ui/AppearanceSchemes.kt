// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.appearance.AppearanceViewModel
import com.qtekfun.ultimateterminal.domain.appearance.SchemeEditor
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme

private val MinTouch = 48.dp

/** A scheme being created or edited; [previousId] is null for a new one. */
internal data class SchemeEdit(val previousId: String?, val scheme: TerminalColorScheme)

private class SchemeActions(
    val select: () -> Unit,
    val edit: (() -> Unit)?,
    val duplicate: () -> Unit,
    val export: () -> Unit,
    val delete: (() -> Unit)?
)

private val SchemeMimeTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

/** The built-in and the custom color schemes: choose one, edit your own, import and export. */
@Composable
internal fun SchemeSection(
    settings: AppSettings,
    viewModel: AppearanceViewModel,
    onEdit: (SchemeEdit) -> Unit
) {
    val all = BuiltInSchemes.all + settings.customSchemes
    var deleting by remember { mutableStateOf<TerminalColorScheme?>(null) }
    var exporting by remember { mutableStateOf<TerminalColorScheme?>(null) }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importScheme(uri)
        }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val scheme = exporting
        exporting = null
        if (uri != null && scheme != null) viewModel.exportScheme(scheme, uri)
    }
    val copyOf = stringResource(R.string.appearance_scheme_copy_of)
    val newName = stringResource(R.string.appearance_scheme_new_name)
    val current = SchemeCatalog.resolve(settings.terminalSchemeId, settings.customSchemes)

    SectionTitle(stringResource(R.string.appearance_section_schemes))
    all.forEach { scheme ->
        SchemeRow(
            scheme = scheme,
            selected = scheme.id == settings.terminalSchemeId,
            actions = SchemeActions(
                select = { viewModel.update { it.copy(terminalSchemeId = scheme.id) } },
                edit = if (scheme.builtIn) null else ({ onEdit(SchemeEdit(scheme.id, scheme)) }),
                duplicate = {
                    onEdit(SchemeEdit(null, SchemeEditor.duplicate(scheme, copyOf, all)))
                },
                export = {
                    exporting = scheme
                    exporter.launch(scheme.id + ".json")
                },
                delete = if (scheme.builtIn) null else ({ deleting = scheme })
            )
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onEdit(SchemeEdit(null, SchemeEditor.blank(newName, current, all))) },
            modifier = Modifier.heightIn(min = MinTouch)
        ) { Text(stringResource(R.string.appearance_scheme_new)) }
        OutlinedButton(
            onClick = { importer.launch(SchemeMimeTypes) },
            modifier = Modifier.heightIn(min = MinTouch)
        ) { Text(stringResource(R.string.appearance_scheme_import)) }
    }
    deleting?.let { scheme ->
        DeleteSchemeDialog(
            scheme = scheme,
            onConfirm = {
                viewModel.deleteScheme(scheme.id)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun DeleteSchemeDialog(
    scheme: TerminalColorScheme,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_scheme_delete_title)) },
        text = { Text(stringResource(R.string.appearance_scheme_delete_message, scheme.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.appearance_scheme_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.appearance_cancel)) }
        }
    )
}

@Composable
private fun SchemeRow(scheme: TerminalColorScheme, selected: Boolean, actions: SchemeActions) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = actions.select),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
            Text(scheme.name)
            AnsiSwatches(scheme.ansi.take(SWATCHES), Modifier.padding(top = 4.dp))
        }
        val label = stringResource(R.string.appearance_scheme_menu, scheme.name)
        Box {
            TextButton(
                onClick = { menu = true },
                modifier = Modifier.semantics {
                    contentDescription =
                        label
                }
            ) {
                Text("⋮")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuAction(R.string.appearance_scheme_edit, actions.edit) { menu = false }
                MenuAction(R.string.appearance_scheme_duplicate, actions.duplicate) { menu = false }
                MenuAction(R.string.appearance_scheme_export, actions.export) { menu = false }
                MenuAction(R.string.appearance_scheme_delete, actions.delete) { menu = false }
            }
        }
    }
}

@Composable
private fun MenuAction(label: Int, action: (() -> Unit)?, close: () -> Unit) {
    if (action == null) return
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            close()
            action()
        }
    )
}

private const val SWATCHES = 8
