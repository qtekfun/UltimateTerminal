// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.profile.LayoutSaveProblem
import com.qtekfun.ultimateterminal.domain.profile.asLayoutSaveProblem
import com.qtekfun.ultimateterminal.profiles.LayoutsViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosTextField

/**
 * Asks for a name and saves the panes of the active tab under it (SPEC RF-12). A name that is in use
 * asks before replacing the layout that has it. [onSaved] runs once it is stored.
 */
@Composable
fun SaveLayoutSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: LayoutsViewModel = viewModel()
) {
    var name by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<LayoutSaveProblem?>(null) }
    var askReplace by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val save = { replace: Boolean ->
        viewModel.save(name, replace) { result ->
            when (result) {
                is Outcome.Success -> {
                    context.toast(R.string.layouts_saved, result.value.name)
                    onSaved()
                }

                is Outcome.Failure -> {
                    val reason = result.error.asLayoutSaveProblem()
                    askReplace = reason == LayoutSaveProblem.NAME_TAKEN
                    problem = reason.takeUnless { askReplace }
                }
            }
        }
    }
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column(Modifier.imePadding()) {
            IosSheetHeader(
                title = stringResource(R.string.layouts_save_title),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.layouts_save),
                onCancel = onDismiss,
                onConfirm = { save(false) },
                confirmEnabled = name.isNotBlank()
            )
            IosSection(footer = problem?.let { stringResource(messageOf(it)) }) {
                IosTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        problem = null
                    },
                    label = stringResource(R.string.layouts_field_name),
                    isError = problem != null,
                    showSeparator = false
                )
            }
        }
    }
    if (askReplace) {
        ReplaceAlert(name.trim(), onConfirm = { save(true) }) { askReplace = false }
    }
}

/** The name is in use: replacing the layout that has it needs the user's word. */
@Composable
private fun ReplaceAlert(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    IosAlert(
        title = stringResource(R.string.layouts_replace_title, name),
        message = stringResource(R.string.layouts_replace_message),
        actions = listOf(
            IosAction(stringResource(R.string.dialog_cancel), IosActionRole.CANCEL),
            IosAction(
                stringResource(R.string.layouts_replace_confirm),
                IosActionRole.DESTRUCTIVE,
                onConfirm
            )
        ),
        onDismiss = onDismiss
    )
}

@StringRes
private fun messageOf(problem: LayoutSaveProblem): Int = when (problem) {
    LayoutSaveProblem.NAME, LayoutSaveProblem.NAME_TAKEN -> R.string.layouts_error_name
    LayoutSaveProblem.NO_TAB -> R.string.layouts_error_no_tab
    LayoutSaveProblem.COMMAND -> R.string.layouts_error_command
    LayoutSaveProblem.LIMIT -> R.string.layouts_error_limit
    LayoutSaveProblem.OTHER -> R.string.layouts_error_failed
}
