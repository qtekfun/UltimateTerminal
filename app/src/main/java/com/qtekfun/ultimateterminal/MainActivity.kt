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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.terminal.SessionManager
import com.qtekfun.ultimateterminal.ui.DistroScreen
import com.qtekfun.ultimateterminal.ui.SessionPrompts
import com.qtekfun.ultimateterminal.ui.TerminalScreen
import com.qtekfun.ultimateterminal.ui.theme.UltimateTerminalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltimateTerminalTheme {
                val sessions by sessionManager.state.collectAsStateWithLifecycle()
                var hadSessions by remember { mutableStateOf(false) }
                var showDistros by rememberSaveable { mutableStateOf(false) }
                // "Exit" in the notification closes every session: close the screen with them.
                LaunchedEffect(sessions.items.isEmpty()) {
                    if (sessions.items.isNotEmpty()) {
                        hadSessions = true
                    } else if (hadSessions) {
                        finishAndRemoveTask()
                    }
                }
                Box {
                    TerminalScreen()
                    // Temporary entry point to the distro screen (T07): it belongs in the tab bar's
                    // "new tab" menu, which T09 owns; see DECISIONS.md, D-T07-7.
                    TextButton(
                        onClick = { showDistros = true },
                        modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding()
                    ) { Text(stringResource(R.string.distros_open)) }
                    if (showDistros) DistroScreen(onClose = { showDistros = false })
                }
                SessionPrompts(hasRunningSession = sessions.needsService)
            }
        }
    }
}
