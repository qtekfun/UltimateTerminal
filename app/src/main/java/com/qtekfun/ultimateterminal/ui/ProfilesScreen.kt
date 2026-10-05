// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.profile.OpenAs
import com.qtekfun.ultimateterminal.domain.profile.OpenResult
import com.qtekfun.ultimateterminal.domain.profile.PaneNotice
import com.qtekfun.ultimateterminal.domain.profile.ProfileProblem
import com.qtekfun.ultimateterminal.profiles.ProfilesUiState
import com.qtekfun.ultimateterminal.profiles.ProfilesViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosBarButton
import com.qtekfun.ultimateterminal.ui.ios.IosBarIconButton
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosLargeTitleScreen
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection

/** What the profiles screen has open over itself. */
private sealed interface ProfileDialog {
    data class Actions(val profile: Profile) : ProfileDialog

    /** [profile] is null for a new one; [nameTaken] is the name the repository refused. */
    data class Editor(val profile: Profile?, val nameTaken: String? = null) : ProfileDialog

    data class Delete(val profile: Profile) : ProfileDialog

    /** The profile opened, but something had to change: read it before the screen closes. */
    data class Changed(val notices: List<PaneNotice>) : ProfileDialog

    /** The profile cannot open. */
    data class Refused(val problem: ProfileProblem) : ProfileDialog

    /** A split was asked for and there is no tab to split. */
    data object NothingToSplit : ProfileDialog
}

/**
 * The profiles screen (SPEC RF-12): list, create, edit and delete; tapping one offers to open it in a
 * new tab or in a split of the active tab. [onClose] leaves the screen; [onOpened] is what opening a
 * profile does once it has opened (D-FIX-1): the caller closes whatever is under it too.
 */
@Composable
fun ProfilesScreen(
    onClose: () -> Unit,
    onOpened: () -> Unit = onClose,
    viewModel: ProfilesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    BackHandler(onBack = onClose)
    IosLargeTitleScreen(
        title = stringResource(R.string.profiles_title),
        leading = { IosBarButton(stringResource(R.string.profiles_close), onClose) },
        trailing = {
            IosBarIconButton(
                IosGlyph.PLUS,
                stringResource(R.string.profiles_add),
                { dialog = ProfileDialog.Editor(null) }
            )
        }
    ) {
        item { ProfileList(state) { dialog = ProfileDialog.Actions(it) } }
    }
    val control = DialogControl(
        onClose = onOpened,
        show = { dialog = it },
        // The action sheet closes itself after an action ran, and the action may have opened the
        // next dialog: only close what is still the sheet, reading the state as it is now.
        closeActions = { if (dialog is ProfileDialog.Actions) dialog = null }
    )
    ProfileDialogs(dialog, state, viewModel, control)
}

/** How the dialogs of the profiles screen move on: show another, close the sheet, leave. */
private class DialogControl(
    val onClose: () -> Unit,
    val show: (ProfileDialog?) -> Unit,
    val closeActions: () -> Unit
)

@Composable
private fun ProfileList(state: ProfilesUiState, onTap: (Profile) -> Unit) {
    if (state.loaded && state.profiles.isEmpty()) {
        IosSection(footer = stringResource(R.string.profiles_empty)) {}
    } else {
        IosSection {
            state.profiles.forEachIndexed { index, profile ->
                IosListRow(
                    title = profile.name,
                    subtitle = summaryOf(profile, state.distros),
                    accessory = IosAccessory.Chevron,
                    showSeparator = index != state.profiles.lastIndex,
                    onClick = { onTap(profile) }
                )
            }
        }
    }
}

/** "Debian · User dev · Runs tmux": where the profile opens and what it does. */
@Composable
private fun summaryOf(profile: Profile, distros: List<Distro>): String {
    val distro = distros.firstOrNull { it.id == profile.distroId }?.name
        ?: stringResource(R.string.profiles_default_distro)
    return listOfNotNull(
        distro,
        profile.user?.let { stringResource(R.string.profiles_summary_user, it) },
        profile.startupCommand?.let { stringResource(R.string.profiles_summary_command, it) }
    ).joinToString(" · ")
}

@Composable
private fun ProfileDialogs(
    dialog: ProfileDialog?,
    state: ProfilesUiState,
    viewModel: ProfilesViewModel,
    control: DialogControl
) {
    val show = control.show
    val onClose = control.onClose
    when (dialog) {
        null -> Unit

        is ProfileDialog.Actions -> ProfileActions(dialog.profile, viewModel, control)

        is ProfileDialog.Editor -> ProfileEditorSheet(
            profile = dialog.profile,
            distros = state.distros,
            nameTaken = dialog.nameTaken,
            onSave = { saved ->
                viewModel.save(saved) { result ->
                    val error = (result as? Outcome.Failure)?.error
                    val taken = (error as? DomainError.NameTaken)?.name
                    show(
                        if (result is Outcome.Failure) {
                            ProfileDialog.Editor(
                                dialog.profile,
                                taken
                            )
                        } else {
                            null
                        }
                    )
                }
            },
            onDismiss = { show(null) }
        )

        is ProfileDialog.Delete -> IosAlert(
            title = stringResource(R.string.profiles_delete_title, dialog.profile.name),
            message = stringResource(R.string.profiles_delete_message),
            actions = listOf(
                IosAction(stringResource(R.string.dialog_cancel), IosActionRole.CANCEL),
                IosAction(stringResource(R.string.profiles_delete), IosActionRole.DESTRUCTIVE) {
                    viewModel.delete(dialog.profile.id)
                }
            ),
            onDismiss = { show(null) }
        )

        is ProfileDialog.Changed -> NoticeAlert(
            title = R.string.profiles_opened_title,
            lines = dialog.notices.map { noticeText(it) }
        ) {
            show(null)
            onClose()
        }

        is ProfileDialog.Refused -> NoticeAlert(
            title = R.string.profiles_rejected_title,
            lines = listOf(problemText(dialog.problem))
        ) { show(null) }

        ProfileDialog.NothingToSplit -> NoticeAlert(
            title = R.string.profiles_rejected_title,
            lines = listOf(stringResource(R.string.profiles_nothing_to_split))
        ) { show(null) }
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
private fun ProfileActions(profile: Profile, viewModel: ProfilesViewModel, control: DialogControl) {
    val show = control.show
    val onClose = control.onClose
    val open = { how: OpenAs ->
        viewModel.open(profile, how) { result ->
            when (result) {
                is OpenResult.Opened -> if (result.notices.isEmpty()) {
                    show(null)
                    onClose()
                } else {
                    show(ProfileDialog.Changed(result.notices))
                }

                is OpenResult.Rejected -> show(ProfileDialog.Refused(result.problem))

                OpenResult.NothingToSplit -> show(ProfileDialog.NothingToSplit)
            }
        }
    }
    IosActionSheet(
        actions = listOf(
            IosAction(stringResource(R.string.profiles_open_tab)) { open(OpenAs.NEW_TAB) },
            IosAction(stringResource(R.string.profiles_open_split_right)) {
                open(OpenAs.SPLIT_RIGHT)
            },
            IosAction(stringResource(R.string.profiles_open_split_down)) {
                open(OpenAs.SPLIT_DOWN)
            },
            IosAction(stringResource(R.string.profiles_edit)) {
                show(ProfileDialog.Editor(profile))
            },
            IosAction(stringResource(R.string.profiles_delete), IosActionRole.DESTRUCTIVE) {
                show(ProfileDialog.Delete(profile))
            }
        ),
        cancelLabel = stringResource(R.string.dialog_cancel),
        onDismiss = control.closeActions,
        title = profile.name
    )
}
