// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.appearance.AppearanceViewModel
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog

private val MinTouch = 48.dp

/**
 * The appearance screen: theme, color schemes, font and design of the terminal, with a live
 * preview on top (T12c). T16 will link to it from the general settings.
 */
@Composable
fun AppearanceScreen(onClose: () -> Unit, viewModel: AppearanceViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val settings = state.settings ?: return@Surface
        var editing by remember { mutableStateOf<SchemeEdit?>(null) }
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.appearance_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.appearance_close))
                }
            }
            state.message?.let { AppearanceMessageBar(it, viewModel::dismissMessage) }
            AppearancePreview(
                scheme = SchemeCatalog.resolve(settings.terminalSchemeId, settings.customSchemes),
                appearance = settings.appearance,
                fontSizeSp = settings.terminalFontSizeSp,
                typefaces = viewModel.typefaces(settings)
            )
            ThemeSection(settings, viewModel::update)
            SchemeSection(settings, viewModel, onEdit = { editing = it })
            FontSection(settings, viewModel)
            DesignSection(settings, viewModel::update)
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
}

@Composable
private fun ThemeSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    SectionTitle(stringResource(R.string.appearance_section_theme))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = settings.themeMode == mode,
                onClick = { update { it.copy(themeMode = mode) } },
                label = { Text(stringResource(themeModeLabel(mode))) },
                modifier = Modifier.heightIn(min = MinTouch)
            )
        }
    }
    SettingSwitch(
        title = stringResource(R.string.appearance_oled),
        hint = stringResource(R.string.appearance_oled_hint),
        checked = settings.oledBlack
    ) { checked -> update { it.copy(oledBlack = checked) } }
    SettingSwitch(
        title = stringResource(R.string.appearance_dynamic),
        hint = stringResource(R.string.appearance_dynamic_hint),
        checked = settings.dynamicColor
    ) { checked -> update { it.copy(dynamicColor = checked) } }
}

private fun themeModeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.appearance_theme_system
    ThemeMode.LIGHT -> R.string.appearance_theme_light
    ThemeMode.DARK -> R.string.appearance_theme_dark
}
