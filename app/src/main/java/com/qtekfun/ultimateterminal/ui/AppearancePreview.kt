// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.terminal.TerminalTypefaces

private const val NATURAL_LINE_HEIGHT = 1.32f
private val SwatchHeight = 20.dp

private const val GREEN = 2
private const val YELLOW = 3
private const val BLUE = 4
private const val MAGENTA = 5
private const val CYAN = 6
private const val RED = 1

/**
 * A sample of the terminal drawn with the colors, the font, the spacing, the margin and the cursor
 * the user has chosen, so every change is seen before going back to the terminal.
 */
@Composable
fun AppearancePreview(
    scheme: TerminalColorScheme,
    appearance: TerminalAppearance,
    fontSizeSp: Float,
    typefaces: TerminalTypefaces,
    modifier: Modifier = Modifier
) {
    val family = remember(typefaces) { FontFamily(typefaces.regular) }
    val description = stringResource(R.string.appearance_preview_description)
    val sample =
        remember(scheme, appearance.cursorShape) { sampleText(scheme, appearance.cursorShape) }
    Column(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .background(Color(scheme.background), RoundedCornerShape(appearance.cornerRadiusDp.dp))
            .padding(appearance.marginDp.dp)
    ) {
        Text(
            text = sample,
            fontFamily = family,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * NATURAL_LINE_HEIGHT * appearance.lineSpacing).sp,
            letterSpacing = appearance.letterSpacing.em,
            color = Color(scheme.foreground)
        )
        AnsiSwatches(scheme.ansi)
    }
}

/** [colors] as rows of eight blocks: the 16 ANSI colors make two rows. */
@Composable
internal fun AnsiSwatches(colors: List<Int>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        colors.chunked(SWATCHES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { color ->
                    Box(Modifier.weight(1f).height(SwatchHeight).background(Color(color)))
                }
            }
        }
    }
}

private const val SWATCHES_PER_ROW = 8

private fun sampleText(scheme: TerminalColorScheme, cursor: CursorShape): AnnotatedString {
    fun color(index: Int) = Color(scheme.ansi[index])
    return buildAnnotatedString {
        withStyle(SpanStyle(color = color(GREEN))) { append("user@host") }
        append(":")
        withStyle(SpanStyle(color = color(BLUE))) { append("~/src") }
        append("$ ls --color\n")
        withStyle(SpanStyle(color = color(BLUE))) { append("docs  ") }
        withStyle(SpanStyle(color = color(CYAN))) { append("link  ") }
        withStyle(SpanStyle(color = color(GREEN))) { append("run.sh  ") }
        append("README.md\n")
        withStyle(SpanStyle(color = color(RED))) { append("error: ") }
        append("not found  ")
        withStyle(SpanStyle(color = color(YELLOW))) { append("warning  ") }
        withStyle(SpanStyle(color = color(MAGENTA))) { append("0O 1lI\n") }
        withStyle(SpanStyle(color = color(GREEN))) { append("user@host") }
        append(":")
        withStyle(SpanStyle(color = color(BLUE))) { append("~/src") }
        append("$ ")
        appendCursor(scheme, cursor)
    }
}

private fun AnnotatedString.Builder.appendCursor(scheme: TerminalColorScheme, shape: CursorShape) {
    val cursor = Color(scheme.cursor)
    when (shape) {
        CursorShape.BLOCK -> withStyle(SpanStyle(background = cursor)) { append(" ") }

        CursorShape.UNDERLINE -> withStyle(
            SpanStyle(color = cursor, textDecoration = TextDecoration.Underline)
        ) { append(" ") }

        CursorShape.BAR -> withStyle(SpanStyle(color = cursor)) { append("▏") }
    }
}
