// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qtekfun.ultimateterminal.domain.session.TabTitle
import com.qtekfun.ultimateterminal.ui.ios.ActionButton
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosRadius
import com.qtekfun.ultimateterminal.ui.ios.IosSize
import com.qtekfun.ultimateterminal.ui.ios.IosSpacing
import com.qtekfun.ultimateterminal.ui.ios.IosText
import com.qtekfun.ultimateterminal.ui.ios.IosTheme
import com.qtekfun.ultimateterminal.ui.ios.Separator

private val FieldHeight = 36.dp

/** The words of a [NamePrompt], already in the user's language. */
internal class NamePromptTexts(
    val title: String,
    val label: String,
    val hint: String,
    val save: String,
    val cancel: String
)

/**
 * An iOS alert that asks for a name: [title], a text field, a [hint] under it and two buttons side
 * by side. It looks like [com.qtekfun.ultimateterminal.ui.ios.IosAlert], which has no field, and
 * takes the keyboard on its own. [onValueChange] is given the text cut to [TabTitle.MAX_LENGTH].
 */
@Composable
internal fun NamePrompt(
    texts: NamePromptTexts,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = IosTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
                IosText(
                    texts.title,
                    style = IosTheme.typography.headline,
                    textAlign = TextAlign.Center
                )
                NameField(
                    value,
                    { onValueChange(it.take(TabTitle.MAX_LENGTH)) },
                    texts.label,
                    onSave,
                    Modifier.focusRequester(focus)
                )
                IosText(
                    texts.hint,
                    modifier = Modifier.padding(top = IosSpacing.xs),
                    style = IosTheme.typography.footnote,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center
                )
            }
            Separator()
            Row(Modifier.height(IntrinsicSize.Min)) {
                ActionButton(
                    IosAction(texts.cancel, IosActionRole.CANCEL),
                    onDismiss,
                    Modifier.weight(1f)
                )
                Box(
                    Modifier.width(IosSize.hairline).fillMaxHeight().background(colors.separator)
                )
                ActionButton(IosAction(texts.save, onClick = onSave), onDismiss = {
                }, Modifier.weight(1f))
            }
        }
    }
}

/** The single-line field of an alert: grey, rounded, with the tint as caret. */
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier
) {
    val colors = IosTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .padding(top = IosSpacing.sm)
            .fillMaxWidth()
            .semantics { contentDescription = label },
        singleLine = true,
        textStyle = IosTheme.typography.body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.tint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = FieldHeight)
                    .clip(RoundedCornerShape(IosRadius.control))
                    .background(colors.fill)
                    .padding(horizontal = IosSpacing.sm, vertical = IosSpacing.xs),
                contentAlignment = Alignment.CenterStart
            ) { field() }
        }
    )
}
