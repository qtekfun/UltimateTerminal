// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.content.Context
import android.os.SystemClock
import com.qtekfun.ultimateterminal.data.proot.FakeProcSource
import com.qtekfun.ultimateterminal.di.IoDispatcher
import com.qtekfun.ultimateterminal.domain.launch.FakeProc
import com.qtekfun.ultimateterminal.domain.launch.FakeProcInputs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Writes the fake `/proc` files of [FakeProc] into the app's private storage on every launch (the
 * uptime changes) and returns their paths. What goes in them is decided by [FakeProc]; this only
 * reads the device's clock and CPU count and writes, so it is checked on a device (DECISIONS.md,
 * T08c).
 */
class AndroidFakeProcSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher
) : FakeProcSource {
    override suspend fun hostFiles(): Map<String, String> = withContext(io) {
        val now = System.currentTimeMillis()
        val uptimeSeconds = SystemClock.elapsedRealtime() / MILLIS
        val inputs = FakeProcInputs(
            cpuCount = Runtime.getRuntime().availableProcessors(),
            uptimeSeconds = uptimeSeconds,
            kernelRelease = System.getProperty("os.version").orEmpty(),
            bootEpochSeconds = now / MILLIS - uptimeSeconds
        )
        val dir = File(context.filesDir, DIRECTORY)
        try {
            check(dir.isDirectory || dir.mkdirs()) { "Cannot create $dir" }
            FakeProc.render(inputs).mapValues { (name, content) ->
                File(dir, name).also { it.writeText(content) }.absolutePath
            }
        } catch (_: IOException) {
            emptyMap()
        } catch (_: IllegalStateException) {
            emptyMap()
        }
    }

    private companion object {
        const val DIRECTORY = "fake-proc"
        const val MILLIS = 1000L
    }
}
