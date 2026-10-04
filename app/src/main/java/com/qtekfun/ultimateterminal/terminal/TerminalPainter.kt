// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.withScale
import com.qtekfun.ultimateterminal.domain.terminal.CellAppearance
import com.qtekfun.ultimateterminal.domain.terminal.CellStyles
import com.qtekfun.ultimateterminal.domain.terminal.TerminalSelection
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalRow
import com.termux.terminal.TextStyle
import com.termux.terminal.WcWidth
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Draws a [TerminalEmulator] screen onto an Android [Canvas], one run of same-styled cells at a
 * time. It only reads the emulator; the cell-to-color rules live in `CellStyles`.
 *
 * Single-threaded (UI thread) and stateful during [draw], to avoid allocating on every frame.
 * Not validated on a device yet (see DECISIONS.md, T03).
 */
class TerminalPainter(typeface: Typeface, textSizePx: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = textSizePx
    }

    /** Width of one cell, taken from the font's advance for "X". */
    val cellWidth: Float = paint.measureText("X")
    private val ascent: Float = -paint.fontMetrics.ascent
    val cellHeight: Int = ceil(
        paint.fontMetrics.descent - paint.fontMetrics.ascent + paint.fontMetrics.leading
    ).toInt()

    /** A run of cells drawn together: same style, same cursor and selection state. */
    private class Run {
        var startColumn = 0
        var startIndex = 0
        var style = 0L
        var cursor = false
        var selected = false

        /** Plain ASCII runs are drawn in one call; anything else is drawn cell by cell. */
        var batchable = false
        var columns = 0
        var chars = 0

        fun start(column: Int, index: Int, style: Long) {
            startColumn = column
            startIndex = index
            this.style = style
            columns = 0
            chars = 0
        }

        fun continuedBy(style: Long, cursor: Boolean, selected: Boolean, batchable: Boolean) =
            chars > 0 && this.batchable && batchable &&
                this.style == style && this.cursor == cursor && this.selected == selected
    }

    private val run = Run()
    private lateinit var canvas: Canvas
    private lateinit var emulator: TerminalEmulator

    /**
     * Draws the rows `topRow until topRow + emulator.mRows` (rows above 0 are scrollback), with the
     * [selection] highlighted.
     */
    fun draw(
        canvas: Canvas,
        emulator: TerminalEmulator,
        topRow: Int,
        selection: TerminalSelection?
    ) {
        this.canvas = canvas
        this.emulator = emulator
        val palette = emulator.mColors.mCurrentColors
        val screenColor = if (emulator.isReverseVideo) {
            TextStyle.COLOR_INDEX_FOREGROUND
        } else {
            TextStyle.COLOR_INDEX_BACKGROUND
        }
        canvas.drawColor(palette[screenColor])

        val screen = emulator.screen
        val cursorVisible = emulator.shouldCursorBeVisible()
        for (screenRow in 0 until emulator.mRows) {
            val row = topRow + screenRow
            val line = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row))
            val cursorColumn = if (cursorVisible &&
                row == emulator.cursorRow
            ) {
                emulator.cursorCol
            } else {
                -1
            }
            drawRow(
                line,
                screenRow * cellHeight,
                cursorColumn,
                selection?.columnsOn(row, emulator.mColumns)
            )
        }
    }

    private fun drawRow(line: TerminalRow, top: Int, cursorColumn: Int, selected: IntRange?) {
        val text = line.mText
        var column = 0
        var index = 0
        run.start(0, 0, 0L)
        while (column < emulator.mColumns) {
            val high = Character.isHighSurrogate(text[index])
            val codePoint = if (high) {
                Character.toCodePoint(
                    text[index],
                    text[index + 1]
                )
            } else {
                text[index].code
            }
            val units = if (high) 2 else 1
            val width = WcWidth.width(codePoint)
            val cursor = cursorColumn == column || (width == 2 && cursorColumn == column + 1)
            val style = line.getStyle(column)
            val batchable = codePoint < ASCII_LIMIT
            val isSelected = selected != null && column in selected

            if (!run.continuedBy(style, cursor, isSelected, batchable)) {
                flush(text, top)
                run.start(column, index, style)
                run.cursor = cursor
                run.selected = isSelected
                run.batchable = batchable
            }
            val extra = combiningLength(text, index + units, line.spaceUsed)
            run.columns += width.coerceAtLeast(1)
            run.chars += units + extra
            column += width
            index += units + extra
            if (!batchable) flush(text, top)
        }
        flush(text, top)
    }

    /** Number of chars of combining marks starting at [from]: they belong to the cell before them. */
    private fun combiningLength(text: CharArray, from: Int, used: Int): Int {
        var length = 0
        while (from + length < used && WcWidth.width(text, from + length) <= 0) {
            length += if (Character.isHighSurrogate(text[from + length])) 2 else 1
        }
        return length
    }

    private fun flush(text: CharArray, top: Int) {
        if (run.chars == 0) return
        val palette = emulator.mColors.mCurrentColors
        val blockCursor =
            run.cursor && emulator.cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
        val reverse = emulator.isReverseVideo || blockCursor || run.selected
        val appearance = CellStyles.resolve(run.style, palette, reverse)

        val left = run.startColumn * cellWidth
        val right = left + run.columns * cellWidth
        val topPx = top.toFloat()

        if (appearance.background != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            paint.color = appearance.background
            canvas.drawRect(left, topPx, right, topPx + cellHeight, paint)
        }
        if (run.cursor) drawCursor(left, right, topPx)
        if (!appearance.invisible) drawText(text, left, right, topPx, appearance)
        run.chars = 0
        run.columns = 0
    }

    private fun drawCursor(left: Float, right: Float, top: Float) {
        val bottom = top + cellHeight
        paint.color = emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR]
        when (emulator.cursorStyle) {
            TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE ->
                canvas.drawRect(left, bottom - cellHeight / UNDERLINE_DIVISOR, right, bottom, paint)

            TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR ->
                canvas.drawRect(left, top, left + cellWidth / BAR_DIVISOR, bottom, paint)

            else -> canvas.drawRect(left, top, right, bottom, paint)
        }
    }

    private fun drawText(
        text: CharArray,
        left: Float,
        right: Float,
        top: Float,
        appearance: CellAppearance
    ) {
        paint.color = appearance.foreground
        paint.isFakeBoldText = appearance.bold
        paint.isUnderlineText = appearance.underline
        paint.isStrikeThruText = appearance.strikethrough
        paint.textSkewX = if (appearance.italic) ITALIC_SKEW else 0f

        val baseline = top + ascent
        val expected = right - left
        val measured = paint.measureText(text, run.startIndex, run.chars)
        // A glyph that does not measure as the cells it occupies (an emoji, a font fallback) is
        // squeezed into them so the grid stays aligned.
        val mismatch =
            !run.batchable && measured > 0f && abs(measured - expected) > expected * WIDTH_TOLERANCE
        if (mismatch) {
            canvas.withScale(expected / measured, 1f, left, baseline) {
                drawText(text, run.startIndex, run.chars, left, baseline, paint)
            }
        } else {
            canvas.drawText(text, run.startIndex, run.chars, left, baseline, paint)
        }
    }

    private companion object {
        const val ASCII_LIMIT = 0x80
        const val UNDERLINE_DIVISOR = 4f
        const val BAR_DIVISOR = 4f
        const val ITALIC_SKEW = -0.35f
        const val WIDTH_TOLERANCE = 0.01f
    }
}
