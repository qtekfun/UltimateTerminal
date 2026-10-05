// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.profile.ProfileDraft
import com.qtekfun.ultimateterminal.domain.profile.ProfileField
import com.qtekfun.ultimateterminal.domain.profile.ProfileForm
import com.qtekfun.ultimateterminal.domain.profile.ProfileFormResult
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosTextField

/** A [ProfileDraft] that survives rotation and the process being recreated while the sheet is open. */
private val DraftSaver = listSaver<ProfileDraft, Any?>(
    save = { listOf(it.name, it.distroId, it.user, it.scrollback, it.startupCommand) },
    restore = { saved ->
        // In the order they were saved.
        val values = saved.iterator()
        ProfileDraft(
            name = values.next() as String,
            distroId = values.next() as Long?,
            user = values.next() as String,
            scrollback = values.next() as String,
            startupCommand = values.next() as String
        )
    }
)

/**
 * The form of a profile: a new one when [profile] is null, otherwise that one. Save stays off while
 * a field is wrong and the wrong ones are marked and explained. [nameTaken] is set by the screen when
 * the repository says another profile has the name, which only the repository can know.
 */
@Composable
internal fun ProfileEditorSheet(
    profile: Profile?,
    distros: List<Distro>,
    nameTaken: String?,
    onSave: (Profile) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) {
        mutableStateOf(ProfileForm.draftOf(profile))
    }
    var choosingDistro by rememberSaveable { mutableStateOf(false) }
    val result = ProfileForm.build(draft, profile)
    val wrong = (result as? ProfileFormResult.Invalid)?.fields.orEmpty()
    val title = if (profile == null) R.string.profiles_editor_new else R.string.profiles_editor_edit
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column(Modifier.imePadding()) {
            IosSheetHeader(
                title = stringResource(title),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.profiles_save),
                onCancel = onDismiss,
                onConfirm = { (result as? ProfileFormResult.Valid)?.let { onSave(it.profile) } },
                confirmEnabled = result is ProfileFormResult.Valid
            )
            val nameError = nameErrorOf(draft, wrong, nameTaken)
            NameAndDistro(draft, distros, nameError, { draft = it }) { choosingDistro = true }
            UserAndCommand(draft, wrong) { draft = it }
        }
    }
    if (choosingDistro) {
        DistroChoice(distros, onChoose = { draft = draft.copy(distroId = it) }) {
            choosingDistro = false
        }
    }
}

/** What is wrong with the name: its shape, or that another profile has it. */
@Composable
private fun nameErrorOf(
    draft: ProfileDraft,
    wrong: Set<ProfileField>,
    nameTaken: String?
): String? = when {
    ProfileField.NAME in wrong -> stringResource(R.string.profiles_error_name)

    nameTaken != null && nameTaken.equals(draft.name.trim(), ignoreCase = true) ->
        stringResource(R.string.profiles_error_taken, nameTaken)

    else -> null
}

@Composable
private fun NameAndDistro(
    draft: ProfileDraft,
    distros: List<Distro>,
    nameError: String?,
    onChange: (ProfileDraft) -> Unit,
    onChooseDistro: () -> Unit
) {
    IosSection(footer = nameError) {
        IosTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = stringResource(R.string.profiles_field_name),
            isError = nameError != null
        )
        IosListRow(
            title = stringResource(R.string.profiles_field_distro),
            accessory = IosAccessory.Value(
                distros.firstOrNull { it.id == draft.distroId }?.name
                    ?: stringResource(R.string.profiles_default_distro),
                chevron = true
            ),
            showSeparator = false,
            onClick = onChooseDistro
        )
    }
}

@Composable
private fun UserAndCommand(
    draft: ProfileDraft,
    wrong: Set<ProfileField>,
    onChange: (ProfileDraft) -> Unit
) {
    val userNote = if (ProfileField.USER in wrong) {
        stringResource(R.string.profiles_error_user)
    } else {
        stringResource(R.string.profiles_user_hint)
    }
    IosSection(footer = userNote) {
        IosTextField(
            value = draft.user,
            onValueChange = { onChange(draft.copy(user = it)) },
            label = stringResource(R.string.profiles_field_user),
            isError = ProfileField.USER in wrong,
            showSeparator = false
        )
    }
    val scrollbackNote = if (ProfileField.SCROLLBACK in wrong) {
        stringResource(R.string.profiles_error_scrollback)
    } else {
        null
    }
    IosSection(footer = scrollbackNote) {
        IosTextField(
            value = draft.scrollback,
            onValueChange = { onChange(draft.copy(scrollback = it.filter(Char::isDigit))) },
            label = stringResource(R.string.profiles_field_scrollback),
            keyboardType = KeyboardType.Number,
            isError = ProfileField.SCROLLBACK in wrong,
            showSeparator = false
        )
    }
    val commandNote = if (ProfileField.STARTUP_COMMAND in wrong) {
        stringResource(R.string.profiles_error_command)
    } else {
        stringResource(R.string.profiles_command_hint)
    }
    IosSection(footer = commandNote) {
        IosTextField(
            value = draft.startupCommand,
            onValueChange = { onChange(draft.copy(startupCommand = it)) },
            label = stringResource(R.string.profiles_field_command),
            isError = ProfileField.STARTUP_COMMAND in wrong,
            showSeparator = false
        )
    }
    IosSection(footer = stringResource(R.string.profiles_look_hint)) {}
}

/** The distro a profile opens in: the default one, or one of those that are ready. */
@Composable
private fun DistroChoice(distros: List<Distro>, onChoose: (Long?) -> Unit, onDismiss: () -> Unit) {
    val actions = buildList {
        add(IosAction(stringResource(R.string.profiles_default_distro)) { onChoose(null) })
        distros.forEach { distro -> add(IosAction(distro.name) { onChoose(distro.id) }) }
    }
    IosActionSheet(
        actions = actions,
        cancelLabel = stringResource(R.string.dialog_cancel),
        onDismiss = onDismiss,
        title = stringResource(R.string.profiles_field_distro)
    )
}
