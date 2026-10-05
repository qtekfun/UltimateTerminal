// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.session.DistroOption
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.TabBarPlacement
import com.qtekfun.ultimateterminal.domain.session.TabItem
import com.qtekfun.ultimateterminal.domain.session.TabName
import com.qtekfun.ultimateterminal.domain.session.TabSwitch
import com.qtekfun.ultimateterminal.domain.session.TabsController
import com.qtekfun.ultimateterminal.domain.session.dropIndex
import com.qtekfun.ultimateterminal.domain.session.tabNames
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosContextMenu
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosIcon
import com.qtekfun.ultimateterminal.ui.ios.IosMenuItem
import com.qtekfun.ultimateterminal.ui.ios.IosSize
import com.qtekfun.ultimateterminal.ui.ios.IosText
import com.qtekfun.ultimateterminal.ui.ios.IosTheme
import com.qtekfun.ultimateterminal.ui.settings.SettingsButton

/** Height of the bar when it runs along the top; also the minimum touch size (SPEC §6). */
internal val TabBarHeight = 48.dp

/** Width of the bar when it is a column on a wide window. */
internal val TabBarSideWidth = 192.dp

private val TouchSize = 48.dp
private val TabMinWidth = 96.dp
private val TabMaxWidth = 200.dp
private val TabTextPadding = 14.dp
private val PillInset = 6.dp
private val BarPadding = 4.dp
private const val INACTIVE_TAB_ALPHA = 0.72f
private const val ENDED_TAB_ALPHA = 0.6f

/**
 * The tabs, drawn as capsules in the colors of the scheme: a row on narrow windows and a column on
 * wide ones. A tap selects, a double tap renames, a long press followed by a drag reorders, and the
 * active tab has a menu to rename and close it (every tab offers the same as screen-reader
 * actions). Which tab takes what is decided in the domain ([TabsController]); this only draws and
 * reports touches. Not validated on a device yet (see DECISIONS.md, T09 and T22b).
 */
@Composable
fun TabBar(
    tabs: TabsController,
    placement: TabBarPlacement,
    links: TabBarLinks,
    modifier: Modifier = Modifier
) {
    val items by tabs.tabs.collectAsStateWithLifecycle()
    val choices by tabs.distroChoices.collectAsStateWithLifecycle()
    val closing by tabs.closeConfirmation.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<TabItem?>(null) }
    val drag = remember { TabDrag() }
    val vertical = placement == TabBarPlacement.Side
    val names = remember(items) { tabNames(items) }

    TabStrip(
        vertical = vertical,
        modifier = modifier,
        tabList = {
            items.forEachIndexed { index, item ->
                val actions = TabChipActions(
                    onSelect = { tabs.switchTo(TabSwitch.ById(item.id)) },
                    onRename = { renaming = item },
                    onClose = { tabs.requestClose(item.id) },
                    onMove = { step -> tabs.move(item.id, item.position - 1 + step) },
                    onDrop = { offset ->
                        tabs.move(item.id, dropTarget(items, item.id, offset, drag))
                    }
                )
                TabChip(
                    item,
                    tabNameText(names[index]),
                    TabChipBar(items.size, vertical, drag),
                    actions
                )
            }
        },
        // At the end of the bar, both of them: the new tab and, always in sight, the settings.
        newTab = {
            NewTabButton(
                choices,
                onNewTab = tabs::newTab,
                onNewTabIn = { tabs.newTabIn(it) },
                links = links
            )
            SettingsButton(links.screens.openSettings)
        }
    )

    renaming?.let { item ->
        RenamePrompt(
            current = item.title.orEmpty(),
            onSave = {
                tabs.rename(item.id, it)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }
    if (closing != null) {
        CloseConfirm(onConfirm = tabs::confirmClose, onDismiss = tabs::dismissClose)
    }
}

/** The position a tab dragged by [offset] lands on, from the measured size of every tab. */
private fun dropTarget(items: List<TabItem>, id: SessionId, offset: Float, drag: TabDrag): Int {
    val from = items.indexOfFirst { it.id == id }
    return if (from < 0) 0 else dropIndex(from, offset, items.map { drag.sizes[it.id] ?: 1f })
}

/** The bar's layout: the tabs scroll, the "new tab" button stays at the end. */
@Composable
private fun TabStrip(
    vertical: Boolean,
    modifier: Modifier,
    tabList: @Composable () -> Unit,
    newTab: @Composable () -> Unit
) {
    val description = stringResource(R.string.tab_bar_description)
    val chrome = currentChrome()
    val hairline = IosSize.hairline
    val bar = modifier
        .background(chrome.surface)
        .drawBehind {
            // A hairline between the bar and the terminal, as iOS bars have.
            val width = hairline.toPx()
            if (vertical) {
                drawLine(
                    chrome.outline,
                    Offset(size.width, 0f),
                    Offset(size.width, size.height),
                    width
                )
            } else {
                drawLine(
                    chrome.outline,
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                    width
                )
            }
        }
        .semantics { contentDescription = description }
    if (vertical) {
        Column(bar.padding(BarPadding)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { tabList() }
            newTab()
        }
    } else {
        Row(bar.padding(horizontal = BarPadding), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) { tabList() }
            newTab()
        }
    }
}

/** The drag in progress, shared by the tabs so only one moves at a time. */
private class TabDrag {
    var id by mutableStateOf<SessionId?>(null)
    var offsetPx by mutableFloatStateOf(0f)

    /** The size of each tab along the bar, measured as they are laid out. */
    val sizes = mutableStateMapOf<SessionId, Float>()

    fun reset() {
        id = null
        offsetPx = 0f
    }
}

/** What the "+" menu opens besides new tabs: other screens, and the split of the focused pane. */
class TabBarLinks(val screens: ScreenLinks, val splitRight: () -> Unit, val splitDown: () -> Unit)

/** What touching one tab does. */
private class TabChipActions(
    val onSelect: () -> Unit,
    val onRename: () -> Unit,
    val onClose: () -> Unit,
    val onMove: (step: Int) -> Unit,
    val onDrop: (Float) -> Unit
)

/** What a tab needs to know about the bar it sits in. */
private class TabChipBar(val count: Int, val vertical: Boolean, val drag: TabDrag)

/** The texts a screen reader hears for a tab, and for what it can do with it. */
private class TabSpeech(
    val description: String,
    val renameLabel: String,
    val closeLabel: String,
    val selectLabel: String,
    val moveEarlier: String?,
    val moveLater: String?
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabChip(item: TabItem, name: String, bar: TabChipBar, actions: TabChipActions) {
    val state = stringResource(
        if (item.running) R.string.tab_state_running else R.string.tab_state_ended
    )
    val speech = TabSpeech(
        stringResource(R.string.tab_description, name, item.position, bar.count, state),
        stringResource(R.string.tab_rename),
        stringResource(R.string.tab_close),
        stringResource(R.string.tab_select),
        // Dragging is the only touch way to reorder, so a screen reader gets the same as actions.
        if (item.position > 1) stringResource(R.string.tab_move_earlier) else null,
        if (item.position < bar.count) stringResource(R.string.tab_move_later) else null
    )
    val dragging = bar.drag.id == item.id
    // The active tab grows when its "..." button appears, and it may then end under the "+" or
    // past the edge: once the new size is laid out, scroll the whole tab into view.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(item.active, bar.count) {
        if (item.active) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    val chip = if (bar.vertical) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.widthIn(min = TabMinWidth, max = TabMaxWidth)
    }
    // The whole 48 dp is the touch target; only the capsule inside it is colored.
    Row(
        chip
            .heightIn(min = TouchSize)
            .bringIntoViewRequester(requester)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                if (dragging && bar.vertical) translationY = bar.drag.offsetPx
                if (dragging && !bar.vertical) translationX = bar.drag.offsetPx
            }
            .onSizeChanged {
                bar.drag.sizes[item.id] = (if (bar.vertical) it.height else it.width).toFloat()
            }
            .capsule(if (item.active) currentChrome().selected else Color.Transparent, PillInset)
            .tabSemantics(item.active, speech, actions)
            .tabGestures(item.id, bar.vertical, bar.drag, actions),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabLabel(item, name, Modifier.weight(1f, fill = false))
        if (item.active) TabMenu(name, actions)
    }
}

/** The name of a tab: bolder when it is the active one, fainter when it ended or is not active. */
@Composable
private fun TabLabel(item: TabItem, name: String, modifier: Modifier) {
    val alpha = when {
        !item.running -> ENDED_TAB_ALPHA
        item.active -> 1f
        else -> INACTIVE_TAB_ALPHA
    }
    IosText(
        name,
        modifier = modifier.padding(
            start = TabTextPadding,
            end = if (item.active) 0.dp else TabTextPadding
        ),
        maxLines = 1,
        style = IosTheme.typography.subheadline.copy(
            fontWeight = if (item.active) FontWeight.SemiBold else null
        ),
        color = currentChrome().onSurface.copy(alpha = alpha)
    )
}

/** What a screen reader says about a tab, and the actions it offers on every one of them. */
private fun Modifier.tabSemantics(
    active: Boolean,
    speech: TabSpeech,
    actions: TabChipActions
): Modifier = semantics(mergeDescendants = true) {
    contentDescription = speech.description
    selected = active
    role = Role.Tab
    // The tap is a raw pointer gesture, which a screen reader cannot trigger: give it a click.
    onClick(label = speech.selectLabel) {
        actions.onSelect()
        true
    }
    customActions = buildList {
        add(
            CustomAccessibilityAction(speech.renameLabel) {
                actions.onRename()
                true
            }
        )
        add(
            CustomAccessibilityAction(speech.closeLabel) {
                actions.onClose()
                true
            }
        )
        speech.moveEarlier?.let {
            add(CustomAccessibilityAction(it) { actions.onMove(-1).let { true } })
        }
        speech.moveLater?.let {
            add(CustomAccessibilityAction(it) { actions.onMove(1).let { true } })
        }
    }
}

/** Fills a capsule inside the element, [inset] short of its top and bottom, as a pill-shaped tab. */
private fun Modifier.capsule(color: Color, inset: Dp): Modifier = drawBehind {
    if (color.alpha > 0f) {
        val top = inset.toPx()
        val height = size.height - 2 * top
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, top),
            size = Size(size.width, height),
            cornerRadius = CornerRadius(height / 2f)
        )
    }
}

/** Tap selects, double tap renames, and a long press followed by a drag moves the tab. */
private fun Modifier.tabGestures(
    id: SessionId,
    vertical: Boolean,
    drag: TabDrag,
    actions: TabChipActions
): Modifier = this
    .pointerInput(id) {
        detectTapGestures(onTap = { actions.onSelect() }, onDoubleTap = { actions.onRename() })
    }
    .pointerInput(id, vertical) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                drag.id = id
                drag.offsetPx = 0f
            },
            onDrag = { change, amount ->
                change.consume()
                drag.offsetPx += if (vertical) amount.y else amount.x
            },
            onDragEnd = {
                actions.onDrop(drag.offsetPx)
                drag.reset()
            },
            onDragCancel = { drag.reset() }
        )
    }

/** The "more" button of the active tab, with its rename and close entries. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabMenu(name: String, actions: TabChipActions) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.tab_menu, name)
    Box(
        Modifier
            .size(TouchSize)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .combinedClickable(onClick = { open = true }),
        contentAlignment = Alignment.Center
    ) {
        IosIcon(IosGlyph.ELLIPSIS, null, tint = currentChrome().onSurface, size = MenuIconSize)
        IosContextMenu(expanded = open, onDismiss = { open = false }) {
            IosMenuItem(
                label = stringResource(R.string.tab_rename),
                onClick = {
                    open = false
                    actions.onRename()
                }
            )
            IosMenuItem(
                label = stringResource(R.string.tab_close),
                onClick = {
                    open = false
                    actions.onClose()
                },
                glyph = IosGlyph.CLOSE,
                destructive = true,
                showSeparator = false
            )
        }
    }
}

private val MenuIconSize = 20.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewTabButton(
    choices: List<DistroOption>,
    onNewTab: () -> Unit,
    onNewTabIn: (Long?) -> Unit,
    links: TabBarLinks
) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = stringResource(R.string.tab_new)
    val chooseLabel = stringResource(R.string.tab_new_choose)
    Box(
        Modifier
            .size(TouchSize)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .combinedClickable(
                onClickLabel = label,
                onClick = onNewTab,
                onLongClickLabel = chooseLabel,
                onLongClick = { menuOpen = true }
            ),
        contentAlignment = Alignment.Center
    ) {
        IosIcon(IosGlyph.PLUS, null, tint = IosTheme.colors.tint)
        NewTabMenu(menuOpen, { menuOpen = false }, choices, onNewTabIn, links)
    }
}
