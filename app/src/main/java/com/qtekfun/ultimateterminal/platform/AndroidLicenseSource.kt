// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.content.Context
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.license.LicenseSource
import com.qtekfun.ultimateterminal.domain.license.LicenseTexts
import java.io.IOException
import java.io.InputStream

/**
 * Reads the bundled license texts from the APK. The `raw` resources are looked up through `R.raw`,
 * never by name at run time: a name lookup is discouraged by Lint and the resource shrinker may
 * rename what nothing references directly.
 */
class AndroidLicenseSource(private val context: Context) : LicenseSource {
    override fun read(name: String): String? = try {
        openStream(name)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: IOException) {
        null
    }

    private fun openStream(name: String): InputStream? = when {
        name == LicenseTexts.APACHE_RESOURCE ->
            context.resources.openRawResource(R.raw.third_party_notices_apache)

        name.startsWith(ASSETS_PREFIX) -> context.assets.open(name.removePrefix(ASSETS_PREFIX))

        else -> null
    }

    private companion object {
        const val ASSETS_PREFIX = "assets/"
    }
}
