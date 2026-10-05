// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.appearance.AppearanceViewModel
import com.qtekfun.ultimateterminal.domain.appearance.FontCatalog
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.terminal.FontZoom
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection

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
    // The font whose sheet (use it, or remove it) is open.
    var sheet by remember { mutableStateOf<Pair<String, String>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFont(uri)
    }
    fun select(id: String) =
        viewModel.update { it.copy(appearance = it.appearance.copy(fontId = id)) }
    IosSection(
        header = stringResource(R.string.appearance_section_font),
        footer = stringResource(R.string.appearance_font_hint)
    ) {
        IosListRow(
            title = stringResource(R.string.appearance_font_bundled),
            accessory = checkIf(appearance.fontId == FontCatalog.BUNDLED_ID),
            onClick = { select(FontCatalog.BUNDLED_ID) }
        )
        settings.customFonts.forEach { font ->
            IosListRow(
                title = font.name,
                accessory = checkIf(appearance.fontId == font.id),
                onClick = { sheet = font.id to font.name }
            )
        }
        IosListRow(
            title = stringResource(R.string.appearance_font_import),
            glyph = IosGlyph.FOLDER,
            accessory = IosAccessory.Chevron,
            showSeparator = false,
            onClick = { picker.launch(FontMimeTypes) }
        )
    }
    FontSpacing(settings, viewModel)
    sheet?.let { (id, name) ->
        IosActionSheet(
            actions = listOf(
                IosAction(stringResource(R.string.appearance_font_use)) { select(id) },
                IosAction(stringResource(R.string.appearance_remove), IosActionRole.DESTRUCTIVE) {
                    viewModel.deleteFont(id)
                }
            ),
            cancelLabel = stringResource(R.string.appearance_cancel),
            onDismiss = { sheet = null },
            title = name
        )
    }
}

internal fun checkIf(selected: Boolean): IosAccessory =
    if (selected) IosAccessory.Check else IosAccessory.None

/** The size of the font and the spacing of its lines and letters. */
@Composable
private fun FontSpacing(settings: AppSettings, viewModel: AppearanceViewModel) {
    val appearance = settings.appearance
    fun space(transform: (TerminalAppearance) -> TerminalAppearance) =
        viewModel.update { it.copy(appearance = transform(it.appearance)) }

    IosSection {
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
            range = TerminalAppearance.LETTER_SPACING_RANGE,
            showSeparator = false
        ) { value -> space { it.copy(letterSpacing = value) } }
    }
}
