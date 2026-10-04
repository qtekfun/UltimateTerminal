// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.dao.SettingDao
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.terminal.FontZoom
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The keys of the `setting` table. They are part of the backup format: do not rename them. */
internal object SettingKeys {
    const val THEME_MODE = "theme_mode"
    const val OLED_BLACK = "oled_black"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val KEEP_AWAKE = "keep_awake"
    const val DEFAULT_SCROLLBACK_LINES = "default_scrollback_lines"
    const val TERMINAL_SCHEME = "terminal_scheme"
    const val TERMINAL_FONT_SIZE_SP = "terminal_font_size_sp"
    const val CUSTOM_SCHEMES = "custom_schemes"
}

class RoomSettingsRepository @Inject constructor(private val dao: SettingDao) : SettingsRepository {
    override fun observe(): Flow<AppSettings> = dao.observeAll().map { rows ->
        parse(rows.associate { it.key to it.value })
    }.distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = parse(dao.all().associate { it.key to it.value })
        dao.upsert(serialize(transform(current)))
    }

    /** A missing or unreadable value falls back to its default, so a damaged row never breaks the app. */
    private fun parse(values: Map<String, String>): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = values[SettingKeys.THEME_MODE]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
            oledBlack =
                values[SettingKeys.OLED_BLACK]?.toBooleanStrictOrNull() ?: defaults.oledBlack,
            dynamicColor = values[SettingKeys.DYNAMIC_COLOR]?.toBooleanStrictOrNull()
                ?: defaults.dynamicColor,
            keepAwake =
                values[SettingKeys.KEEP_AWAKE]?.toBooleanStrictOrNull() ?: defaults.keepAwake,
            defaultScrollbackLines = values[SettingKeys.DEFAULT_SCROLLBACK_LINES]
                ?.toIntOrNull()
                ?.takeIf { it in Profile.SCROLLBACK_RANGE }
                ?: defaults.defaultScrollbackLines,
            terminalSchemeId = values[SettingKeys.TERMINAL_SCHEME]
                ?.takeIf { it.isNotBlank() }
                ?: defaults.terminalSchemeId,
            terminalFontSizeSp = values[SettingKeys.TERMINAL_FONT_SIZE_SP]
                ?.toFloatOrNull()
                ?.takeIf { it.isFinite() }
                ?.coerceIn(FontZoom.MIN_SP, FontZoom.MAX_SP)
                ?: defaults.terminalFontSizeSp,
            customSchemes = values[SettingKeys.CUSTOM_SCHEMES]
                ?.let(SchemeCodec::decodeList)
                ?.take(SchemeCatalog.MAX_CUSTOM_SCHEMES)
                ?: defaults.customSchemes
        )
    }

    private fun serialize(settings: AppSettings) = listOf(
        SettingEntity(SettingKeys.THEME_MODE, settings.themeMode.name),
        SettingEntity(SettingKeys.OLED_BLACK, settings.oledBlack.toString()),
        SettingEntity(SettingKeys.DYNAMIC_COLOR, settings.dynamicColor.toString()),
        SettingEntity(SettingKeys.KEEP_AWAKE, settings.keepAwake.toString()),
        SettingEntity(
            SettingKeys.DEFAULT_SCROLLBACK_LINES,
            settings.defaultScrollbackLines.coerceIn(Profile.SCROLLBACK_RANGE).toString()
        ),
        SettingEntity(SettingKeys.TERMINAL_SCHEME, settings.terminalSchemeId),
        SettingEntity(
            SettingKeys.TERMINAL_FONT_SIZE_SP,
            settings.terminalFontSizeSp.coerceIn(FontZoom.MIN_SP, FontZoom.MAX_SP).toString()
        ),
        SettingEntity(SettingKeys.CUSTOM_SCHEMES, SchemeCodec.encodeList(settings.customSchemes))
    )
}
