// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.appearance.AppearanceViewModel
import com.qtekfun.ultimateterminal.domain.appearance.FontCatalog
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.terminal.FontZoom

private val MinTouch = 48.dp

/** What the document picker offers when importing a font: fonts, and whatever a file manager calls them. */
private val FontMimeTypes = arrayOf(
    "font/ttf",
    "font/otf",
    "font/sfnt",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream"
)

/** The font, its size and its spacing; fonts of the user's own can be imported and removed. */
@Composable
internal fun FontSection(settings: AppSettings, viewModel: AppearanceViewModel) {
    val appearance = settings.appearance
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFont(uri)
    }
    fun select(id: String) =
        viewModel.update { it.copy(appearance = it.appearance.copy(fontId = id)) }
    fun space(transform: (TerminalAppearance) -> TerminalAppearance) =
        viewModel.update { it.copy(appearance = transform(it.appearance)) }

    SectionTitle(stringResource(R.string.appearance_section_font))
    Text(
        stringResource(R.string.appearance_font_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FontRow(
        name = stringResource(R.string.appearance_font_bundled),
        selected = appearance.fontId == FontCatalog.BUNDLED_ID,
        onSelect = { select(FontCatalog.BUNDLED_ID) },
        onDelete = null
    )
    settings.customFonts.forEach { font ->
        FontRow(
            name = font.name,
            selected = appearance.fontId == font.id,
            onSelect = { select(font.id) },
            onDelete = { viewModel.deleteFont(font.id) }
        )
    }
    OutlinedButton(
        onClick = { picker.launch(FontMimeTypes) },
        modifier = Modifier.heightIn(min = MinTouch)
    ) { Text(stringResource(R.string.appearance_font_import)) }

    LabeledSlider(
        label = stringResource(R.string.appearance_font_size),
        valueText = "${decimal(settings.terminalFontSizeSp, 1)} sp",
        value = settings.terminalFontSizeSp,
        range = FontZoom.MIN_SP..FontZoom.MAX_SP
    ) { size -> viewModel.update { it.copy(terminalFontSizeSp = size) } }
    LabeledSlider(
        label = stringResource(R.string.appearance_line_spacing),
        valueText = "x" + decimal(appearance.lineSpacing),
        value = appearance.lineSpacing,
        range = TerminalAppearance.LINE_SPACING_RANGE
    ) { value -> space { it.copy(lineSpacing = value) } }
    LabeledSlider(
        label = stringResource(R.string.appearance_letter_spacing),
        valueText = decimal(appearance.letterSpacing) + " em",
        value = appearance.letterSpacing,
        range = TerminalAppearance.LETTER_SPACING_RANGE
    ) { value -> space { it.copy(letterSpacing = value) } }
}

@Composable
private fun FontRow(
    name: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, modifier = Modifier.weight(1f).padding(start = 12.dp))
        if (onDelete != null) {
            val label = stringResource(R.string.appearance_font_delete, name)
            TextButton(
                onClick = onDelete,
                modifier = Modifier.semantics {
                    contentDescription =
                        label
                }
            ) {
                Text(stringResource(R.string.appearance_remove))
            }
        }
    }
}
