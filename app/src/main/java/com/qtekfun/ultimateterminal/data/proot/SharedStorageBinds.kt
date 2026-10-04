// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.storage.StorageMountPlan

/**
 * The same session with the shared storage bound in, when the plan has it. A disabled or degraded
 * plan returns the session unchanged, so the shell always starts.
 */
fun ProotSession.withSharedStorage(plan: StorageMountPlan): ProotSession = when (plan) {
    is StorageMountPlan.Active ->
        copy(binds = binds + plan.binds.map { ProotBind(it.hostPath, it.guestPath) })

    is StorageMountPlan.Degraded, StorageMountPlan.Disabled -> this
}
