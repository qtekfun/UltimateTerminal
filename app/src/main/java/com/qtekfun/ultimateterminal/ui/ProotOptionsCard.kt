// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.storage.ProotOptionsViewModel

private val MIN_TOUCH = 48.dp

/** The switch for proot's compatibility mode; it applies to the tabs opened afterwards. */
@Composable
fun ProotOptionsCard(viewModel: ProotOptionsViewModel = viewModel()) {
    val enabled by viewModel.compatibilityMode.collectAsStateWithLifecycle()
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.proot_compat_label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = viewModel::setCompatibilityMode,
                    modifier = Modifier.heightIn(min = MIN_TOUCH)
                )
            }
            Text(
                stringResource(R.string.proot_compat_explanation),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
