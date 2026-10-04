// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import android.app.Application
import com.qtekfun.ultimateterminal.terminal.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UltimateTerminalApp : Application() {
    /** The terminal sessions live as long as the process; the screens reconnect to them. */
    @Inject lateinit var sessionManager: SessionManager
}
