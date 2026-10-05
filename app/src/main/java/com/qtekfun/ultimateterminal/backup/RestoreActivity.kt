// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.backup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a backup is being restored right now. The first-run setup reads it: a restore installs
 * its distros one by one and applies the configuration (and with it the default distro) last, so
 * the setup must not close, and a tab must not open, when the first distro lands.
 */
@Singleton
class RestoreActivity @Inject constructor() {
    private val running = MutableStateFlow(false)

    val active: StateFlow<Boolean> = running.asStateFlow()

    fun set(value: Boolean) {
        running.value = value
    }
}
