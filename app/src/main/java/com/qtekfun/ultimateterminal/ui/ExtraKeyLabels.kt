// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.annotation.StringRes
import com.qtekfun.ultimateterminal.R

/** The spoken name of an extra key, for accessibility; null for an id with no name. */
@StringRes
@Suppress("CyclomaticComplexMethod")
internal fun extraKeyDescription(id: String): Int? = when (id) {
    "esc" -> R.string.extra_key_esc
    "tab" -> R.string.extra_key_tab
    "ctrl" -> R.string.extra_key_ctrl
    "alt" -> R.string.extra_key_alt
    "up" -> R.string.extra_key_up
    "down" -> R.string.extra_key_down
    "left" -> R.string.extra_key_left
    "right" -> R.string.extra_key_right
    "home" -> R.string.extra_key_home
    "end" -> R.string.extra_key_end
    "pgup" -> R.string.extra_key_pgup
    "pgdn" -> R.string.extra_key_pgdn
    "ins" -> R.string.extra_key_ins
    "del" -> R.string.extra_key_del
    "slash" -> R.string.extra_key_slash
    "dash" -> R.string.extra_key_dash
    "pipe" -> R.string.extra_key_pipe
    "tilde" -> R.string.extra_key_tilde
    "backslash" -> R.string.extra_key_backslash
    else -> null
}
