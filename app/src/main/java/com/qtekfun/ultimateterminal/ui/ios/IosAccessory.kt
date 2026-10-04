// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

/** What a list row shows at its trailing edge. */
sealed interface IosAccessory {
    data object None : IosAccessory

    data object Chevron : IosAccessory

    data object Check : IosAccessory

    data class Value(val text: String, val chevron: Boolean = false) : IosAccessory

    data class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : IosAccessory
}
