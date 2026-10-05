// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.app.ActivityManager
import android.content.Context

/**
 * Finishes and removes every task of the app, also those of an activity that is stopped or in
 * recents, so a stale screen never comes back empty (D-NOTIF-1). It does not end the process: with
 * the service stopped and no activity left Android reclaims it, and killing it by hand would skip
 * the normal lifecycle callbacks.
 */
class AppTaskFinisher(private val context: Context) {
    fun finishAll() {
        context.getSystemService(ActivityManager::class.java)
            ?.appTasks
            ?.forEach { it.finishAndRemoveTask() }
    }
}
