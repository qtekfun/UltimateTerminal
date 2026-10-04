// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.content.Context
import com.qtekfun.ultimateterminal.data.proot.ProotCommandBuilder
import com.qtekfun.ultimateterminal.data.proot.ProotRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Finds proot where Android extracted it. Device glue with no logic of its own, so it is checked on
 * a device (see DECISIONS.md, T08b). The binaries are real files in `nativeLibraryDir` because the
 * manifest sets `android:extractNativeLibs="true"` and Gradle packs them uncompressed-legacy
 * (`useLegacyPackaging`); with `targetSdk` 28 the app may execute them from there.
 */
class AndroidProotRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ProotRuntime {
    override val nativeLibraryDir: String get() = context.applicationInfo.nativeLibraryDir

    override fun hasBinaries(): Boolean =
        listOf(ProotCommandBuilder.PROOT_BINARY, ProotCommandBuilder.LOADER_BINARY).all { name ->
            File(nativeLibraryDir, name).let { it.isFile && it.canExecute() }
        }

    override fun prepareTmpDir(): String? {
        val dir = File(context.cacheDir, TMP_DIR_NAME)
        return dir.takeIf { (it.isDirectory || it.mkdirs()) && it.canWrite() }?.absolutePath
    }

    private companion object {
        const val TMP_DIR_NAME = "proot-tmp"
    }
}
