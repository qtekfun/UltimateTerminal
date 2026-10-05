// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.settings.DnsParse
import com.qtekfun.ultimateterminal.domain.settings.DnsProblem
import com.qtekfun.ultimateterminal.domain.settings.DnsServers
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosTextField

/**
 * Edits the fallback DNS servers. What is typed is read by [DnsServers]; Save stays off while it is
 * not valid, and an empty box means the built-in servers.
 */
@Composable
internal fun DnsSheet(
    current: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable {
        mutableStateOf(if (DnsServers.isDefault(current)) "" else DnsServers.format(current))
    }
    val parsed = DnsServers.parse(text)
    val hint = stringResource(R.string.settings_dns_hint)
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(R.string.settings_dns_sheet_title),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.settings_dns_save),
                onCancel = onDismiss,
                onConfirm = {
                    onSave(parsed.servers)
                    onDismiss()
                },
                confirmEnabled = parsed.problem == null
            )
            IosSection(footer = hint) {
                IosTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.settings_dns_field),
                    keyboardType = KeyboardType.Uri,
                    isError = parsed.problem != null,
                    showSeparator = false
                )
            }
            if (parsed.problem != null) {
                IosSection {
                    IosListRow(
                        title = problemText(parsed),
                        destructive = true,
                        showSeparator = false
                    )
                }
            }
            if (!DnsServers.isDefault(current)) {
                IosSection {
                    IosListRow(
                        title = stringResource(R.string.settings_dns_restore),
                        showSeparator = false,
                        onClick = {
                            onSave(emptyList())
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun problemText(parsed: DnsParse): String = when (parsed.problem) {
    DnsProblem.NOT_AN_ADDRESS ->
        stringResource(R.string.settings_dns_error_address, parsed.rejected.firstOrNull().orEmpty())

    DnsProblem.TOO_MANY -> stringResource(R.string.settings_dns_error_many)

    null -> ""
}
