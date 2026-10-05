// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.appearance.AppearanceViewModel
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.ui.ios.IosBarButton
import com.qtekfun.ultimateterminal.ui.ios.IosLargeTitleScreen
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSpacing

/**
 * The appearance screen: theme, color schemes, font and design of the terminal, with a live
 * preview on top (T12c), in the iOS style (T22c).
 */
@Composable
fun AppearanceScreen(onClose: () -> Unit, viewModel: AppearanceViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SchemeEdit?>(null) }
    BackHandler(onBack = onClose)
    IosLargeTitleScreen(
        title = stringResource(R.string.appearance_title),
        leading = { IosBarButton(stringResource(R.string.appearance_close), onClose) }
    ) {
        val settings = state.settings ?: return@IosLargeTitleScreen
        state.message?.let { message ->
            item { AppearanceMessageBar(message, viewModel::dismissMessage) }
        }
        item {
            AppearancePreview(
                scheme = SchemeCatalog.resolve(settings.terminalSchemeId, settings.customSchemes),
                appearance = settings.appearance,
                fontSizeSp = settings.terminalFontSizeSp,
                typefaces = viewModel.typefaces(settings),
                modifier = Modifier.padding(horizontal = IosSpacing.md).padding(top = IosSpacing.md)
            )
        }
        item { ThemeSection(settings, viewModel::update) }
        item { SchemeSection(settings, viewModel, onEdit = { editing = it }) }
        item { FontSection(settings, viewModel) }
        item { KeysSection(settings, viewModel::update) }
        item { DesignSection(settings, viewModel::update) }
    }
    editing?.let { edit ->
        SchemeEditorDialog(
            edit = edit,
            onSave = { scheme, onDone ->
                viewModel.saveScheme(edit.previousId, scheme, onDone)
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun ThemeSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val modes = ThemeMode.entries
    IosSection(header = stringResource(R.string.appearance_section_theme)) {
        SegmentedRow(
            label = null,
            options = modes.map { stringResource(themeModeLabel(it)) },
            selectedIndex = modes.indexOf(settings.themeMode),
            onSelect = { index -> update { it.copy(themeMode = modes[index]) } }
        )
        SettingSwitch(
            title = stringResource(R.string.appearance_oled),
            hint = stringResource(R.string.appearance_oled_hint),
            checked = settings.oledBlack
        ) { checked -> update { it.copy(oledBlack = checked) } }
        SettingSwitch(
            title = stringResource(R.string.appearance_dynamic),
            hint = stringResource(R.string.appearance_dynamic_hint),
            checked = settings.dynamicColor,
            showSeparator = false
        ) { checked -> update { it.copy(dynamicColor = checked) } }
    }
}

private fun themeModeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.appearance_theme_system
    ThemeMode.LIGHT -> R.string.appearance_theme_light
    ThemeMode.DARK -> R.string.appearance_theme_dark
}
