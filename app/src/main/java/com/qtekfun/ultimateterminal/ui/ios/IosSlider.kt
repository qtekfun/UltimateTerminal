// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.domain.ios.SliderMath

private val TrackHeight = 4.dp
private val ThumbSize = 28.dp

/**
 * The iOS slider: a thin track filled with the tint up to a round white thumb. It is 48 dp tall to
 * hit. [onValueChange] runs on every move and [onValueChangeFinished] when the finger lifts or
 * after a tap, so a caller can save once. A screen reader reads [description] and the value and
 * can raise and lower it; [valueDescription] is how it says the value ("14 sp") instead of a percentage.
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    description: String,
    modifier: Modifier = Modifier,
    valueDescription: String? = null,
    onValueChangeFinished: () -> Unit = {}
) {
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val fraction = SliderMath.fractionOf(value, range)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(IosSize.minTouch)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                valueDescription?.let { stateDescription = it }
                setProgress { target ->
                    change(target.coerceIn(range.start, range.endInclusive))
                    finished()
                    true
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbPx = with(density) { ThumbSize.toPx() }
        // The thumb's centre travels from half a thumb in to half a thumb short of the end.
        val valueAtX = { x: Float ->
            SliderMath.valueAt(SliderMath.fractionAt(x - thumbPx / 2, widthPx - thumbPx), range)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(IosSize.minTouch)
                .sliderGestures(widthPx, valueAtX, { change(it) }, { finished() })
        )
        Track(fraction)
        Box(
            Modifier
                .offset { IntOffset(((widthPx - thumbPx) * fraction).toInt(), 0) }
                .size(ThumbSize)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** The grey track and, over it, the tinted part up to the thumb. */
@Composable
private fun BoxWithConstraintsScope.Track(fraction: Float) {
    val colors = IosTheme.colors
    Box(Modifier.fillMaxWidth().height(TrackHeight).clip(CircleShape).background(colors.fill))
    Box(
        Modifier
            .width(ThumbSize / 2 + (maxWidth - ThumbSize) * fraction)
            .height(TrackHeight)
            .clip(CircleShape)
            .background(colors.tint)
    )
}

/** A tap or a horizontal drag at x reports the value there; lifting the finger reports the end. */
private fun Modifier.sliderGestures(
    widthPx: Float,
    valueAtX: (Float) -> Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
): Modifier = this
    .pointerInput(widthPx) {
        detectTapGestures { offset ->
            onChange(valueAtX(offset.x))
            onFinished()
        }
    }
    .pointerInput(widthPx) {
        detectHorizontalDragGestures(
            onDragStart = { offset -> onChange(valueAtX(offset.x)) },
            onDragEnd = onFinished,
            onDragCancel = onFinished
        ) { pointer, _ ->
            pointer.consume()
            onChange(valueAtX(pointer.position.x))
        }
    }
