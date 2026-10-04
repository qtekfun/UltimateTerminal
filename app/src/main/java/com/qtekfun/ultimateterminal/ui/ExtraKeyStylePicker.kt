// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.appearance.ExtraKeyStyle
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.LatchState
import com.qtekfun.ultimateterminal.domain.terminal.StickyState
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.ThemeDecision
import com.qtekfun.ultimateterminal.domain.theme.resolveTheme
import com.qtekfun.ultimateterminal.ui.ios.IosSegmentedControl
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

/** A short row to show the style with: Esc, Tab, an armed Ctrl and two arrows. */
private val PreviewKeys = ExtraKeysConfig(
    rows = listOf(listOf("esc", "tab", "ctrl", "left", "right"))
)
private val PreviewSticky = StickyState(ctrl = LatchState.ARMED)

/**
 * Picks the style of the extra-keys row, and shows it as it will look in the colors of the current
 * scheme. It reads [settings] and reports the choice through [onStyle], and does not store
 * anything itself, so the appearance screen and the Keyboard section of the settings can both
 * show it (see `DECISIONS.md`, T22d).
 */
@Composable
fun ExtraKeyStylePicker(
    settings: AppSettings,
    onStyle: (ExtraKeyStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val styles = ExtraKeyStyle.entries
    val selected = settings.appearance.extraKeyStyle
    val scheme = remember(settings.terminalSchemeId, settings.customSchemes) {
        SchemeCatalog.resolve(settings.terminalSchemeId, settings.customSchemes)
    }
    val decision = resolveTheme(settings.themeMode, isSystemInDarkTheme(), settings.oledBlack)
    val chrome = rememberChromePalette(scheme, settings.appearance)
    val labels = styles.map { stringResource(styleLabel(it)) }
    val previewDescription = stringResource(R.string.extra_key_style_preview)
    IosTheme(ThemeDecision(decision.dark, decision.oled), scheme) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IosSegmentedControl(
                options = labels,
                selectedIndex = styles.indexOf(selected),
                onSelect = { onStyle(styles[it]) }
            )
            // The terminal's own background behind the row, as it will be under the real one.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(chrome.corner))
                    .background(Color(scheme.background))
                    .semantics { contentDescription = previewDescription }
            ) {
                CompositionLocalProvider(LocalChromePalette provides chrome) {
                    Box(Modifier.clearAndSetSemantics {}) {
                        ExtraKeysRow(PreviewKeys, PreviewSticky, onKey = {})
                    }
                }
            }
        }
    }
}

private fun styleLabel(style: ExtraKeyStyle): Int = when (style) {
    ExtraKeyStyle.FLAT -> R.string.extra_key_style_flat
    ExtraKeyStyle.CAPSULE -> R.string.extra_key_style_capsule
    ExtraKeyStyle.CLASSIC -> R.string.extra_key_style_classic
}
