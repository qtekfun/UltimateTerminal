// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import com.qtekfun.ultimateterminal.domain.language.AppLanguage
import java.util.Locale

/**
 * The per-app language. From Android 13 the system owns it (`LocaleManager`, also reachable from the
 * system settings, which `locales_config.xml` enables). Before that the choice lives in a small
 * preference file and is applied when an activity's context is built.
 */
object AppLocale {
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    fun current(context: Context): AppLanguage = if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.TIRAMISU
    ) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        AppLanguage.fromTag(if (locales.isEmpty) null else locales[0].toLanguageTag())
    } else {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        AppLanguage.fromTag(prefs.getString(KEY, null))
    }

    /** Stores [language]. Before Android 13 the caller recreates the activity to see it. */
    fun set(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.SYSTEM) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(language.tag)
                }
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY, language.tag)
            }
        }
    }

    /** A context in the chosen language for `attachBaseContext` (the system does it from 13). */
    fun wrap(base: Context): Context {
        val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AppLanguage.SYSTEM
        } else {
            current(base)
        }
        return if (language == AppLanguage.SYSTEM) base else base.inLanguage(language)
    }

    private fun Context.inLanguage(language: AppLanguage): Context {
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        return createConfigurationContext(configuration)
    }
}
