// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/** How a sticky modifier key (Ctrl, Alt on the extra-keys row) is currently held. */
enum class LatchState {
    OFF,

    /** Applies to the next key only, then goes back to [OFF]. */
    ARMED,

    /** Applies to every key until tapped again. */
    LOCKED
}

enum class StickyKey { CTRL, ALT }

/**
 * Ctrl and Alt as they behave on a touch keyboard, where they cannot be held down while another
 * key is pressed. Tapping a key cycles it: off, armed for one key, locked, off. So a single tap
 * is one press and a second tap (while still armed) locks it. Immutable: every operation returns
 * the new state.
 */
data class StickyState(
    val ctrl: LatchState = LatchState.OFF,
    val alt: LatchState = LatchState.OFF
) {
    val ctrlActive: Boolean get() = ctrl != LatchState.OFF
    val altActive: Boolean get() = alt != LatchState.OFF
    val anyActive: Boolean get() = ctrlActive || altActive

    operator fun get(key: StickyKey): LatchState = when (key) {
        StickyKey.CTRL -> ctrl
        StickyKey.ALT -> alt
    }

    fun tap(key: StickyKey): StickyState {
        val next = when (get(key)) {
            LatchState.OFF -> LatchState.ARMED
            LatchState.ARMED -> LatchState.LOCKED
            LatchState.LOCKED -> LatchState.OFF
        }
        return when (key) {
            StickyKey.CTRL -> copy(ctrl = next)
            StickyKey.ALT -> copy(alt = next)
        }
    }

    /** The state after a key has been sent: armed modifiers are spent, locked ones stay. */
    fun afterKey(): StickyState = StickyState(
        ctrl = if (ctrl == LatchState.ARMED) LatchState.OFF else ctrl,
        alt = if (alt == LatchState.ARMED) LatchState.OFF else alt
    )
}
