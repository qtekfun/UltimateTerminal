// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.launch.LaunchMessage
import com.qtekfun.ultimateterminal.domain.launch.LaunchNotice
import com.qtekfun.ultimateterminal.domain.launch.LaunchProblem
import com.qtekfun.ultimateterminal.domain.storage.StorageDegradation

private val MIN_TOUCH = 48.dp

/**
 * Tells the user why the active tab did not open what they asked for, or what it opened without. A
 * problem stays until the tab goes; a notice can be dismissed. Both offer the distro screen, which is
 * where every one of them is fixed. Not validated on a device (see DECISIONS.md, T08b).
 */
@Composable
fun LaunchMessageBanner(
    message: LaunchMessage?,
    onOpenDistros: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (message == null) return
    var dismissed by remember(message) { mutableStateOf(false) }
    val isProblem = message is LaunchMessage.Problem
    if (dismissed && !isProblem) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isProblem) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = launchMessageText(message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            TextButton(onClick = onOpenDistros, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.launch_action_distros))
            }
            if (!isProblem) {
                TextButton(
                    onClick = { dismissed = true },
                    modifier = Modifier.heightIn(min = MIN_TOUCH)
                ) { Text(stringResource(R.string.launch_action_dismiss)) }
            }
        }
    }
}

@Composable
private fun launchMessageText(message: LaunchMessage): String = when (message) {
    is LaunchMessage.Problem -> problemText(message.problem)
    is LaunchMessage.Notice -> noticeText(message.notice)
}

@Composable
private fun problemText(problem: LaunchProblem): String = when (problem) {
    LaunchProblem.ProotMissing -> stringResource(R.string.launch_problem_proot_missing)

    is LaunchProblem.DistroCorrupt ->
        stringResource(R.string.launch_problem_distro_corrupt, problem.distroName)

    is LaunchProblem.InvalidUser ->
        stringResource(R.string.launch_problem_invalid_user, problem.user)

    LaunchProblem.TempDirUnavailable -> stringResource(R.string.launch_problem_tmp)

    LaunchProblem.Unexpected -> stringResource(R.string.launch_problem_unexpected)
}

@Composable
private fun noticeText(notice: LaunchNotice): String = when (notice) {
    is LaunchNotice.DistroUnavailable -> notice.distroName?.let {
        stringResource(R.string.launch_notice_distro_unavailable_named, it)
    } ?: stringResource(R.string.launch_notice_distro_unavailable)

    is LaunchNotice.StorageNotMounted -> stringResource(
        when (notice.reason) {
            StorageDegradation.PERMISSION_DENIED -> R.string.launch_notice_storage_permission
            StorageDegradation.STORAGE_UNAVAILABLE -> R.string.launch_notice_storage_unavailable
            StorageDegradation.MOUNT_POINT_FAILED -> R.string.launch_notice_storage_mount_point
        }
    )

    LaunchNotice.DnsNotConfigured -> stringResource(R.string.launch_notice_dns)
}
