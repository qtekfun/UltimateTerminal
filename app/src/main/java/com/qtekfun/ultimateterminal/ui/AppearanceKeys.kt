// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.AppSettings

/** The style of the extra keys (Esc, Tab, Ctrl...) with a preview, in the appearance screen. */
@Composable
internal fun KeysSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    SectionTitle(stringResource(R.string.appearance_section_keys))
    ExtraKeyStylePicker(settings, onStyle = { style ->
        update { it.copy(appearance = it.appearance.copy(extraKeyStyle = style)) }
    })
}
