// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.content.Context
import android.net.ConnectivityManager
import com.qtekfun.ultimateterminal.data.proot.ResolvConfSource
import com.qtekfun.ultimateterminal.di.IoDispatcher
import com.qtekfun.ultimateterminal.domain.launch.ResolvConf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Writes `resolv.conf` from the DNS servers of the active network and returns its path, to be bound
 * over a distro's `/etc/resolv.conf`. Which servers go in is decided by [ResolvConf]; this only asks
 * Android and writes the file, so it is checked on a device (see DECISIONS.md, T08b).
 */
class AndroidResolvConfSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher
) : ResolvConfSource {
    override suspend fun hostFile(): String? = withContext(io) {
        val file = File(context.filesDir, FILE_NAME)
        try {
            file.writeText(ResolvConf.render(systemServers()))
            file.absolutePath
        } catch (_: IOException) {
            null
        }
    }

    // A network that vanishes, or a profile that may not read it, is not an error: the fallback
    // servers of ResolvConf apply then.
    @Suppress("TooGenericExceptionCaught")
    private fun systemServers(): List<String> = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager?.getLinkProperties(manager.activeNetwork)?.dnsServers.orEmpty()
            .mapNotNull { it.hostAddress }
    } catch (_: RuntimeException) {
        emptyList()
    }

    private companion object {
        const val FILE_NAME = "resolv.conf"
    }
}
