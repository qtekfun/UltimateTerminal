// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.paneCount
import com.qtekfun.ultimateterminal.domain.profile.LayoutNotice
import com.qtekfun.ultimateterminal.domain.profile.LayoutRefusal
import com.qtekfun.ultimateterminal.domain.profile.RestoreResult
import com.qtekfun.ultimateterminal.profiles.LayoutsViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosBarButton
import com.qtekfun.ultimateterminal.ui.ios.IosLargeTitleScreen
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection

private sealed interface LayoutDialog {
    data class Actions(val layout: Layout) : LayoutDialog

    data class Delete(val layout: Layout) : LayoutDialog

    data object Save : LayoutDialog

    /** The layout opened, but not as saved: read what changed before the screen closes. */
    data class Changed(val notices: List<LayoutNotice>) : LayoutDialog

    /** The layout did not open. */
    data class Refused(val reason: LayoutRefusal) : LayoutDialog
}

/**
 * The saved layouts (SPEC RF-12): save the panes of the active tab, open one as a new tab, replace
 * it with the current panes or delete it. [onClose] leaves the screen, which opening a layout does
 * too, once the user has read what it changed.
 */
@Composable
fun LayoutsScreen(
    onClose: () -> Unit,
    onOpened: () -> Unit = onClose,
    viewModel: LayoutsViewModel = viewModel()
) {
    val layouts by viewModel.layouts.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LayoutDialog?>(null) }
    BackHandler(onBack = onClose)
    IosLargeTitleScreen(
        title = stringResource(R.string.layouts_title),
        leading = { IosBarButton(stringResource(R.string.layouts_close), onClose) }
    ) {
        item {
            IosSection(footer = stringResource(R.string.layouts_save_footer)) {
                IosListRow(
                    title = stringResource(R.string.layouts_save_current),
                    showSeparator = false,
                    onClick = { dialog = LayoutDialog.Save }
                )
            }
        }
        item { LayoutList(layouts) { dialog = LayoutDialog.Actions(it) } }
    }
    LayoutDialogs(
        dialog = dialog,
        viewModel = viewModel,
        onClose = onOpened,
        show = { dialog = it },
        // The action sheet closes itself after an action ran, and the action may have opened the
        // next dialog: only close what is still the sheet, reading the state as it is now.
        closeActions = { if (dialog is LayoutDialog.Actions) dialog = null }
    )
}

@Composable
private fun LayoutList(layouts: List<Layout>?, onTap: (Layout) -> Unit) {
    when {
        layouts == null -> Unit

        layouts.isEmpty() -> IosSection(footer = stringResource(R.string.layouts_empty)) {}

        else -> IosSection {
            layouts.forEachIndexed { index, layout ->
                val count = layout.root.paneCount()
                IosListRow(
                    title = layout.name,
                    subtitle = pluralStringResource(R.plurals.layouts_panes, count, count),
                    accessory = IosAccessory.Chevron,
                    showSeparator = index != layouts.lastIndex,
                    onClick = { onTap(layout) }
                )
            }
        }
    }
}

@Composable
private fun LayoutDialogs(
    dialog: LayoutDialog?,
    viewModel: LayoutsViewModel,
    onClose: () -> Unit,
    show: (LayoutDialog?) -> Unit,
    closeActions: () -> Unit
) {
    val dismiss = { show(null) }
    when (dialog) {
        null -> Unit

        is LayoutDialog.Actions ->
            LayoutActions(dialog.layout, viewModel, onClose, show, closeActions)

        LayoutDialog.Save -> SaveLayoutSheet(onDismiss = dismiss, onSaved = dismiss)

        is LayoutDialog.Delete -> IosAlert(
            title = stringResource(R.string.layouts_delete_title, dialog.layout.name),
            actions = listOf(
                IosAction(stringResource(R.string.dialog_cancel), IosActionRole.CANCEL),
                IosAction(stringResource(R.string.layouts_delete), IosActionRole.DESTRUCTIVE) {
                    viewModel.delete(dialog.layout.id)
                }
            ),
            onDismiss = dismiss
        )

        is LayoutDialog.Changed -> NoticeAlert(
            R.string.layouts_opened_title,
            dialog.notices.map { layoutNoticeText(it) }
        ) {
            dismiss()
            onClose()
        }

        is LayoutDialog.Refused -> NoticeAlert(
            R.string.layouts_title,
            listOf(
                stringResource(
                    if (dialog.reason == LayoutRefusal.TOO_MANY_PANES) {
                        R.string.layouts_refused_many
                    } else {
                        R.string.layouts_refused_deep
                    }
                )
            ),
            dismiss
        )
    }
}

@Composable
private fun NoticeAlert(title: Int, lines: List<String>, onDismiss: () -> Unit) {
    IosAlert(
        title = stringResource(title),
        message = lines.joinToString("\n"),
        actions = listOf(IosAction(stringResource(R.string.msg_dismiss), IosActionRole.CANCEL)),
        onDismiss = onDismiss
    )
}

@Composable
private fun LayoutActions(
    layout: Layout,
    viewModel: LayoutsViewModel,
    onClose: () -> Unit,
    show: (LayoutDialog?) -> Unit,
    closeActions: () -> Unit
) {
    val context = LocalContext.current
    val open = {
        viewModel.open(layout) { result ->
            when (result) {
                is RestoreResult.Opened -> if (result.notices.isEmpty()) {
                    show(null)
                    onClose()
                } else {
                    show(LayoutDialog.Changed(result.notices))
                }

                is RestoreResult.Refused -> show(LayoutDialog.Refused(result.reason))
            }
        }
    }
    val replace = {
        viewModel.save(layout.name, replace = true) { result ->
            if (result is Outcome.Success) {
                context.toast(R.string.layouts_saved, layout.name)
            } else {
                context.toast(R.string.layouts_error_failed)
            }
        }
    }
    IosActionSheet(
        actions = listOf(
            IosAction(stringResource(R.string.layouts_open)) { open() },
            IosAction(stringResource(R.string.layouts_replace)) { replace() },
            IosAction(stringResource(R.string.layouts_delete), IosActionRole.DESTRUCTIVE) {
                show(LayoutDialog.Delete(layout))
            }
        ),
        cancelLabel = stringResource(R.string.dialog_cancel),
        onDismiss = closeActions,
        title = layout.name
    )
}
