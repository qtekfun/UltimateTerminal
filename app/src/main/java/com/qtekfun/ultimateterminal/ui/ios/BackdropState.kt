// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.domain.ios.BarStyle

/**
 * What a translucent bar needs to blur the content that scrolls under it: the content is recorded
 * once into [source], and the bar draws a blurred copy of the part of it that lies behind the bar.
 * The blur is a RenderEffect, from Android 12; before that the bar is almost solid ([BarStyle]).
 */
class BackdropState internal constructor(
    internal val source: GraphicsLayer,
    internal val blurred: GraphicsLayer
) {
    internal var sourceOrigin by mutableStateOf(Offset.Zero)
    internal var barOrigin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdropState(): BackdropState {
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    return remember(source, blurred) { BackdropState(source, blurred) }
}

/** Marks the content that scrolls under the bar: it is drawn as usual and also recorded for the blur. */
fun Modifier.backdropSource(state: BackdropState): Modifier = this
    .onGloballyPositioned { state.sourceOrigin = it.positionInRoot() }
    .drawWithContent {
        val area = IntSize(size.width.toInt(), size.height.toInt())
        state.source.record(this, layoutDirection, area) { this@drawWithContent.drawContent() }
        drawLayer(state.source)
    }

/**
 * The background of a bar over [state]'s content: a blurred copy of what is behind it, tinted with
 * [tint] at the opacity of [style]. [visible], 0 to 1, fades it in as the content moves under it.
 */
fun Modifier.backdropBar(
    state: BackdropState,
    style: BarStyle,
    tint: Color,
    visible: Float = 1f
): Modifier = this
    .onGloballyPositioned { state.barOrigin = it.positionInRoot() }
    .drawBehind {
        if (visible <= 0f) return@drawBehind
        if (style.blurs && Build.VERSION.SDK_INT >= BarStyle.FIRST_BLUR_SDK) {
            val area = IntSize(size.width.toInt(), size.height.toInt())
            val radius = style.blurRadiusDp.dp.toPx()
            val behind = state.barOrigin - state.sourceOrigin
            state.blurred.record(this, layoutDirection, area) {
                translate(-behind.x, -behind.y) { drawLayer(state.source) }
            }
            state.blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            state.blurred.alpha = visible
            drawLayer(state.blurred)
        }
        drawRect(tint.copy(alpha = style.tintAlpha * visible))
    }
