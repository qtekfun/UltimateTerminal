// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.qtekfun.ultimateterminal.BuildConfig
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.settings.NoticeSection
import com.qtekfun.ultimateterminal.domain.settings.Notices
import com.qtekfun.ultimateterminal.domain.settings.SettingsPage
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LICENSE = "GPL-3.0-or-later"
private const val SOURCE_URL = "https://github.com/qtekfun/UltimateTerminal"
private const val NOTICES_ASSET = "THIRD_PARTY_NOTICES.md"

/** The version, the license, the credits and where the source is. */
@Composable
internal fun AboutPage(nav: PageNav) {
    val context = LocalContext.current
    val footer = stringResource(R.string.settings_about_footer)
    SettingsPage(stringResource(R.string.settings_section_about), nav.backLabel, nav.back) {
        section(footer = footer) {
            IosListRow(
                title = stringResource(R.string.settings_about_version),
                accessory = IosAccessory.Value(BuildConfig.VERSION_NAME)
            )
            IosListRow(
                title = stringResource(R.string.settings_about_license),
                accessory = IosAccessory.Value(LICENSE)
            )
            Link(R.string.settings_about_credits) { nav.open(SettingsPage.NOTICES) }
            Link(R.string.settings_about_source, last = true) { openSource(context) }
        }
    }
}

private fun openSource(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser installed: there is nowhere to open it.
    }
}

/** The credits of the third-party code and fonts, from `THIRD_PARTY_NOTICES.md`. */
@Composable
internal fun NoticesPage(nav: PageNav) {
    val context = LocalContext.current
    val notices by produceState<List<NoticeSection>?>(null) {
        value = withContext(Dispatchers.IO) { readNotices(context) }
    }
    val failed = stringResource(R.string.settings_credits_error)
    SettingsPage(stringResource(R.string.settings_about_credits), nav.backLabel, nav.back) {
        val sections = notices
        if (sections != null && sections.isEmpty()) {
            section { IosListRow(title = failed, showSeparator = false) }
        }
        sections.orEmpty().forEach { block ->
            section(header = block.title.ifEmpty { null }) {
                block.items.forEachIndexed { index, item ->
                    IosListRow(
                        title = item.title,
                        subtitle = item.detail,
                        showSeparator = index != block.items.lastIndex
                    )
                }
            }
        }
    }
}

private fun readNotices(context: Context): List<NoticeSection> = try {
    context.assets.open(NOTICES_ASSET).bufferedReader().use { Notices.parse(it.readText()) }
} catch (_: IOException) {
    emptyList()
}
