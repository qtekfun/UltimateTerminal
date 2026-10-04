// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.terminal.SessionManager
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
                // "Exit" in the notification closes every session: close the screen with them.
                LaunchedEffect(sessions.items.isEmpty()) {
                    if (sessions.items.isNotEmpty()) {
                        hadSessions = true
                    } else if (hadSessions) {
                        finishAndRemoveTask()
                    }
                }
                TerminalScreen()
                SessionPrompts(hasRunningSession = sessions.needsService)
            }
        }
    }
}
