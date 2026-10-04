// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

/** A process to start: the full command line and the environment of the host process. */
data class ProotLaunch(val command: List<String>, val environment: Map<String, String>)
