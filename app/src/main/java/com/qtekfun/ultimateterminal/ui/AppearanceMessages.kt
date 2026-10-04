// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.appearance.AppearanceMessage
import com.qtekfun.ultimateterminal.domain.DomainError

/** The result of the last import or export, with a way to dismiss it. */
@Composable
internal fun AppearanceMessageBar(message: AppearanceMessage, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(messageText(message), modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.appearance_ok)) }
        }
    }
}

@Composable
private fun messageText(message: AppearanceMessage): String = when (message) {
    is AppearanceMessage.FontImported -> stringResource(
        R.string.appearance_msg_font_imported,
        message.name
    )

    is AppearanceMessage.FontFailed -> stringResource(
        fontError(message.error),
        nameOf(message.error)
    )

    is AppearanceMessage.SchemeImported ->
        stringResource(R.string.appearance_msg_scheme_imported, message.name)

    is AppearanceMessage.SchemeFailed ->
        stringResource(schemeError(message.error), nameOf(message.error))

    AppearanceMessage.SchemeExported -> stringResource(R.string.appearance_msg_scheme_exported)

    AppearanceMessage.FileFailed -> stringResource(R.string.appearance_msg_file_failed)
}

private fun nameOf(error: DomainError): String = (error as? DomainError.NameTaken)?.name.orEmpty()

private fun fontError(error: DomainError): Int = when {
    error is DomainError.NameTaken -> R.string.appearance_msg_font_name_taken

    error is DomainError.InvalidValue && error.field == "customFonts" ->
        R.string.appearance_msg_font_limit

    error is DomainError.InvalidValue -> when (error.field) {
        "TOO_LARGE" -> R.string.appearance_msg_font_too_large
        "UNREADABLE" -> R.string.appearance_msg_font_unreadable
        "NOT_MONOSPACED" -> R.string.appearance_msg_font_not_monospaced
        "MISSING_GLYPHS" -> R.string.appearance_msg_font_missing_glyphs
        "IO" -> R.string.appearance_msg_font_io
        else -> R.string.appearance_msg_font_not_a_font
    }

    else -> R.string.appearance_msg_font_not_a_font
}

private fun schemeError(error: DomainError): Int = when (error) {
    is DomainError.NameTaken -> R.string.appearance_msg_scheme_name_taken

    is DomainError.InvalidValue ->
        if (error.field == "customSchemes") {
            R.string.appearance_msg_scheme_limit
        } else {
            R.string.appearance_msg_scheme_invalid
        }

    else -> R.string.appearance_msg_scheme_invalid
}
