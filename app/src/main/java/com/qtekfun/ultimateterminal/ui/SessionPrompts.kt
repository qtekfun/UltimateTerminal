// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.session.PromptInputs
import com.qtekfun.ultimateterminal.domain.session.SessionPrompt
import com.qtekfun.ultimateterminal.domain.session.nextPrompt

/**
 * Asks, once and in context, for what keeps the shells alive in the background: the notification
 * permission (Android 13+) and, as advice only, to leave the battery optimisation. Nothing is
 * forced: both can be declined, and the answer is not asked again until the app is restarted.
 */
@Composable
fun SessionPrompts(hasRunningSession: Boolean) {
    val context = LocalContext.current
    var notificationsDeclined by rememberSaveable { mutableStateOf(false) }
    var batteryDeclined by rememberSaveable { mutableStateOf(false) }
    // The user may change these in the system settings and come back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) notificationsDeclined = true
        refresh++
    }

    val notificationsAllowed = remember(refresh) { notificationsGranted(context) }
    val batteryExempt = remember(refresh) { ignoringBatteryOptimizations(context) }
    val prompt = nextPrompt(
        PromptInputs(
            sdkInt = Build.VERSION.SDK_INT,
            hasRunningSession = hasRunningSession,
            notificationsGranted = notificationsAllowed,
            notificationsDeclined = notificationsDeclined,
            ignoringBatteryOptimizations = batteryExempt,
            batteryDeclined = batteryDeclined
        )
    )
    when (prompt) {
        SessionPrompt.None -> Unit

        SessionPrompt.Notifications -> PromptDialog(
            title = R.string.prompt_notifications_title,
            message = R.string.prompt_notifications_message,
            confirm = R.string.prompt_allow,
            onConfirm = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onDismiss = { notificationsDeclined = true }
        )

        SessionPrompt.BatteryOptimization -> PromptDialog(
            title = R.string.prompt_battery_title,
            message = R.string.prompt_battery_message,
            confirm = R.string.prompt_open_settings,
            onConfirm = {
                batteryDeclined = true
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
            onDismiss = { batteryDeclined = true }
        )
    }
}

@Composable
private fun PromptDialog(
    title: Int,
    message: Int,
    confirm: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.prompt_not_now)) }
        }
    )
}

private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun ignoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
