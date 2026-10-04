// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeyCatalog
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysEditing
import com.qtekfun.ultimateterminal.domain.terminal.unusedKeys
import com.qtekfun.ultimateterminal.domain.terminal.withKey
import com.qtekfun.ultimateterminal.domain.terminal.withKeyMoved
import com.qtekfun.ultimateterminal.domain.terminal.withoutKey
import com.qtekfun.ultimateterminal.settings.SettingsViewModel
import com.qtekfun.ultimateterminal.ui.extraKeyDescription
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosListRow

/** Where a key is: its row and its place in it. */
private data class KeyPosition(val row: Int, val index: Int)

/** Arrange the extra keys: move or remove the ones in the rows, add the ones that are not. */
@Composable
internal fun KeyboardKeysPage(settings: AppSettings, viewModel: SettingsViewModel, nav: PageNav) {
    val config = settings.extraKeys
    var selected by remember { mutableStateOf<KeyPosition?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val unused = config.unusedKeys()
    val rowHeaders = config.rows.indices.map { stringResource(R.string.settings_keys_row, it + 1) }
    val addFooter = if (isFull(config)) {
        stringResource(R.string.settings_keys_full)
    } else {
        stringResource(
            R.string.settings_keys_add_footer,
            ExtraKeysEditing.MAX_KEYS_PER_ROW,
            ExtraKeysEditing.MAX_ROWS
        )
    }
    val emptyHint = stringResource(R.string.settings_keys_empty)
    val addHeader = stringResource(R.string.settings_keys_add_header)
    val noneLeft = stringResource(R.string.settings_keys_none_left)
    SettingsPage(stringResource(R.string.settings_keys_customize), nav.backLabel, nav.back) {
        if (config.rows.isEmpty()) section(footer = emptyHint) {}
        config.rows.forEachIndexed { row, keys ->
            section(header = rowHeaders[row]) {
                keys.forEachIndexed { index, id ->
                    KeyRow(id, last = index == keys.lastIndex) {
                        selected = KeyPosition(row, index)
                    }
                }
            }
        }
        section(header = addHeader, footer = addFooter) {
            if (unused.isEmpty()) IosListRow(title = noneLeft, showSeparator = false)
            unused.forEachIndexed { index, key ->
                KeyRow(
                    key.id,
                    last = index == unused.lastIndex,
                    accessory = IosAccessory.None
                ) { viewModel.editExtraKeys { it.withKey(targetRow(it), key.id) } }
            }
        }
        section {
            IosListRow(
                title = stringResource(R.string.settings_keys_reset),
                destructive = true,
                showSeparator = false,
                onClick = { confirmReset = true }
            )
        }
    }
    selected?.let { position ->
        KeyActions(config, position, viewModel) { selected = null }
    }
    if (confirmReset) {
        ResetAlert(onConfirm = viewModel::resetExtraKeys, onDismiss = { confirmReset = false })
    }
}

/** The row a key added with one tap goes to: the last one if it has room, otherwise a new one. */
private fun targetRow(config: ExtraKeysConfig): Int =
    if ((config.rows.lastOrNull()?.size ?: ExtraKeysEditing.MAX_KEYS_PER_ROW) <
        ExtraKeysEditing.MAX_KEYS_PER_ROW
    ) {
        config.rows.lastIndex
    } else {
        config.rows.size
    }

/** No room for another key: every row is full and no new row is allowed. */
private fun isFull(config: ExtraKeysConfig): Boolean = config.unusedKeys().isNotEmpty() &&
    config.rows.size >= ExtraKeysEditing.MAX_ROWS && targetRow(config) >= config.rows.size

@Composable
private fun KeyRow(
    id: String,
    last: Boolean,
    accessory: IosAccessory = IosAccessory.None,
    onClick: () -> Unit
) {
    val key = ExtraKeyCatalog.find(id)
    IosListRow(
        title = key?.symbol ?: id,
        subtitle = extraKeyDescription(id)?.let { stringResource(it) },
        glyph = if (accessory == IosAccessory.None) null else IosGlyph.PLUS,
        accessory = accessory,
        showSeparator = !last,
        onClick = onClick
    )
}

@Composable
private fun KeyActions(
    config: ExtraKeysConfig,
    position: KeyPosition,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val (row, index) = position
    val last = config.rows.getOrNull(row)?.lastIndex ?: -1
    val actions = buildList {
        if (index > 0) {
            add(
                IosAction(stringResource(R.string.settings_keys_move_left)) {
                    viewModel.editExtraKeys { it.withKeyMoved(row, index, index - 1) }
                }
            )
        }
        if (index < last) {
            add(
                IosAction(stringResource(R.string.settings_keys_move_right)) {
                    viewModel.editExtraKeys { it.withKeyMoved(row, index, index + 1) }
                }
            )
        }
        add(
            IosAction(stringResource(R.string.settings_keys_remove), IosActionRole.DESTRUCTIVE) {
                viewModel.editExtraKeys { it.withoutKey(row, index) }
            }
        )
    }
    IosActionSheet(
        actions = actions,
        cancelLabel = stringResource(R.string.dialog_cancel),
        onDismiss = onDismiss,
        title = config.rows.getOrNull(row)?.getOrNull(index)?.let {
            ExtraKeyCatalog.find(it)?.symbol
        }
    )
}

@Composable
private fun ResetAlert(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    IosAlert(
        title = stringResource(R.string.settings_keys_reset_title),
        message = stringResource(R.string.settings_keys_reset_body),
        actions = listOf(
            IosAction(stringResource(R.string.dialog_cancel), IosActionRole.CANCEL),
            IosAction(
                stringResource(R.string.settings_keys_reset),
                IosActionRole.DESTRUCTIVE,
                onConfirm
            )
        ),
        onDismiss = onDismiss
    )
}
