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

    /**
     * Whether every permission of a dialog result was granted. An empty result means the request
     * was interrupted, not that all of nothing was granted: `all` on an empty map is `true`, which
     * would turn the feature on without any permission.
     */
    fun allGranted(results: Map<String, Boolean>): Boolean =
        results.isNotEmpty() && results.values.all { it }

    /**
     * What the switch shows. The feature needs the permission, so a stored "on" without it is shown
     * as off: the switch never claims something that would not work.
     */
    fun isShownOn(enabled: Boolean, permissionGranted: Boolean): Boolean =
        enabled && permissionGranted
}
