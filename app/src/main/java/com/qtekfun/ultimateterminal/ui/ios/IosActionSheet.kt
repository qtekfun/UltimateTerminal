// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import android.view.Gravity
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * A group of actions that slides up from the bottom, with the cancel button apart in its own card, as
 * in iOS. [cancelLabel] is the text of that button (the caller passes it so it is localized).
 */
@Composable
fun IosActionSheet(
    actions: List<IosAction>,
    cancelLabel: String,
    onDismiss: () -> Unit,
    title: String? = null,
    message: String? = null
) {
    val colors = IosTheme.colors
    val shape = RoundedCornerShape(IosRadius.alert)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BottomOfScreen()
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(IosSpacing.sm)) {
            Column(Modifier.fillMaxWidth().clip(shape).background(colors.cell)) {
                if (title != null || message != null) {
                    Column(
                        Modifier.fillMaxWidth().padding(IosSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (title != null) {
                            IosText(
                                title,
                                style = IosTheme.typography.footnote,
                                color = colors.secondaryLabel,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (message != null) {
                            IosText(
                                message,
                                style = IosTheme.typography.footnote,
                                color = colors.secondaryLabel,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Separator()
                }
                actions.forEachIndexed { index, action ->
                    if (index > 0) Separator()
                    ActionButton(action, onDismiss, Modifier.fillMaxWidth())
                }
            }
            Column(
                Modifier.padding(
                    top = IosSpacing.sm
                ).fillMaxWidth().clip(shape).background(colors.cell)
            ) {
                ActionButton(
                    IosAction(cancelLabel, IosActionRole.CANCEL),
                    onDismiss,
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Moves the dialog's window to the bottom edge, full width. Used inside a [Dialog]. */
@Composable
internal fun BottomOfScreen() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.setGravity(Gravity.BOTTOM)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
