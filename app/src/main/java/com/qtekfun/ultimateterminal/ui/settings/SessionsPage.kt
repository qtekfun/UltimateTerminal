// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.settings.SettingsViewModel
import com.qtekfun.ultimateterminal.ui.ignoringBatteryOptimizations
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.notificationsGranted
import com.qtekfun.ultimateterminal.ui.requestBatteryExemption

/** Keeping the shells alive: the wake lock, and what Android must allow for it. */
@Composable
internal fun SessionsPage(settings: AppSettings, viewModel: SettingsViewModel, nav: PageNav) {
    val context = LocalContext.current
    // The user may change these in the system settings and come back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }
    val notifications = remember(refresh) { notificationsGranted(context) }
    val unrestricted = remember(refresh) { ignoringBatteryOptimizations(context) }
    val allowed = stringResource(R.string.settings_allowed)
    val denied = stringResource(R.string.settings_not_allowed)
    val awakeFooter = stringResource(R.string.settings_keep_awake_footer)
    val backgroundHeader = stringResource(R.string.settings_background_header)
    val backgroundFooter = stringResource(R.string.settings_background_footer)
    SettingsPage(stringResource(R.string.settings_section_sessions), nav.backLabel, nav.back) {
        section(footer = awakeFooter) {
            IosListRow(
                title = stringResource(R.string.settings_keep_awake),
                accessory = IosAccessory.Toggle(settings.keepAwake, viewModel::setKeepAwake),
                showSeparator = false
            )
        }
        section(header = backgroundHeader, footer = backgroundFooter) {
            NotificationsRow(notifications, allowed, denied, launcher)
            BatteryRow(unrestricted, allowed, denied)
        }
    }
}

@Composable
private fun NotificationsRow(
    granted: Boolean,
    allowed: String,
    denied: String,
    launcher: ManagedActivityResultLauncher<String, Boolean>
) {
    val context = LocalContext.current
    // Before Android 13 notifications need no permission, so there is nothing to ask for.
    val asks = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    IosListRow(
        title = stringResource(R.string.settings_notifications),
        accessory = IosAccessory.Value(if (granted) allowed else denied, chevron = asks),
        onClick = if (asks) {
            {
                if (granted) {
                    openNotificationSettings(context)
                } else {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            null
        }
    )
}

@Composable
private fun BatteryRow(unrestricted: Boolean, allowed: String, denied: String) {
    val context = LocalContext.current
    IosListRow(
        title = stringResource(R.string.settings_battery),
        accessory = IosAccessory.Value(if (unrestricted) allowed else denied, chevron = true),
        showSeparator = false,
        onClick = {
            if (unrestricted) openBatterySettings(context) else requestBatteryExemption(context)
        }
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    startSafely(context, intent)
}

private fun openBatterySettings(context: Context) {
    startSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

// A device may lack the screen: then nothing opens, which is better than a crash.
private fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // This device has no such screen.
    }
}
