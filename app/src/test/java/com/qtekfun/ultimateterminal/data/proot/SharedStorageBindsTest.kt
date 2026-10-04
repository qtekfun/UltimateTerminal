// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.storage.StorageBind
import com.qtekfun.ultimateterminal.domain.storage.StorageDegradation
import com.qtekfun.ultimateterminal.domain.storage.StorageMountPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SharedStorageBindsTest {
    private val builder = ProotCommandBuilder("/native", "/tmp-private")
    private val session = ProotSession(rootfs = "/data/rootfs")

    @Test
    fun `an active plan adds its binds after the default ones`() {
        val plan = StorageMountPlan.Active(
            listOf(StorageBind("/storage/emulated/0/Download", "/root/storage/downloads"))
        )

        val command = builder.build(session.withSharedStorage(plan)).command

        val binds = command.windowed(2).filter { it[0] == "-b" }.map { it[1] }
        assertEquals(
            listOf(
                "/dev:/dev",
                "/proc:/proc",
                "/sys:/sys",
                "/storage/emulated/0/Download:/root/storage/downloads"
            ),
            binds
        )
    }

    @Test
    fun `a disabled or degraded plan leaves the session as it was`() {
        assertEquals(session, session.withSharedStorage(StorageMountPlan.Disabled))
        assertEquals(
            session,
            session.withSharedStorage(
                StorageMountPlan.Degraded(StorageDegradation.PERMISSION_DENIED)
            )
        )
    }
}
