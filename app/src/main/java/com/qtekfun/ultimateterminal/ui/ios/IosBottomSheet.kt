// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.ios.SheetDetents
import com.qtekfun.ultimateterminal.domain.ios.SheetSnap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val SCRIM_ALPHA = 0.4f
private const val SPRING_DAMPING = 0.85f
private const val MILLIS_PER_SECOND = 1f

/**
 * A modal sheet that rises from the bottom and rests at the [detents] (medium and large by default).
 * It is dragged by its handle: let go and it settles at the nearest detent, or goes away if it was
 * thrown down. A tap on the dimmed page, the back button and the screen reader's "close" action
 * dismiss it too.
 */
@Composable
fun IosBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    detents: List<SheetDetent> = SheetDetent.entries,
    initial: SheetDetent = detents.first(),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        NoWindowDim()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
            SheetBody(heightPx, onDismiss, SheetSettings(detents, initial), modifier, content)
        }
    }
}

@Composable
private fun NoWindowDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(0f) }
}

/** The heights a sheet rests at, and the one it opens to. */
private class SheetSettings(val detents: List<SheetDetent>, val initial: SheetDetent)

@Composable
private fun BoxWithConstraintsScope.SheetBody(
    heightPx: Float,
    onDismiss: () -> Unit,
    settings: SheetSettings,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val detents = settings.detents
    val initial = settings.initial
    val colors = IosTheme.colors
    val scope = rememberCoroutineScope()
    val fraction = remember { Animatable(0f) }
    val spec = spring<Float>(dampingRatio = SPRING_DAMPING, stiffness = Spring.StiffnessMediumLow)
    LaunchedEffect(initial) { fraction.animateTo(initial.fraction, spec) }

    suspend fun settle(velocityPxPerSecond: Float) {
        // Dragging down is positive in pixels and shrinks the sheet.
        val velocity = -velocityPxPerSecond / heightPx / MILLIS_PER_SECOND
        when (val snap = SheetDetents.snap(fraction.value, velocity, detents)) {
            SheetSnap.Dismiss -> {
                fraction.animateTo(0f, spec)
                onDismiss()
            }

            is SheetSnap.To -> fraction.animateTo(snap.detent.fraction, spec)
        }
    }

    // The sheet sits above the keyboard and shrinks to what is left (D-FIX-9).
    val density = LocalDensity.current
    val imePx = WindowInsets.ime.getBottom(density).toFloat()
    val scrimAlpha = SCRIM_ALPHA * (fraction.value / initial.fraction).coerceIn(0f, 1f)
    Scrim(scrimAlpha, onDismiss)
    Column(
        modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = with(density) { imePx.toDp() })
            .height(
                with(density) {
                    SheetDetents.heightAboveKeyboard(fraction.value, heightPx, imePx).toDp()
                }
            )
            .clip(RoundedCornerShape(topStart = IosRadius.sheet, topEnd = IosRadius.sheet))
            .background(colors.cell)
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null
            ) {}
    ) {
        Handle(
            drag = rememberDraggableState { delta ->
                scope.launch {
                    fraction.snapTo(
                        (fraction.value - delta / heightPx).coerceIn(0f, SheetDetent.LARGE.fraction)
                    )
                }
            },
            onStop = { velocity -> settle(velocity) },
            actions = handleActions(detents, onDismiss) { target ->
                scope.launch { fraction.animateTo(target.fraction, spec) }
            }
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = IosSpacing.md)
                .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)),
            content = content
        )
    }
}

@Composable
private fun Scrim(alpha: Float, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null
            ) { onDismiss() }
    )
}

@Composable
private fun handleActions(
    detents: List<SheetDetent>,
    onDismiss: () -> Unit,
    moveTo: (SheetDetent) -> Unit
): List<CustomAccessibilityAction> {
    val expand = stringResource(R.string.ios_sheet_expand)
    val collapse = stringResource(R.string.ios_sheet_collapse)
    val dismiss = stringResource(R.string.ios_sheet_dismiss)
    return buildList {
        if (SheetDetent.LARGE in
            detents
        ) {
            add(
                CustomAccessibilityAction(expand) {
                    moveTo(SheetDetent.LARGE)
                    true
                }
            )
        }
        if (SheetDetent.MEDIUM in
            detents
        ) {
            add(
                CustomAccessibilityAction(collapse) {
                    moveTo(SheetDetent.MEDIUM)
                    true
                }
            )
        }
        add(
            CustomAccessibilityAction(dismiss) {
                onDismiss()
                true
            }
        )
    }
}

/** The grabber: a 48 dp tall strip, so a finger finds it, with the small pill iOS draws. */
@Composable
private fun Handle(
    drag: DraggableState,
    onStop: suspend CoroutineScope.(Float) -> Unit,
    actions: List<CustomAccessibilityAction>
) {
    val description = stringResource(R.string.ios_sheet_handle)
    Box(
        Modifier
            .fillMaxWidth()
            .height(IosSize.minTouch)
            .draggable(drag, Orientation.Vertical, onDragStopped = onStop)
            .semantics {
                contentDescription = description
                customActions = actions
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(IosSize.sheetHandleWidth)
                .height(IosSize.sheetHandleHeight)
                .clip(CircleShape)
                .background(IosTheme.colors.separator)
        )
    }
}
