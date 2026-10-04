// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/** A system settings screen to open: its intent action and, optionally, its data URI. */
data class SettingsIntent(val action: String, val dataUri: String? = null)

/**
 * How to ask for the battery-optimisation exemption (T08c). The first choice is the system dialog
 * "Allow the app to always run in the background?", which is the screen the user wants to see;
 * if a device does not offer it, the general list of exemptions is the way out. The strings are
 * the values of `Settings.ACTION_*`, kept here so the order and the data can be tested on the host.
 */
object BatteryExemption {
    const val ACTION_REQUEST = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
    const val ACTION_LIST = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"

    /** The screens to try, in order, until one opens. */
    fun intentsFor(packageName: String): List<SettingsIntent> {
        require(packageName.isNotBlank()) { "The package name must not be blank" }
        return listOf(
            SettingsIntent(ACTION_REQUEST, "package:$packageName"),
            SettingsIntent(ACTION_LIST)
        )
    }
}
