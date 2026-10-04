// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputRouterTest {
    private val changes = mutableListOf<StickyState>()
    private val router = InputRouter(onStickyChanged = { changes += it })

    private fun extra(id: String) = checkNotNull(ExtraKeyCatalog.find(id))

    private fun encode(routed: RoutedInput): KeyOutput {
        val key = (routed as RoutedInput.Key).input
        return KeyEncoder.encode(key, cursorKeysApplication = false, keypadApplication = false)
    }

    @Test
    fun ordinaryKeysPassThroughUnchanged() {
        val input = KeyInput(KeyEvent.KEYCODE_A, 'a'.code)
        assertEquals(RoutedInput.Key(input), router.route(input))
        assertTrue(changes.isEmpty())
    }

    @Test
    fun anArmedCtrlMakesTheNextSoftKeyboardLetterAControlCharacter() {
        router.press(extra("ctrl"))
        val routed = router.routeText("c")
        assertEquals(KeyOutput.CodePoint(3, false), encode(routed))
        assertEquals(StickyState(), router.sticky)
    }

    @Test
    fun anArmedAltPrefixesEscape() {
        router.press(extra("alt"))
        assertEquals(KeyOutput.CodePoint('x'.code, true), encode(router.routeText("x")))
    }

    @Test
    fun aLockedModifierSurvivesEveryKey() {
        router.press(extra("ctrl"))
        router.press(extra("ctrl"))
        repeat(3) { router.routeText("d") }
        assertEquals(LatchState.LOCKED, router.sticky.ctrl)
        router.press(extra("ctrl"))
        assertEquals(LatchState.OFF, router.sticky.ctrl)
    }

    @Test
    fun theStickyModifiersAlsoApplyToHardwareKeysAndExtraKeys() {
        router.press(extra("ctrl"))
        val arrow = router.press(extra("right"))
        assertEquals(KeyOutput.Sequence("\u001b[1;5C"), encode(arrow))
        router.press(extra("alt"))
        val hardware = router.route(KeyInput(KeyEvent.KEYCODE_B, 'b'.code))
        assertEquals(KeyOutput.CodePoint('b'.code, true), encode(hardware))
    }

    @Test
    fun anExtraKeyTypesItsText() {
        assertEquals(RoutedInput.Text("-"), router.press(extra("dash")))
        router.press(extra("ctrl"))
        assertEquals(KeyOutput.CodePoint(0x1f, false), encode(router.press(extra("slash"))))
    }

    @Test
    fun longTextIsTypedAsIsAndSpendsAnArmedModifier() {
        router.press(extra("ctrl"))
        assertEquals(RoutedInput.Text("ls -l"), router.routeText("ls -l"))
        assertEquals(StickyState(), router.sticky)
    }

    @Test
    fun emptyTextIsIgnored() {
        assertEquals(RoutedInput.Ignored, router.routeText(""))
    }

    @Test
    fun aModifierKeyOnItsOwnIsIgnoredAndKeepsTheStickyState() {
        router.press(extra("ctrl"))
        listOf(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_CAPS_LOCK
        ).forEach { assertEquals(RoutedInput.Ignored, router.route(KeyInput(it, 0))) }
        assertEquals(LatchState.ARMED, router.sticky.ctrl)
    }

    @Test
    fun tappingAModifierReportsNothingToSend() {
        assertEquals(RoutedInput.Ignored, router.press(extra("ctrl")))
        assertEquals(listOf(StickyState().tap(StickyKey.CTRL)), changes)
    }

    @Test
    fun shortcutsAreRecognizedAndSpendTheStickyModifier() {
        val newTab = KeyInput(KeyEvent.KEYCODE_T, 't'.code, ctrl = true, shift = true)
        assertEquals(RoutedInput.Shortcut(AppShortcut.NewTab), router.route(newTab))
        router.press(extra("ctrl"))
        val viaSticky = router.route(KeyInput(KeyEvent.KEYCODE_TAB, 0))
        assertEquals(RoutedInput.Shortcut(AppShortcut.NextTab), viaSticky)
        assertEquals(StickyState(), router.sticky)
    }

    @Test
    fun reboundShortcutsTakeEffect() {
        router.shortcuts = ShortcutMap.parse("alt+z=copy").map
        val routed = router.route(KeyInput(KeyEvent.KEYCODE_Z, 'z'.code, alt = true))
        assertEquals(RoutedInput.Shortcut(AppShortcut.Copy), routed)
        val old = KeyInput(KeyEvent.KEYCODE_T, 't'.code, ctrl = true, shift = true)
        assertTrue(router.route(old) is RoutedInput.Key)
    }
}
