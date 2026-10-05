// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.launch.ExitHint
import com.qtekfun.ultimateterminal.domain.session.TabBarPlacement
import com.qtekfun.ultimateterminal.domain.session.TabsController
import com.qtekfun.ultimateterminal.domain.session.reserveForTabBar
import com.qtekfun.ultimateterminal.domain.session.tabBarPlacement
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.EdgeInsets
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.extraKeysHeightPx
import com.qtekfun.ultimateterminal.domain.terminal.reserveBottom
import com.qtekfun.ultimateterminal.domain.terminal.terminalLayoutFor
import com.qtekfun.ultimateterminal.domain.terminal.withTextMargin
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.domain.theme.ThemeDecision
import com.qtekfun.ultimateterminal.terminal.PainterStyle
import com.qtekfun.ultimateterminal.terminal.TerminalInputView
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.terminal.TerminalTypefaces
import com.qtekfun.ultimateterminal.terminal.TerminalViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosButton
import com.qtekfun.ultimateterminal.ui.ios.IosButtonStyle
import com.qtekfun.ultimateterminal.ui.ios.IosTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * The terminal: a Compose canvas that draws the emulator, plus an invisible view that takes the
 * keyboard. The window is drawn edge to edge; the terminal takes all of it except what the system
 * bars, the display cutout and the keyboard cover, and the pty is resized to that area (T04).
 *
 * Prototype (T03, T04). Drawing, gestures, keyboard input and resizing have not been validated on
 * a device yet (see DECISIONS.md).
 */
@Composable
fun TerminalScreen(
    look: TerminalLook,
    initialFontSizeSp: Float,
    onFontSizeChanged: (Float) -> Unit,
    screens: ScreenLinks,
    modifier: Modifier = Modifier,
    viewModel: TerminalViewModel = viewModel()
) {
    val (scheme, appearance, typefaces) = look
    val density = LocalDensity.current
    // Before the layout effect below, so a shell that starts on the first layout has the colors.
    SchemeAndFontEffects(viewModel, scheme, initialFontSizeSp, onFontSizeChanged)
    val fontSizeSp by viewModel.fontSize.sizeSp.collectAsStateWithLifecycle()
    val painter = rememberTerminalPainter(fontSizeSp, scheme, appearance, typefaces)
    val inputView = remember { arrayOfNulls<TerminalInputView>(1) }

    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    val insets = coveredEdges()
    val placement = tabBarPlacementOf(windowSize, density)
    LayoutEffect(viewModel, painter, windowSize, insets, appearance.marginDp)

    // The scheme's background fills the whole window, bars included; the content is padded by the
    // same insets the layout was computed with, so what is drawn is exactly what the pty is told.
    val chrome = rememberChromePalette(scheme, appearance)
    // The iOS-style bars, sheets and alerts follow the terminal's scheme, not the system theme: the
    // screen is drawn in the scheme's colors and its chrome has to sit well with them.
    val decision = remember(scheme) {
        ThemeDecision(dark = scheme.isDark, oled = scheme.background == TerminalColorScheme.BLACK)
    }
    IosTheme(decision, scheme) {
        CompositionLocalProvider(LocalChromePalette provides chrome) {
            TerminalContent(
                modifier,
                TerminalParts(viewModel, painter, inputView, look, screens),
                ContentLayout(placement, insets, density)
            ) { windowSize = it }
        }
    }
}

/** What the terminal screen draws and talks to. */
private data class TerminalParts(
    val viewModel: TerminalViewModel,
    val painter: TerminalPainter,
    val inputView: Array<TerminalInputView?>,
    val look: TerminalLook,
    val screens: ScreenLinks
)

/** How the window is split between the tab bar, the terminal and what the system covers. */
private data class ContentLayout(
    val placement: TabBarPlacement,
    val insets: EdgeInsets,
    val density: Density
)

@Composable
private fun TerminalContent(
    modifier: Modifier,
    parts: TerminalParts,
    layout: ContentLayout,
    onWindowSize: (IntSize) -> Unit
) {
    val viewModel = parts.viewModel
    val painter = parts.painter
    val inputView = parts.inputView
    val screens = parts.screens
    val look = parts.look
    val (placement, insets, density) = layout
    val (scheme, appearance) = look
    Box(
        modifier.fillMaxSize().background(Color(scheme.background)).onSizeChanged {
            onWindowSize(it)
        }
    ) {
        val padded = Modifier.fillMaxSize().padding(insets.toPadding(density))
        val links = TabBarLinks(
            openDistros = screens.openDistros,
            openSsh = screens.openSsh,
            openAppearance = screens.openAppearance,
            openSettings = screens.openSettings,
            splitRight = viewModel.panes::splitVertical,
            splitDown = viewModel.panes::splitHorizontal
        )
        val pane = @Composable { paneModifier: Modifier ->
            TerminalPane(
                viewModel,
                painter,
                inputView,
                appearance.marginDp.dp,
                screens.openDistros,
                paneModifier
            )
        }
        if (placement == TabBarPlacement.Top) {
            Column(padded) {
                TabBarSlot(viewModel.tabs, placement, links)
                pane(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(padded) {
                TabBarSlot(viewModel.tabs, placement, links)
                pane(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** What the terminal looks like: its colors, its design and the fonts it draws with. */
data class TerminalLook(
    val scheme: TerminalColorScheme,
    val appearance: TerminalAppearance,
    val typefaces: TerminalTypefaces
)

/**
 * True while another screen (Settings, Appearance, Distros, SSH) is drawn over the terminal. The
 * terminal stays composed underneath, so without this its input view would keep the keyboard open
 * over those screens. Provided by `MainActivity`.
 */
val LocalTerminalCovered = compositionLocalOf { false }

/** The other screens the terminal opens from its menus. */
class ScreenLinks(
    val openDistros: () -> Unit,
    val openSsh: () -> Unit,
    val openAppearance: () -> Unit,
    val openSettings: () -> Unit
)

/** The tab bar at the size its placement reserves, which the grid of the terminal leaves out. */
@Composable
private fun TabBarSlot(tabs: TabsController, placement: TabBarPlacement, links: TabBarLinks) {
    val size = if (placement == TabBarPlacement.Top) {
        Modifier.fillMaxWidth().height(TabBarHeight)
    } else {
        Modifier.fillMaxHeight().width(TabBarSideWidth)
    }
    TabBar(tabs, placement, links, size)
}

/** The terminal, the extra-keys row under it and, over the first rows, what the tab could not start. */
@Composable
private fun TerminalPane(
    viewModel: TerminalViewModel,
    painter: TerminalPainter,
    inputView: Array<TerminalInputView?>,
    margin: Dp,
    onOpenDistros: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extraKeys = rememberShownExtraKeys(viewModel)
    val sticky by viewModel.stickyModifiers.collectAsStateWithLifecycle()
    val launchMessage by viewModel.launchMessage.collectAsStateWithLifecycle()
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            // The margin is applied before the panes measure their area, so the grid they give each
            // pty is the padded one, and `withTextMargin` makes the first layout agree with it.
            TerminalPanes(
                viewModel,
                painter,
                inputView,
                Modifier.weight(1f).fillMaxWidth().padding(margin)
            )
            if (extraKeys.visible) {
                ExtraKeysRow(extraKeys, sticky, viewModel.keyboard::onExtraKey)
            }
        }
        // Over the first rows, so the pty's size does not change when it appears.
        LaunchMessageBanner(launchMessage, onOpenDistros, Modifier.align(Alignment.TopCenter))
    }
}

/** The painter for the current font size, scheme, font and spacing. */
@Composable
private fun rememberTerminalPainter(
    fontSizeSp: Float,
    scheme: TerminalColorScheme,
    appearance: TerminalAppearance,
    typefaces: TerminalTypefaces
): TerminalPainter {
    val density = LocalDensity.current
    val style = remember(appearance) { PainterStyle.of(appearance) }
    return remember(density, fontSizeSp, typefaces, scheme.selection, style) {
        TerminalPainter(typefaces, with(density) { fontSizeSp.sp.toPx() }, scheme.selection, style)
    }
}

/** Applies the stored colors and font size, and saves the font size when the user changes it. */
@OptIn(FlowPreview::class)
@Composable
private fun SchemeAndFontEffects(
    viewModel: TerminalViewModel,
    scheme: TerminalColorScheme,
    initialFontSizeSp: Float,
    onFontSizeChanged: (Float) -> Unit
) {
    LaunchedEffect(scheme) { viewModel.applyScheme(scheme) }
    // A size chosen in the appearance screen arrives as a new stored value; the pinch and the
    // shortcuts save theirs, which then matches the current size and changes nothing.
    LaunchedEffect(initialFontSizeSp) {
        if (viewModel.fontSize.sizeSp.value != initialFontSizeSp) {
            viewModel.fontSize.restore(initialFontSizeSp)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.fontSize.restore(initialFontSizeSp)
        // The first value is the one just restored (or the default): only user changes are saved.
        viewModel.fontSize.sizeSp.drop(1).debounce(FONT_SIZE_SAVE_DELAY_MILLIS)
            .collect(onFontSizeChanged)
    }
}

@Composable
internal fun TerminalOverlays(viewModel: TerminalViewModel, inputView: Array<TerminalInputView?>) {
    val selection by viewModel.selection.selection.collectAsStateWithLifecycle()
    val exitStatus by viewModel.exitStatus.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context -> TerminalInputView(context).also { inputView[0] = it } },
            update = { it.sink = viewModel.keyboard },
            modifier = Modifier.size(1.dp)
        )
        // Opens once when the terminal first shows (not when it starts already covered); after a
        // screen covers it the keyboard is hidden and only a tap on the terminal brings it back.
        val covered = LocalTerminalCovered.current
        LaunchedEffect(Unit) { if (!covered) inputView[0]?.showKeyboard() }
        LaunchedEffect(covered) { if (covered) inputView[0]?.hideKeyboard() }

        if (selection != null) {
            IosButton(
                text = stringResource(R.string.terminal_copy),
                onClick = viewModel.selection::copy,
                modifier = Modifier.align(
                    Alignment.TopEnd
                ).padding(8.dp).width(OVERLAY_BUTTON_WIDTH),
                style = IosButtonStyle.TINTED
            )
        }
        exitStatus?.let { status ->
            IosButton(
                text = stringResource(
                    when (ExitHint.of(status)) {
                        ExitHint.COMMAND_NOT_FOUND -> R.string.terminal_session_ended_not_found
                        ExitHint.NOT_EXECUTABLE -> R.string.terminal_session_ended_not_executable
                        null -> R.string.terminal_session_ended
                    },
                    status
                ),
                onClick = viewModel::restart,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp)
            )
        }
    }
}

/** Draws the screen and turns touches into scrolling, selection and a request for the keyboard. */
@Composable
internal fun TerminalCanvas(
    viewModel: TerminalViewModel,
    painter: TerminalPainter,
    onTap: () -> Unit
) {
    val frame by viewModel.frame.collectAsStateWithLifecycle()
    val topRow by viewModel.topRow.collectAsStateWithLifecycle()
    val selection by viewModel.selection.selection.collectAsStateWithLifecycle()
    val onPinch = remember(viewModel) { viewModel.fontSize::pinch }
    // A blinking cursor redraws the canvas on a timer, and shows solid while output arrives.
    var blinkTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(painter) {
        painter.cursorBlinkOn = true
        while (painter.cursorBlinks) {
            delay(CURSOR_BLINK_MILLIS)
            painter.cursorBlinkOn = !painter.cursorBlinkOn
            blinkTick++
        }
    }

    fun cellAt(offset: Offset) = CellPosition(
        column = (offset.x / painter.cellWidth).toInt().coerceAtLeast(0),
        row = (offset.y / painter.cellHeight).toInt().coerceAtLeast(0) + topRow
    )

    // The screen is drawn, so a screen reader gets a name and a way to raise the keyboard, not the
    // text: reading every redraw aloud would be noise, and the content must not leak into any log.
    val description = stringResource(R.string.terminal_description)
    val showKeyboard = stringResource(R.string.terminal_show_keyboard)
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = description
                onClick(label = showKeyboard) {
                    onTap()
                    true
                }
            }
            .pointerInput(painter) {
                detectTapGestures(onTap = {
                    viewModel.selection.clear()
                    onTap()
                })
            }
            .pointerInput(painter) {
                detectVerticalDragGestures { _, dragAmount ->
                    viewModel.scrollBy(dragAmount, painter.cellHeight.toFloat())
                }
            }
            .pointerInput(painter) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { viewModel.selection.start(cellAt(it)) },
                    onDrag = { change, _ -> viewModel.selection.extend(cellAt(change.position)) }
                )
            }
            // Last, so it sees the events first and can take a two-finger pinch for itself.
            .pinchToZoom(onPinch)
    ) {
        // Reading the frame here makes only the drawing, not the composition, depend on it.
        val emulator = viewModel.emulator
        if (frame >= 0 && blinkTick >= 0 && emulator != null) {
            drawIntoCanvas { painter.draw(it.nativeCanvas, emulator, topRow, selection) }
        }
    }
}

private val OVERLAY_BUTTON_WIDTH = 120.dp

/** Half a period of the blinking cursor. */
private const val CURSOR_BLINK_MILLIS = 530L

/** How long the font size must stay put before it is saved: a pinch changes it many times. */
private const val FONT_SIZE_SAVE_DELAY_MILLIS = 500L
