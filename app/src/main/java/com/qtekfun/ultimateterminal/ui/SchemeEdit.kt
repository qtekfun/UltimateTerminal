// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme

/** A scheme being created or edited; [previousId] is null for a new one. */
internal data class SchemeEdit(val previousId: String?, val scheme: TerminalColorScheme)
