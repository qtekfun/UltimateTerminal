// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.dao.SettingDao
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.domain.appearance.ChromeStyle
import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.ExtraKeyStyle
import com.qtekfun.ultimateterminal.domain.appearance.FontCatalog
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.session.SidebarMode
import com.qtekfun.ultimateterminal.domain.settings.DnsServers
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.FontZoom
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
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
    const val SHARED_STORAGE = "shared_storage"
    const val PROOT_COMPATIBILITY_MODE = "proot_compatibility_mode"
    const val DEFAULT_SCROLLBACK_LINES = "default_scrollback_lines"
    const val TERMINAL_SCHEME = "terminal_scheme"
    const val TERMINAL_FONT_SIZE_SP = "terminal_font_size_sp"
    const val CUSTOM_SCHEMES = "custom_schemes"
    const val CUSTOM_FONTS = "custom_fonts"
    const val FONT_ID = "appearance_font_id"
    const val LINE_SPACING = "appearance_line_spacing"
    const val LETTER_SPACING = "appearance_letter_spacing"
    const val MARGIN_DP = "appearance_margin_dp"
    const val CURSOR_SHAPE = "appearance_cursor_shape"
    const val CURSOR_BLINK = "appearance_cursor_blink"
    const val CHROME_STYLE = "appearance_chrome_style"
    const val CORNER_DP = "appearance_corner_dp"
    const val EXTRA_KEY_STYLE = "appearance_extra_key_style"
    const val EXTRA_KEYS = "extra_keys"
    const val DNS_FALLBACK = "dns_fallback"
    const val SHORTCUTS = "shortcuts"
    const val SIDEBAR_MODE = "sidebar_mode"
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
            sharedStorage = values[SettingKeys.SHARED_STORAGE]?.toBooleanStrictOrNull()
                ?: defaults.sharedStorage,
            prootCompatibilityMode = values[SettingKeys.PROOT_COMPATIBILITY_MODE]
                ?.toBooleanStrictOrNull() ?: defaults.prootCompatibilityMode,
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
                ?: defaults.customSchemes,
            appearance = parseAppearance(values),
            customFonts = values[SettingKeys.CUSTOM_FONTS]
                ?.let(FontCatalog::decodeList)
                ?.take(FontCatalog.MAX_CUSTOM_FONTS)
                ?: defaults.customFonts
        ).withKeysAndDns(values)
    }

    /**
     * The extra keys, the DNS servers and the shortcuts; a missing value keeps its default. The
     * shortcuts are read leniently: a line that makes no sense is skipped, the rest is kept.
     */
    private fun AppSettings.withKeysAndDns(values: Map<String, String>): AppSettings = copy(
        extraKeys = values[SettingKeys.EXTRA_KEYS]
            ?.let { ExtraKeysConfig.parse(it).first }
            ?: extraKeys,
        dnsFallbackServers = values[SettingKeys.DNS_FALLBACK]
            ?.let { DnsServers.parse(it).servers }
            ?: dnsFallbackServers,
        shortcuts = values[SettingKeys.SHORTCUTS]?.let { ShortcutMap.parse(it).map } ?: shortcuts,
        sidebarMode = values[SettingKeys.SIDEBAR_MODE]?.let(SidebarMode::parse) ?: sidebarMode
    )

    /** Each value that is missing or unreadable keeps its default; numbers are clamped into range. */
    private fun parseAppearance(values: Map<String, String>): TerminalAppearance {
        val defaults = TerminalAppearance()
        return TerminalAppearance(
            fontId = values[SettingKeys.FONT_ID]?.takeIf { it.isNotBlank() } ?: defaults.fontId,
            lineSpacing = values[SettingKeys.LINE_SPACING]?.toFloatOrNull()
                ?: defaults.lineSpacing,
            letterSpacing = values[SettingKeys.LETTER_SPACING]?.toFloatOrNull()
                ?: defaults.letterSpacing,
            marginDp = values[SettingKeys.MARGIN_DP]?.toIntOrNull() ?: defaults.marginDp,
            cursorShape = values[SettingKeys.CURSOR_SHAPE]
                ?.let { name -> CursorShape.entries.firstOrNull { it.name == name } }
                ?: defaults.cursorShape,
            cursorBlink = values[SettingKeys.CURSOR_BLINK]?.toBooleanStrictOrNull()
                ?: defaults.cursorBlink,
            chromeStyle = values[SettingKeys.CHROME_STYLE]
                ?.let { name -> ChromeStyle.entries.firstOrNull { it.name == name } }
                ?: defaults.chromeStyle,
            cornerRadiusDp = values[SettingKeys.CORNER_DP]?.toIntOrNull()
                ?: defaults.cornerRadiusDp,
            extraKeyStyle = ExtraKeyStyle.parse(values[SettingKeys.EXTRA_KEY_STYLE])
        ).sanitized()
    }

    private fun serialize(settings: AppSettings) = listOf(
        SettingEntity(SettingKeys.THEME_MODE, settings.themeMode.name),
        SettingEntity(SettingKeys.OLED_BLACK, settings.oledBlack.toString()),
        SettingEntity(SettingKeys.DYNAMIC_COLOR, settings.dynamicColor.toString()),
        SettingEntity(SettingKeys.KEEP_AWAKE, settings.keepAwake.toString()),
        SettingEntity(SettingKeys.SHARED_STORAGE, settings.sharedStorage.toString()),
        SettingEntity(
            SettingKeys.PROOT_COMPATIBILITY_MODE,
            settings.prootCompatibilityMode.toString()
        ),
        SettingEntity(
            SettingKeys.DEFAULT_SCROLLBACK_LINES,
            settings.defaultScrollbackLines.coerceIn(Profile.SCROLLBACK_RANGE).toString()
        ),
        SettingEntity(SettingKeys.TERMINAL_SCHEME, settings.terminalSchemeId),
        SettingEntity(
            SettingKeys.TERMINAL_FONT_SIZE_SP,
            settings.terminalFontSizeSp.coerceIn(FontZoom.MIN_SP, FontZoom.MAX_SP).toString()
        ),
        SettingEntity(SettingKeys.CUSTOM_SCHEMES, SchemeCodec.encodeList(settings.customSchemes)),
        SettingEntity(SettingKeys.CUSTOM_FONTS, FontCatalog.encodeList(settings.customFonts)),
        SettingEntity(SettingKeys.EXTRA_KEYS, settings.extraKeys.serialize()),
        SettingEntity(SettingKeys.DNS_FALLBACK, DnsServers.format(settings.dnsFallbackServers)),
        SettingEntity(SettingKeys.SHORTCUTS, settings.shortcuts.serialize()),
        SettingEntity(SettingKeys.SIDEBAR_MODE, settings.sidebarMode.name)
    ) + serializeAppearance(settings.appearance.sanitized())

    private fun serializeAppearance(appearance: TerminalAppearance) = listOf(
        SettingEntity(SettingKeys.FONT_ID, appearance.fontId),
        SettingEntity(SettingKeys.LINE_SPACING, appearance.lineSpacing.toString()),
        SettingEntity(SettingKeys.LETTER_SPACING, appearance.letterSpacing.toString()),
        SettingEntity(SettingKeys.MARGIN_DP, appearance.marginDp.toString()),
        SettingEntity(SettingKeys.CURSOR_SHAPE, appearance.cursorShape.name),
        SettingEntity(SettingKeys.CURSOR_BLINK, appearance.cursorBlink.toString()),
        SettingEntity(SettingKeys.CHROME_STYLE, appearance.chromeStyle.name),
        SettingEntity(SettingKeys.CORNER_DP, appearance.cornerRadiusDp.toString()),
        SettingEntity(SettingKeys.EXTRA_KEY_STYLE, appearance.extraKeyStyle.name)
    )
}
