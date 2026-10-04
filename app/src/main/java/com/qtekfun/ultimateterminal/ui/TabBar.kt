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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.session.DistroOption
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.TabBarPlacement
import com.qtekfun.ultimateterminal.domain.session.TabItem
import com.qtekfun.ultimateterminal.domain.session.TabSwitch
import com.qtekfun.ultimateterminal.domain.session.TabTitle
import com.qtekfun.ultimateterminal.domain.session.TabsController
import com.qtekfun.ultimateterminal.domain.session.dropIndex

/** Height of the bar when it runs along the top; also the minimum touch size (SPEC §6). */
internal val TabBarHeight = 48.dp

/** Width of the bar when it is a column on a wide window. */
internal val TabBarSideWidth = 192.dp

private val TouchSize = 48.dp
private val TabMinWidth = 96.dp
private val TabMaxWidth = 200.dp
private val TabTextPadding = 12.dp
private const val MENU_ICON_ALPHA = 0.8f
private const val ENDED_TAB_ALPHA = 0.6f

/**
 * The tabs: a row on narrow windows and a column on wide ones. A tap selects, a double tap or the
 * menu renames, a long press followed by a drag reorders, and the menu closes. Which tab takes
 * what is decided in the domain ([TabsController]); this only draws and reports touches. Not
 * validated on a device yet (see DECISIONS.md, T09).
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

    TabStrip(
        vertical = vertical,
        modifier = modifier,
        tabList = {
            items.forEach { item ->
                val actions = TabChipActions(
                    onSelect = { tabs.switchTo(TabSwitch.ById(item.id)) },
                    onRename = { renaming = item },
                    onClose = { tabs.requestClose(item.id) },
                    onDrop = { offset ->
                        tabs.move(item.id, dropTarget(items, item.id, offset, drag))
                    }
                )
                TabChip(item, items.size, vertical, drag, actions)
            }
        },
        newTab = {
            NewTabButton(
                choices,
                onNewTab = tabs::newTab,
                onNewTabIn = { tabs.newTabIn(it) },
                links = links
            )
        }
    )

    renaming?.let { item ->
        RenameDialog(
            current = item.title.orEmpty(),
            onSave = {
                tabs.rename(item.id, it)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }
    if (closing != null) {
        CloseConfirmDialog(onConfirm = tabs::confirmClose, onDismiss = tabs::dismissClose)
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
    val bar = modifier
        .background(currentChrome().surface)
        .semantics { contentDescription = description }
    if (vertical) {
        Column(bar) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { tabList() }
            newTab()
        }
    } else {
        Row(bar, verticalAlignment = Alignment.CenterVertically) {
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
class TabBarLinks(
    val openDistros: () -> Unit,
    val openSsh: () -> Unit,
    val openAppearance: () -> Unit,
    val splitRight: () -> Unit,
    val splitDown: () -> Unit
)

/** What touching one tab does. */
private class TabChipActions(
    val onSelect: () -> Unit,
    val onRename: () -> Unit,
    val onClose: () -> Unit,
    val onDrop: (Float) -> Unit
)

@Composable
private fun TabChip(
    item: TabItem,
    count: Int,
    vertical: Boolean,
    drag: TabDrag,
    actions: TabChipActions
) {
    val name = item.title ?: stringResource(R.string.tab_default_title, item.id.value)
    val state = stringResource(
        if (item.running) R.string.tab_state_running else R.string.tab_state_ended
    )
    val description = stringResource(R.string.tab_description, name, item.position, count, state)
    val dragging = drag.id == item.id
    val chrome = currentChrome()
    val chip = if (vertical) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.widthIn(min = TabMinWidth, max = TabMaxWidth)
    }
    Row(
        chip
            .heightIn(min = TouchSize)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                if (dragging && vertical) translationY = drag.offsetPx
                if (dragging && !vertical) translationX = drag.offsetPx
            }
            .onSizeChanged {
                drag.sizes[item.id] = (if (vertical) it.height else it.width).toFloat()
            }
            .background(
                if (item.active) chrome.selected else chrome.surface,
                RoundedCornerShape(chrome.corner)
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = item.active
                role = Role.Tab
            }
            .tabGestures(item.id, vertical, drag, actions),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val textAlpha = if (item.running) 1f else ENDED_TAB_ALPHA
        Text(
            name,
            modifier = Modifier.weight(1f, fill = false).padding(start = TabTextPadding),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = chrome.onSurface.copy(alpha = textAlpha)
        )
        TabMenu(name, actions)
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

/** The "more" button of a tab, with its rename and close entries. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabMenu(name: String, actions: TabChipActions) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.tab_menu, name)
    Box(
        Modifier
            .size(TouchSize)
            .semantics { contentDescription = label }
            .combinedClickable(onClick = { open = true }),
        contentAlignment = Alignment.Center
    ) {
        Text("⋮", modifier = Modifier.alpha(MENU_ICON_ALPHA), color = currentChrome().onSurface)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_rename)) },
                onClick = {
                    open = false
                    actions.onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_close)) },
                onClick = {
                    open = false
                    actions.onClose()
                }
            )
        }
    }
}

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
            .semantics { contentDescription = label }
            .combinedClickable(
                onClickLabel = label,
                onClick = onNewTab,
                onLongClickLabel = chooseLabel,
                onLongClick = { menuOpen = true }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("+", style = MaterialTheme.typography.titleLarge, color = currentChrome().onSurface)
        NewTabMenu(menuOpen, { menuOpen = false }, choices, onNewTabIn, links)
    }
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(TabTitle.MAX_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.tab_rename_label)) },
                supportingText = { Text(stringResource(R.string.tab_rename_hint)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.tab_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tab_cancel)) }
        }
    )
}

@Composable
private fun CloseConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_close_title)) },
        text = { Text(stringResource(R.string.tab_close_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.tab_close_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tab_cancel)) }
        }
    )
}
