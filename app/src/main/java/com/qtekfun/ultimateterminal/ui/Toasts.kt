// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/** A short message for the result of something that has no screen of its own to show it. */
internal fun Context.toast(@StringRes message: Int, vararg args: Any) {
    Toast.makeText(this, getString(message, *args), Toast.LENGTH_SHORT).show()
}
