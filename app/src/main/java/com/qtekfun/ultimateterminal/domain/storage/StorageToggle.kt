// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.storage

/** What the switch for the shared storage must do when the user flips it. */
enum class StorageToggleAction {
    /** Store the setting as on. */
    ENABLE,

    /** Store the setting as off. */
    DISABLE,

    /** Ask for the storage permission first; the setting changes only if it is granted. */
    REQUEST_PERMISSION
}

object StorageToggle {
    /** The permission is requested only when the user turns the feature on without it (SPEC RF-05). */
    fun onToggled(turnOn: Boolean, permissionGranted: Boolean): StorageToggleAction = when {
        !turnOn -> StorageToggleAction.DISABLE
        permissionGranted -> StorageToggleAction.ENABLE
        else -> StorageToggleAction.REQUEST_PERMISSION
    }

    /** The user answered the permission dialog: the feature turns on only if it was granted. */
    fun onPermissionResult(granted: Boolean): StorageToggleAction =
        if (granted) StorageToggleAction.ENABLE else StorageToggleAction.DISABLE
}
