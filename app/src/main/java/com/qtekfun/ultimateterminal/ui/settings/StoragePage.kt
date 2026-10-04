// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.storage.StorageToggle
import com.qtekfun.ultimateterminal.storage.StorageAccessViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosListRow

/**
 * The switch that mounts the device's shared storage in `~/storage` (SPEC RF-05). The permission
 * is requested only when the user turns it on, never before.
 */
@Composable
internal fun StoragePage(nav: PageNav, viewModel: StorageAccessViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> viewModel.onPermissionResult(StorageToggle.allGranted(results)) }
    // The permission may have been changed in the system settings while the app was away.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose { }
    }
    val footer = if (state.enabled && !state.permissionGranted) {
        stringResource(R.string.storage_permission_missing)
    } else {
        stringResource(R.string.storage_explanation)
    }
    SettingsPage(stringResource(R.string.settings_section_storage), nav.backLabel, nav.back) {
        section(footer = footer) {
            IosListRow(
                title = stringResource(R.string.storage_switch_label),
                accessory = IosAccessory.Toggle(
                    StorageToggle.isShownOn(state.enabled, state.permissionGranted)
                ) { turnOn ->
                    if (viewModel.onToggled(turnOn)) {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    }
                },
                showSeparator = false
            )
        }
        section {
            IosListRow(
                title = stringResource(R.string.storage_open_settings),
                accessory = IosAccessory.Chevron,
                showSeparator = false,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                }
            )
        }
    }
}
