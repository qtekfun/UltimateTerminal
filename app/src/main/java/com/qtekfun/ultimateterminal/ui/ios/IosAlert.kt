// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** How an action of an alert or an action sheet reads. */
enum class IosActionRole {
    DEFAULT,

    /** The safe way out: bold, like iOS's Cancel. */
    CANCEL,

    DESTRUCTIVE
}

/** One button of an [IosAlert] or an [IosActionSheet]. It runs [onClick] and the dialog closes. */
data class IosAction(
    val label: String,
    val role: IosActionRole = IosActionRole.DEFAULT,
    val onClick: () -> Unit = {}
)

/** A centered iOS alert: [title], an optional [message] and one to three actions (two sit side by side). */
@Composable
fun IosAlert(
    title: String,
    actions: List<IosAction>,
    onDismiss: () -> Unit,
    message: String? = null
) {
    val colors = IosTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .width(IosSize.alertWidth)
                .clip(RoundedCornerShape(IosRadius.alert))
                .background(colors.cell)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(IosSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IosText(title, style = IosTheme.typography.headline, textAlign = TextAlign.Center)
                if (message != null) {
                    IosText(
                        text = message,
                        modifier = Modifier.padding(top = IosSpacing.xs),
                        style = IosTheme.typography.footnote,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Separator()
            if (actions.size == 2) SideBySide(actions, onDismiss) else Stacked(actions, onDismiss)
        }
    }
}

@Composable
private fun Stacked(actions: List<IosAction>, onDismiss: () -> Unit) {
    actions.forEachIndexed { index, action ->
        if (index > 0) Separator()
        ActionButton(action, onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SideBySide(actions: List<IosAction>, onDismiss: () -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        ActionButton(actions[0], onDismiss, Modifier.weight(1f))
        Box(Modifier.width(IosSize.hairline).fillMaxHeight().background(IosTheme.colors.separator))
        ActionButton(actions[1], onDismiss, Modifier.weight(1f))
    }
}

/** A row that closes the dialog after running its action. At least 48 dp tall to hit. */
@Composable
internal fun ActionButton(action: IosAction, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = IosTheme.colors
    IosPressable(
        onClick = {
            action.onClick()
            onDismiss()
        },
        modifier = modifier,
        pressedColor = colors.pressedCell
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = IosSize.minTouch),
            contentAlignment = Alignment.Center
        ) {
            IosText(
                text = action.label,
                style = IosTheme.typography.body.copy(
                    fontWeight = if (action.role ==
                        IosActionRole.CANCEL
                    ) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    }
                ),
                color = if (action.role ==
                    IosActionRole.DESTRUCTIVE
                ) {
                    colors.destructive
                } else {
                    colors.tint
                },
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun Separator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(IosSize.hairline).background(IosTheme.colors.separator))
}
