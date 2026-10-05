// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.appearance.AppearanceMessage
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection

/** The result of the last import or export, as a row that dismisses itself when tapped. */
@Composable
internal fun AppearanceMessageBar(message: AppearanceMessage, onDismiss: () -> Unit) {
    IosSection {
        IosListRow(
            title = messageText(message),
            glyph = IosGlyph.INFO,
            accessory = IosAccessory.Value(stringResource(R.string.appearance_ok)),
            showSeparator = false,
            onClick = onDismiss
        )
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
