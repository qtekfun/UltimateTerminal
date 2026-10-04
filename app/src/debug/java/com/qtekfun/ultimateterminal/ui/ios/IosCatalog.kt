// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.ThemeDecision

private val Looks = listOf(
    ThemeDecision(dark = false, oled = false),
    ThemeDecision(dark = true, oled = false),
    ThemeDecision(dark = true, oled = true)
)

/** The overlays the catalog can open. */
private enum class Overlay { NONE, ALERT, ACTIONS, SHEET }

/**
 * Every component of the iOS-style design on one scrolling screen, for looking at them on a device.
 * The first segment of the theme picker switches between light, dark and OLED; the row under it
 * cycles the terminal color scheme the tint is taken from.
 */
@Composable
fun IosCatalog(initialLook: Int = 0) {
    var look by rememberSaveable { mutableIntStateOf(initialLook) }
    var schemeIndex by rememberSaveable { mutableIntStateOf(-1) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = BuiltInSchemes.all.getOrNull(schemeIndex)

    IosTheme(Looks[look], scheme) {
        IosLargeTitleScreen(
            title = stringResource(R.string.catalog_title),
            leading = {
                IosBarButton(stringResource(R.string.catalog_done), onClick = {}, bold = true)
            },
            trailing = {
                Box {
                    IosBarIconButton(
                        IosGlyph.ELLIPSIS,
                        stringResource(
                            R.string.catalog_menu
                        ),
                        onClick = {
                            menuOpen =
                                true
                        }
                    )
                    CatalogMenu(menuOpen) { menuOpen = false }
                }
            }
        ) {
            themeSection(look, schemeIndex, onLook = { look = it }, onScheme = {
                schemeIndex =
                    if (schemeIndex + 1 >= BuiltInSchemes.all.size) -1 else schemeIndex + 1
            })
            rowsSection()
            controlsSection(onOpen = { overlay = it })
            scrollSection()
        }
        CatalogOverlays(overlay) { overlay = Overlay.NONE }
    }
}

private fun LazyListScope.themeSection(
    look: Int,
    schemeIndex: Int,
    onLook: (Int) -> Unit,
    onScheme: () -> Unit
) {
    item {
        IosSection(header = stringResource(R.string.catalog_section_theme)) {
            IosSegmentedControl(
                options = listOf(
                    stringResource(R.string.catalog_light),
                    stringResource(R.string.catalog_dark),
                    stringResource(R.string.catalog_oled)
                ),
                selectedIndex = look,
                onSelect = onLook,
                modifier = Modifier.padding(horizontal = IosSpacing.md)
            )
            IosListRow(
                title = stringResource(R.string.catalog_scheme),
                accessory = IosAccessory.Value(
                    BuiltInSchemes.all.getOrNull(schemeIndex)?.name
                        ?: stringResource(R.string.catalog_scheme_default),
                    chevron = true
                ),
                showSeparator = false,
                onClick = onScheme
            )
        }
    }
}

private fun LazyListScope.rowsSection() {
    item {
        var switch by remember { mutableStateOf(true) }
        IosSection(
            header = stringResource(R.string.catalog_section_rows),
            footer = stringResource(R.string.catalog_footer)
        ) {
            IosListRow(
                stringResource(
                    R.string.catalog_row_plain
                ),
                accessory = IosAccessory.Chevron,
                onClick = {
                }
            )
            IosListRow(
                title = stringResource(R.string.catalog_row_subtitle),
                subtitle = stringResource(R.string.catalog_row_subtitle_detail),
                glyph = IosGlyph.TERMINAL,
                accessory = IosAccessory.Chevron,
                onClick = {}
            )
            IosListRow(
                title = stringResource(R.string.catalog_row_value),
                accessory = IosAccessory.Value(
                    stringResource(R.string.catalog_row_value_text),
                    chevron = true
                ),
                glyph = IosGlyph.SETTINGS,
                onClick = {}
            )
            IosListRow(
                title = stringResource(R.string.catalog_row_switch),
                glyph = IosGlyph.KEY,
                accessory = IosAccessory.Toggle(switch) { switch = it }
            )
            IosListRow(
                stringResource(
                    R.string.catalog_row_check
                ),
                accessory = IosAccessory.Check,
                onClick = {
                }
            )
            IosListRow(
                title = stringResource(R.string.catalog_row_destructive),
                glyph = IosGlyph.TRASH,
                destructive = true,
                showSeparator = false,
                onClick = {}
            )
        }
    }
}

private fun LazyListScope.controlsSection(onOpen: (Overlay) -> Unit) {
    item {
        var segment by remember { mutableIntStateOf(1) }
        var query by remember { mutableStateOf("") }
        IosSection(header = stringResource(R.string.catalog_section_controls)) {
            IosSegmentedControl(
                options = listOf(
                    stringResource(R.string.catalog_segment_one),
                    stringResource(R.string.catalog_segment_two),
                    stringResource(R.string.catalog_segment_three)
                ),
                selectedIndex = segment,
                onSelect = { segment = it },
                modifier = Modifier.padding(horizontal = IosSpacing.md)
            )
            IosSearchField(
                query,
                {
                    query = it
                },
                stringResource(
                    R.string.catalog_search_placeholder
                ),
                Modifier.padding(horizontal = IosSpacing.md)
            )
            Box(Modifier.fillMaxWidth().padding(IosSpacing.md)) {
                IosButton(stringResource(R.string.catalog_button_filled), onClick = {
                }, glyph = IosGlyph.DOWNLOAD)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = IosSpacing.md)) {
                IosButton(stringResource(R.string.catalog_button_tinted), onClick = {
                }, style = IosButtonStyle.TINTED)
            }
            Box(
                Modifier.fillMaxWidth().padding(
                    horizontal = IosSpacing.md,
                    vertical = IosSpacing.sm
                )
            ) {
                IosButton(stringResource(R.string.catalog_button_plain), onClick = {
                }, style = IosButtonStyle.PLAIN)
            }
            Box(
                Modifier.fillMaxWidth().padding(
                    horizontal = IosSpacing.md
                ).padding(bottom = IosSpacing.md)
            ) {
                IosButton(stringResource(R.string.catalog_button_destructive), onClick = {
                }, style = IosButtonStyle.TINTED, destructive = true)
            }
        }
    }
    item {
        IosSection(header = stringResource(R.string.catalog_section_overlays)) {
            IosListRow(
                stringResource(
                    R.string.catalog_open_alert
                ),
                accessory = IosAccessory.Chevron,
                onClick = {
                    onOpen(Overlay.ALERT)
                }
            )
            IosListRow(
                stringResource(
                    R.string.catalog_open_action_sheet
                ),
                accessory = IosAccessory.Chevron,
                onClick = {
                    onOpen(Overlay.ACTIONS)
                }
            )
            IosListRow(
                title = stringResource(R.string.catalog_open_sheet),
                accessory = IosAccessory.Chevron,
                showSeparator = false,
                onClick = { onOpen(Overlay.SHEET) }
            )
        }
    }
}

private fun LazyListScope.scrollSection() {
    item {
        IosSection(
            header = stringResource(R.string.catalog_section_scroll),
            footer = stringResource(R.string.catalog_scroll_footer)
        ) {
            repeat(SCROLL_ROWS) { index ->
                IosListRow(
                    title = stringResource(R.string.catalog_scroll_row, index + 1),
                    accessory = IosAccessory.Chevron,
                    showSeparator = index < SCROLL_ROWS - 1,
                    onClick = {}
                )
            }
        }
    }
}

private const val SCROLL_ROWS = 12

@Composable
private fun CatalogMenu(expanded: Boolean, onDismiss: () -> Unit) {
    IosContextMenu(expanded, onDismiss) {
        IosMenuItem(
            stringResource(R.string.catalog_menu_copy),
            onClick = onDismiss,
            glyph = IosGlyph.COPY
        )
        IosMenuItem(
            stringResource(R.string.catalog_menu_download),
            onClick = onDismiss,
            glyph = IosGlyph.DOWNLOAD
        )
        IosMenuItem(
            stringResource(R.string.catalog_delete),
            onClick = onDismiss,
            glyph = IosGlyph.TRASH,
            destructive = true,
            showSeparator = false
        )
    }
}

@Composable
private fun CatalogOverlays(overlay: Overlay, onClose: () -> Unit) {
    when (overlay) {
        Overlay.NONE -> Unit

        Overlay.ALERT -> IosAlert(
            title = stringResource(R.string.catalog_alert_title),
            message = stringResource(R.string.catalog_alert_message),
            actions = listOf(
                IosAction(stringResource(R.string.catalog_cancel), IosActionRole.CANCEL),
                IosAction(stringResource(R.string.catalog_delete), IosActionRole.DESTRUCTIVE)
            ),
            onDismiss = onClose
        )

        Overlay.ACTIONS -> IosActionSheet(
            actions = listOf(
                IosAction(stringResource(R.string.catalog_menu_copy)),
                IosAction(stringResource(R.string.catalog_menu_download)),
                IosAction(stringResource(R.string.catalog_delete), IosActionRole.DESTRUCTIVE)
            ),
            cancelLabel = stringResource(R.string.catalog_cancel),
            onDismiss = onClose,
            title = stringResource(R.string.catalog_open_action_sheet)
        )

        Overlay.SHEET -> IosBottomSheet(onDismiss = onClose, initial = SheetDetent.MEDIUM) {
            IosText(stringResource(R.string.catalog_open_sheet), style = IosTheme.typography.title2)
            IosText(
                stringResource(R.string.catalog_sheet_body),
                modifier = Modifier.padding(top = IosSpacing.sm),
                color = IosTheme.colors.secondaryLabel
            )
        }
    }
}

@Preview(name = "Light", showBackground = true, heightDp = 900)
@Composable
private fun CatalogLight() = IosCatalog(initialLook = 0)

@Preview(name = "Dark", showBackground = true, heightDp = 900)
@Composable
private fun CatalogDark() = IosCatalog(initialLook = 1)

@Preview(name = "OLED", showBackground = true, heightDp = 900)
@Composable
private fun CatalogOled() = IosCatalog(initialLook = 2)
