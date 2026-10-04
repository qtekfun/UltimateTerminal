// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

private const val DISABLED_ALPHA = 0.4f

/**
 * The top of a form sheet: Cancel at the start, the [title] in the middle and the confirming button
 * at the end, in bold, dimmed and inert while [confirmEnabled] is false.
 */
@Composable
fun IosSheetHeader(
    title: String,
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = IosSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            IosBarButton(cancelLabel, onCancel)
        }
        IosText(
            title,
            Modifier.weight(2f).semantics { heading() },
            style = IosTheme.typography.headline,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            IosBarButton(
                confirmLabel,
                onClick = { if (confirmEnabled) onConfirm() },
                modifier = Modifier
                    .alpha(if (confirmEnabled) 1f else DISABLED_ALPHA)
                    .semantics { if (!confirmEnabled) disabled() },
                bold = true
            )
        }
    }
}
