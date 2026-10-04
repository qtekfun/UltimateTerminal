// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import com.qtekfun.ultimateterminal.di.IoDispatcher
import com.qtekfun.ultimateterminal.domain.appearance.FontFileStore
import com.qtekfun.ultimateterminal.domain.appearance.FontProbe
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The fonts the user imported, in `files/fonts` of the app's private storage. A name that is not a
 * plain file name (a path, `..`) is refused, so nothing outside the folder can be touched.
 */
@Singleton
class AndroidFontStore @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FontFileStore {
    private val directory = File(context.filesDir, "fonts")

    override suspend fun write(fileName: String, bytes: ByteArray): Boolean =
        withContext(ioDispatcher) {
            val target = fileFor(fileName) ?: return@withContext false
            try {
                directory.mkdirs()
                target.writeBytes(bytes)
                true
            } catch (_: IOException) {
                false
            }
        }

    override suspend fun delete(fileName: String) {
        withContext(ioDispatcher) { fileFor(fileName)?.delete() }
    }

    override fun probe(fileName: String): FontProbe? = existing(fileName)?.let(::AndroidFontProbe)

    /** The loaded font in [fileName], or null if the file is gone or is not a font. */
    fun typefaceOf(fileName: String): Typeface? =
        existing(fileName)?.let { Typeface.Builder(it).build() }

    private fun existing(fileName: String): File? = fileFor(fileName)?.takeIf { it.isFile }

    private fun fileFor(fileName: String): File? = File(directory, fileName).takeIf {
        it.name == fileName && fileName != "." &&
            fileName != ".."
    }
}

/** Measures a font file with the platform's own text engine. */
internal class AndroidFontProbe(file: File) : FontProbe {
    private val typeface: Typeface? = Typeface.Builder(file).build()

    private val paint = Paint().apply {
        typeface = this@AndroidFontProbe.typeface
        textSize = MEASURE_SIZE
    }

    override fun loads(): Boolean = typeface != null

    override fun hasGlyphs(text: String): Boolean = paint.hasGlyph(text)

    override fun advance(text: String): Float = paint.measureText(text)

    private companion object {
        const val MEASURE_SIZE = 100f
    }
}
