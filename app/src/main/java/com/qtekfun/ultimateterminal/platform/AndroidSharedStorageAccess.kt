// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Asks Android about the shared storage. This is device glue with no logic of its own (the
 * decisions are in `domain.storage`), so it is not measured by the coverage rules and is
 * checked on a device (see DECISIONS.md, T13).
 *
 * The app targets API 28 on purpose (SPEC §2), where the classic READ/WRITE_EXTERNAL_STORAGE
 * permissions are the model; the "all files access" of Android 11 is accepted too if the user
 * granted it in the system settings, but it is never requested.
 */
class AndroidSharedStorageAccess @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SharedStorageAccess {
    override fun isPermissionGranted(): Boolean {
        val classic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val allFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        return classic || allFiles
    }

    // The path-based API is the point of targeting API 28: native processes (proot) need a path.
    @Suppress("DEPRECATION")
    override fun sharedRoot(): String? =
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            null
        }

    override fun directoryExists(absolutePath: String): Boolean = File(absolutePath).isDirectory
}
