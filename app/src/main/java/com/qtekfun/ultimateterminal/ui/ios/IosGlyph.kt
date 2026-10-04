// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.qtekfun.ultimateterminal.R

/** The icons of the iOS-style screens: Lucide (ISC), not SF Symbols, which Apple does not license for this. */
enum class IosGlyph(@DrawableRes val resId: Int) {
    CHECK(R.drawable.ic_ios_check),
    CHEVRON_DOWN(R.drawable.ic_ios_chevron_down),
    CHEVRON_LEFT(R.drawable.ic_ios_chevron_left),
    CHEVRON_RIGHT(R.drawable.ic_ios_chevron_right),
    COPY(R.drawable.ic_ios_copy),
    DOWNLOAD(R.drawable.ic_ios_download),
    ELLIPSIS(R.drawable.ic_ios_ellipsis),
    FOLDER(R.drawable.ic_ios_folder),
    INFO(R.drawable.ic_ios_info),
    KEY(R.drawable.ic_ios_key_round),
    PLUS(R.drawable.ic_ios_plus),
    SEARCH(R.drawable.ic_ios_search),
    SETTINGS(R.drawable.ic_ios_settings),
    TERMINAL(R.drawable.ic_ios_terminal),
    TRASH(R.drawable.ic_ios_trash),
    CLOSE(R.drawable.ic_ios_x)
}

/** A [glyph] drawn in [tint]. Pass a [contentDescription] unless the icon only decorates a labeled control. */
@Composable
fun IosIcon(
    glyph: IosGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = IosTheme.colors.tint,
    size: Dp = IosSize.icon
) {
    Image(
        painter = painterResource(glyph.resId),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
}
