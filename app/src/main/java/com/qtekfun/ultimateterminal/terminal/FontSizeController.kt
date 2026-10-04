// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.terminal.FontZoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The terminal font size as an observable value, changed by pinch or by shortcut. */
class FontSizeController(private val zoom: FontZoom = FontZoom()) {
    private val state = MutableStateFlow(zoom.sizeSp)

    private var restored = false

    val sizeSp: StateFlow<Float> = state.asStateFlow()

    /** A pinch changed the scale by [factor] since the last call. */
    fun pinch(factor: Float) {
        state.value = zoom.pinch(factor)
    }

    /** Puts back a stored size. Only the first call counts, so saving the size never undoes a later pinch. */
    fun restore(sizeSp: Float) {
        if (restored) return
        restored = true
        state.value = zoom.set(sizeSp)
    }

    fun zoomIn() {
        state.value = zoom.zoomIn()
    }

    fun zoomOut() {
        state.value = zoom.zoomOut()
    }

    fun reset() {
        state.value = zoom.reset()
    }
}
