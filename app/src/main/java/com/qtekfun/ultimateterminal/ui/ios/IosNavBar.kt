// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.qtekfun.ultimateterminal.domain.ios.BarStyle
import com.qtekfun.ultimateterminal.domain.ios.LargeTitle

/**
 * A screen with an iOS-style large title: the title sits big above the content and, as the content
 * scrolls, collapses into the translucent navigation bar. [leading] and [trailing] are the bar's
 * buttons. The content is a list: put [IosSection]s in it with `item { }`.
 */
@Composable
fun IosLargeTitleScreen(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val listState = rememberLazyListState()
    val backdrop = rememberBackdropState()
    var titleHeightPx by remember { mutableFloatStateOf(0f) }
    val scrollPx by remember {
        derivedStateOf {
            // Once the title item has scrolled out of the list the collapse is complete.
            if (listState.firstVisibleItemIndex ==
                0
            ) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                Float.MAX_VALUE
            }
        }
    }
    val fraction = LargeTitle.collapseFraction(scrollPx, titleHeightPx)
    val barBottom =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + IosSize.navBarHeight
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize().background(IosTheme.colors.groupedBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().backdropSource(backdrop),
            contentPadding = PaddingValues(top = barBottom, bottom = bottomInset + IosSpacing.lg)
        ) {
            item {
                LargeTitleHeader(
                    title,
                    fraction,
                    Modifier.onSizeChanged {
                        titleHeightPx =
                            it.height.toFloat()
                    }
                )
            }
            content()
        }
        IosNavBar(title, fraction, backdrop, Modifier.align(Alignment.TopCenter), leading, trailing)
    }
}

@Composable
private fun LargeTitleHeader(title: String, fraction: Float, modifier: Modifier) {
    IosText(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .alpha(LargeTitle.largeTitleAlpha(fraction))
            .padding(horizontal = IosSpacing.md, vertical = IosSpacing.sm)
            .semantics { heading() },
        style = IosTheme.typography.largeTitle
    )
}

/** The navigation bar: translucent over the content, with the small title appearing as it collapses. */
@Composable
fun IosNavBar(
    title: String,
    collapseFraction: Float,
    backdrop: BackdropState,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val colors = IosTheme.colors
    val style = remember { BarStyle.forSdk(Build.VERSION.SDK_INT) }
    val visible = LargeTitle.separatorAlpha(collapseFraction)
    Box(
        modifier
            .fillMaxWidth()
            .backdropBar(backdrop, style, colors.groupedBackground, visible)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            Modifier.fillMaxWidth().height(
                IosSize.navBarHeight
            ).padding(horizontal = IosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { leading() }
            IosText(
                text = title,
                // The large title is the heading; this one only repeats it for the eye.
                modifier = Modifier
                    .alpha(LargeTitle.smallTitleAlpha(collapseFraction))
                    .clearAndSetSemantics {},
                style = IosTheme.typography.headline,
                maxLines = 1
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, content = trailing)
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(IosSize.hairline)
                .alpha(visible)
                .background(colors.separator)
        )
    }
}

/** A text-only button for the bar ("Done", "Edit"): tinted, and at least 48 dp to hit. */
@Composable
fun IosBarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bold: Boolean = false
) {
    IosPressable(onClick, modifier.heightIn(min = IosSize.minTouch)) { pressed ->
        Box(
            Modifier
                .heightIn(min = IosSize.minTouch)
                .widthIn(min = IosSize.minTouch)
                .padding(horizontal = IosSpacing.sm),
            contentAlignment = Alignment.Center
        ) {
            IosText(
                text = text,
                modifier = Modifier.alpha(if (pressed) PRESSED_ALPHA else 1f),
                style = if (bold) IosTheme.typography.headline else IosTheme.typography.body,
                color = IosTheme.colors.tint
            )
        }
    }
}

/** An icon-only button for the bar. */
@Composable
fun IosBarIconButton(
    glyph: IosGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IosPressable(onClick, modifier.height(IosSize.minTouch)) { pressed ->
        Box(
            Modifier
                .size(IosSize.minTouch, IosSize.minTouch)
                .padding(horizontal = IosSpacing.sm + IosSpacing.xs),
            contentAlignment = Alignment.Center
        ) {
            IosIcon(glyph, contentDescription, Modifier.alpha(if (pressed) PRESSED_ALPHA else 1f))
        }
    }
}

internal const val PRESSED_ALPHA = 0.5f
