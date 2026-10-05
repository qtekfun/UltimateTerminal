// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.distro.DistroNames
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.launch.GuestUser
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosTextField

/** Which distribution to install, what to call it and the user it opens as. */
@Composable
internal fun InstallSheet(
    existingNames: List<String>,
    onInstall: (DistroFamily, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var family by rememberSaveable { mutableStateOf(DistroFamily.DEBIAN) }
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var user by rememberSaveable { mutableStateOf(NewDistro.DEFAULT_USER) }
    val familyName = stringResource(family.nameRes())
    // Follows the chosen family until the user types a name of their own.
    val shownName = name ?: DistroNames.suggest(familyName, existingNames)
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(R.string.install_title),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.install_confirm),
                onCancel = onDismiss,
                onConfirm = { onInstall(family, shownName, user) },
                confirmEnabled = shownName.isNotBlank() && user.isNotBlank()
            )
            IosSection(header = stringResource(R.string.install_distro_header)) {
                DistroFamily.entries.forEachIndexed { index, option ->
                    IosListRow(
                        title = stringResource(option.nameRes()),
                        accessory = if (option == family) IosAccessory.Check else IosAccessory.None,
                        showSeparator = index != DistroFamily.entries.lastIndex,
                        onClick = { family = option }
                    )
                }
            }
            IosSection(header = stringResource(R.string.install_details_header)) {
                IosTextField(
                    value = shownName,
                    onValueChange = { name = it },
                    label = stringResource(R.string.install_name_label)
                )
                IosTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = stringResource(R.string.install_user_label),
                    showSeparator = false
                )
            }
        }
    }
}

/** A sheet with one text field, used to rename and to name a copy. */
@Composable
internal fun NameSheet(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = title,
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.dialog_ok),
                onCancel = onDismiss,
                onConfirm = { onConfirm(text) },
                confirmEnabled = text.isNotBlank()
            )
            IosSection {
                IosTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.install_name_label),
                    showSeparator = false
                )
            }
        }
    }
}

/** A sheet to change the user a distro opens as; a missing user is created at the next session. */
@Composable
internal fun UserSheet(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val valid = GuestUser.isValid(text.trim())
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(R.string.distro_change_user_title),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.dialog_ok),
                onCancel = onDismiss,
                onConfirm = { onConfirm(text) },
                confirmEnabled = valid
            )
            IosSection(
                footer = stringResource(
                    if (text.isBlank() || valid) {
                        R.string.distro_change_user_note
                    } else {
                        R.string.distro_change_user_invalid
                    }
                )
            ) {
                IosTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.install_user_label),
                    showSeparator = false
                )
            }
        }
    }
}
