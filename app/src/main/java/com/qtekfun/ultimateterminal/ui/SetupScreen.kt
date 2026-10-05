// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.backup.BackupMessage
import com.qtekfun.ultimateterminal.backup.BackupViewModel
import com.qtekfun.ultimateterminal.distro.DistroViewModel
import com.qtekfun.ultimateterminal.domain.distro.DistroNames
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.setup.FirstRunSetup
import com.qtekfun.ultimateterminal.setup.SetupStep
import com.qtekfun.ultimateterminal.setup.setupStepOf
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosButton
import com.qtekfun.ultimateterminal.ui.ios.IosButtonStyle
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosLargeTitleScreen
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSpacing
import com.qtekfun.ultimateterminal.ui.ios.IosText
import com.qtekfun.ultimateterminal.ui.ios.IosTextField
import com.qtekfun.ultimateterminal.ui.ios.IosTheme
import com.qtekfun.ultimateterminal.ui.settings.Working

/**
 * The first-run screen (SPEC RF-15): shown instead of the Android shell while there is no distro.
 * It drives the same [DistroViewModel] and [BackupViewModel] as the Distributions and Backups
 * screens, so installing and restoring work exactly as there; the caller closes it when a distro
 * is ready or [onSkip] is chosen.
 */
@Composable
fun SetupScreen(
    onSkip: () -> Unit,
    distros: DistroViewModel = viewModel(),
    backups: BackupViewModel = viewModel()
) {
    val state by distros.uiState.collectAsStateWithLifecycle()
    val backup by backups.uiState.collectAsStateWithLifecycle()
    var family by rememberSaveable { mutableStateOf(FirstRunSetup.recommended) }
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var user by rememberSaveable { mutableStateOf(NewDistro.DEFAULT_USER) }
    val shownName = name ?: DistroNames.suggest(
        stringResource(family.nameRes()),
        state.distros.map { it.name }
    )
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(backups::startRestore)
    }
    val step = setupStepOf(state)
    val busy = step is SetupStep.Installing || backup.busy
    IosLargeTitleScreen(title = stringResource(R.string.setup_title)) {
        item { Intro() }
        (step as? SetupStep.Installing)?.let { installing ->
            item { InstallProgress(installing.install, distros::cancelInstall) }
        }
        if (backup.busy) item { IosSection { Working(backup, backups::cancel) } }
        state.message?.let { item { MessageRow(it, distros::dismissMessage) } }
        backup.message?.let { item { BackupMessageRow(it, backups::dismissMessage) } }
        if (step is SetupStep.Choose) {
            item { FamilySection(family) { family = it } }
            item {
                DetailsSection(
                    name = shownName,
                    user = user,
                    onName = { name = it },
                    onUser = { user = it }
                )
            }
        }
        item {
            val ready = state.ready && !busy
            Actions(
                SetupActions(
                    canInstall = FirstRunSetup.canInstall(shownName, user) && ready,
                    canLeave = !busy,
                    retry = (step as? SetupStep.Choose)?.lastError != null,
                    onInstall = { distros.install(family, shownName, user) },
                    onRestore = { open.launch(arrayOf("*/*")) },
                    onSkip = onSkip
                )
            )
        }
    }
    backup.passwordFor?.let { file ->
        PasswordSheet(
            onConfirm = { password -> backups.restoreEncrypted(file, password) },
            onDismiss = backups::cancelPassword
        )
    }
}

@Composable
private fun Intro() {
    IosText(
        text = stringResource(R.string.setup_intro),
        modifier = Modifier.padding(horizontal = IosSpacing.md),
        style = IosTheme.typography.body,
        color = IosTheme.colors.secondaryLabel
    )
}

@Composable
private fun FamilySection(selected: DistroFamily, onSelect: (DistroFamily) -> Unit) {
    IosSection(
        header = stringResource(R.string.install_distro_header),
        footer = stringResource(R.string.setup_network_note)
    ) {
        FirstRunSetup.families.forEachIndexed { index, option ->
            IosListRow(
                title = stringResource(option.nameRes()),
                subtitle = stringResource(option.setupNoteRes()),
                accessory = if (option == selected) IosAccessory.Check else IosAccessory.None,
                showSeparator = index != FirstRunSetup.families.lastIndex,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun DetailsSection(
    name: String,
    user: String,
    onName: (String) -> Unit,
    onUser: (String) -> Unit
) {
    IosSection(header = stringResource(R.string.install_details_header)) {
        IosTextField(
            value = name,
            onValueChange = onName,
            label = stringResource(R.string.install_name_label)
        )
        IosTextField(
            value = user,
            onValueChange = onUser,
            label = stringResource(R.string.install_user_label),
            showSeparator = false
        )
    }
}

/** What the buttons under the form do, and which of them can be pressed now. */
private data class SetupActions(
    val canInstall: Boolean,
    val canLeave: Boolean,
    val retry: Boolean,
    val onInstall: () -> Unit,
    val onRestore: () -> Unit,
    val onSkip: () -> Unit
)

@Composable
private fun BackupMessageRow(message: BackupMessage, onDismiss: () -> Unit) {
    IosSection {
        IosListRow(
            title = backupMessageText(message),
            glyph = IosGlyph.INFO,
            accessory = IosAccessory.Value(stringResource(R.string.msg_dismiss)),
            showSeparator = false,
            onClick = onDismiss
        )
    }
}

@Composable
private fun Actions(actions: SetupActions) {
    IosSection {
        IosButton(
            text = stringResource(
                if (actions.retry) R.string.setup_retry else R.string.install_confirm
            ),
            onClick = actions.onInstall,
            enabled = actions.canInstall
        )
        IosButton(
            text = stringResource(R.string.setup_restore),
            onClick = actions.onRestore,
            style = IosButtonStyle.TINTED,
            enabled = actions.canLeave,
            modifier = Modifier.padding(top = IosSpacing.sm)
        )
        IosButton(
            text = stringResource(R.string.setup_skip),
            onClick = actions.onSkip,
            style = IosButtonStyle.PLAIN,
            enabled = actions.canLeave,
            modifier = Modifier.padding(top = IosSpacing.sm)
        )
    }
}
