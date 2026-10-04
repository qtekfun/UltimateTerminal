// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

private const val SPRING_DAMPING = 0.8f

/**
 * Two or more options in a grey track with a white thumb that slides to the chosen one. Each
 * option is a full 48 dp tall touch target even though the track is drawn at 32 dp.
 */
@Composable
fun IosSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(options.size >= 2) { "A segmented control needs at least two options" }
    Box(modifier.fillMaxWidth().height(IosSize.minTouch), contentAlignment = Alignment.Center) {
        Track(options.size, selectedIndex)
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                Segment(label, selected = index == selectedIndex, onClick = {
                    onSelect(index)
                }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    IosPressable(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().semantics { this.selected = selected },
        role = Role.Tab
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            IosText(
                text = label,
                style = IosTheme.typography.footnote.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The grey track, with the white thumb under the chosen segment springing to it. */
@Composable
private fun Track(count: Int, selectedIndex: Int) {
    val colors = IosTheme.colors
    val track = Modifier
        .fillMaxWidth()
        .height(IosSize.segmentedHeight)
        .clip(RoundedCornerShape(IosRadius.control))
        .background(colors.fill)
    BoxWithConstraints(track) {
        val segment = maxWidth / count
        val thumbX by animateDpAsState(
            targetValue = segment * selectedIndex,
            animationSpec = spring(
                dampingRatio = SPRING_DAMPING,
                stiffness = Spring.StiffnessMedium
            ),
            label = "segment"
        )
        val thumbShape = RoundedCornerShape(IosRadius.segment)
        Box(
            Modifier
                .offset { IntOffset(thumbX.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .padding(IosSpacing.xxs)
                .shadow(elevation = 1.dp, shape = thumbShape)
                .background(colors.cell, thumbShape)
        )
    }
}
