// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.qtekfun.ultimateterminal.ui.ios.IosBarButton
import com.qtekfun.ultimateterminal.ui.ios.IosLargeTitleScreen
import com.qtekfun.ultimateterminal.ui.ios.IosSection

/** A page of Settings: a large title and a back button that names where it goes back to. */
@Composable
internal fun SettingsPage(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    IosLargeTitleScreen(
        title = title,
        leading = { IosBarButton(backLabel, onBack) },
        content = content
    )
}

/** One grouped section as one item of the page's list. */
internal fun LazyListScope.section(
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    item { IosSection(header = header, footer = footer, content = content) }
}
