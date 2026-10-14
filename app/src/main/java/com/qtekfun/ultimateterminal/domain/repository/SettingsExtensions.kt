// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.repository

/** Saves the terminal font size the user settled on (a pinch or the appearance screen). */
suspend fun SettingsRepository.saveFontSize(size: Float) {
    update { it.copy(terminalFontSizeSp = size) }
}
