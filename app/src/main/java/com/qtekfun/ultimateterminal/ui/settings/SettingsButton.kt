// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosIcon
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

private val TouchSize = 48.dp

/**
 * The gear that opens Settings (SPEC RF-11): always in the tab bar, next to the "+", 48 dp wide and
 * tall, with its spoken name. Not behind a menu: the user could not find the settings there.
 */
@Composable
fun SettingsButton(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.settings_open)
    Box(
        modifier
            .size(TouchSize)
            .semantics { contentDescription = label }
            .clickable(onClickLabel = label, role = Role.Button, onClick = onOpen),
        contentAlignment = Alignment.Center
    ) {
        IosIcon(IosGlyph.SETTINGS, null, tint = IosTheme.colors.tint)
    }
}
