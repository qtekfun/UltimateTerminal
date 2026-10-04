// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.qtekfun.ultimateterminal.domain.session.ServiceControl

/** Starts and stops [SessionService], the foreground service that keeps the shells alive. */
class ServiceLauncher(private val context: Context) : ServiceControl {
    override fun setRunning(wanted: Boolean) {
        val intent = Intent(context, SessionService::class.java)
        if (wanted) {
            ContextCompat.startForegroundService(
                context,
                intent
            )
        } else {
            context.stopService(intent)
        }
    }
}
