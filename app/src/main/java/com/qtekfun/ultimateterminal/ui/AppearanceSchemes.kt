// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.appearance.AppearanceViewModel
import com.qtekfun.ultimateterminal.domain.appearance.SchemeEditor
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSpacing

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
    var sheet by remember { mutableStateOf<TerminalColorScheme?>(null) }
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

    SchemeList(all, settings.terminalSchemeId) { sheet = it }
    IosSection {
        IosListRow(
            title = stringResource(R.string.appearance_scheme_new),
            glyph = IosGlyph.PLUS,
            onClick = { onEdit(SchemeEdit(null, SchemeEditor.blank(newName, current, all))) }
        )
        IosListRow(
            title = stringResource(R.string.appearance_scheme_import),
            glyph = IosGlyph.DOWNLOAD,
            showSeparator = false,
            onClick = { importer.launch(SchemeMimeTypes) }
        )
    }
    sheet?.let { scheme ->
        SchemeSheet(
            scheme = scheme,
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
            ),
            onDismiss = { sheet = null }
        )
    }
    deleting?.let { scheme ->
        DeleteSchemeAlert(
            scheme = scheme,
            onConfirm = { viewModel.deleteScheme(scheme.id) },
            onDismiss = { deleting = null }
        )
    }
}

/** Every scheme, with the ANSI colors under its name and a check on the one in use. */
@Composable
private fun SchemeList(
    all: List<TerminalColorScheme>,
    selectedId: String,
    onOpen: (TerminalColorScheme) -> Unit
) {
    IosSection(header = stringResource(R.string.appearance_section_schemes)) {
        all.forEachIndexed { index, scheme ->
            IosListRow(
                title = scheme.name,
                accessory = checkIf(scheme.id == selectedId),
                showSeparator = index != all.lastIndex,
                detail = {
                    AnsiSwatches(scheme.ansi.take(SWATCHES), Modifier.padding(top = IosSpacing.xs))
                },
                onClick = { onOpen(scheme) }
            )
        }
    }
}

/** What can be done with one scheme: the sheet that slides up when its row is tapped. */
@Composable
private fun SchemeSheet(
    scheme: TerminalColorScheme,
    actions: SchemeActions,
    onDismiss: () -> Unit
) {
    val items = buildList {
        add(IosAction(stringResource(R.string.appearance_scheme_use), onClick = actions.select))
        actions.edit?.let {
            add(IosAction(stringResource(R.string.appearance_scheme_edit), onClick = it))
        }
        add(
            IosAction(
                stringResource(R.string.appearance_scheme_duplicate),
                onClick = actions.duplicate
            )
        )
        add(IosAction(stringResource(R.string.appearance_scheme_export), onClick = actions.export))
        actions.delete?.let {
            add(
                IosAction(
                    stringResource(R.string.appearance_scheme_delete),
                    IosActionRole.DESTRUCTIVE,
                    it
                )
            )
        }
    }
    IosActionSheet(
        actions = items,
        cancelLabel = stringResource(R.string.appearance_cancel),
        onDismiss = onDismiss,
        title = scheme.name
    )
}

@Composable
private fun DeleteSchemeAlert(
    scheme: TerminalColorScheme,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    IosAlert(
        title = stringResource(R.string.appearance_scheme_delete_title),
        message = stringResource(R.string.appearance_scheme_delete_message, scheme.name),
        actions = listOf(
            IosAction(stringResource(R.string.appearance_cancel), IosActionRole.CANCEL),
            IosAction(
                stringResource(R.string.appearance_scheme_delete),
                IosActionRole.DESTRUCTIVE,
                onConfirm
            )
        ),
        onDismiss = onDismiss
    )
}

private const val SWATCHES = 8
