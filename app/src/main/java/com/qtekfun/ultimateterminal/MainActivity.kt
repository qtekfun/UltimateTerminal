// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.ui.DistroScreen
import com.qtekfun.ultimateterminal.ui.TerminalScreen
import com.qtekfun.ultimateterminal.ui.theme.UltimateTerminalTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltimateTerminalTheme {
                var showDistros by rememberSaveable { mutableStateOf(false) }
                Box {
                    TerminalScreen()
                    // Temporary entry point: the tab bar of T09 will hold this action.
                    TextButton(
                        onClick = { showDistros = true },
                        modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding()
                    ) { Text(stringResource(R.string.distros_open)) }
                    if (showDistros) DistroScreen(onClose = { showDistros = false })
                }
            }
        }
    }
}
