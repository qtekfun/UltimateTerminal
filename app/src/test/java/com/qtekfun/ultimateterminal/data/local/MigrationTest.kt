// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Opens a database created with each exported schema and lets Room migrate and validate it to the
 * latest version. Each new schema version adds a case here that also checks its data survives.
 */
class MigrationTest {
    @TempDir
    lateinit var dir: File

    private val schemas = File("schemas/${UltimateTerminalDatabase::class.qualifiedName}")

    /** Creates [file] exactly as version [version] of the exported schema describes it. */
    private fun createFromSchema(file: File, version: Int, extraSql: List<String> = emptyList()) {
        val schema = Json.parseToJsonElement(
            File(schemas, "$version.json").readText()
        ).jsonObject["database"]!!.jsonObject
        val statements = schema["entities"]!!.jsonArray.flatMap { entity ->
            val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
            val create = entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace(
                "\${TABLE_NAME}",
                table
            )
            val indices = entity.jsonObject["indices"]?.jsonArray.orEmpty().map {
                it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            }
            listOf(create) + indices
        } + schema["setupQueries"]!!.jsonArray.map { it.jsonPrimitive.content }
        val connection = BundledSQLiteDriver().open(file.path)
        (statements + extraSql + "PRAGMA user_version = $version").forEach(connection::execSQL)
        connection.close()
    }

    private fun open(file: File): UltimateTerminalDatabase {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getDatabasePath(any()) } returns file
        return Room.databaseBuilder<UltimateTerminalDatabase>(context, file.name)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*UltimateTerminalDatabase.MIGRATIONS)
            .build()
    }

    @Test
    fun `opens a version 1 database with the latest code and keeps its data`() = runTest {
        val file = File(dir, "v1.db")
        createFromSchema(
            file,
            version = 1,
            extraSql = listOf(
                "INSERT INTO distro (id, name, type, release, directory, defaultUser, state, " +
                    "sizeBytes, installedAtMillis, isDefault) " +
                    "VALUES (1, 'debian', 'DEBIAN', '12', 'distros/a', 'root', 'READY', 10, 0, 1)",
                "INSERT INTO setting (`key`, value) VALUES ('oled_black', 'true')"
            )
        )

        val db = open(file)
        val distro = db.distroDao().get(1)
        val settings = db.settingDao().all()
        db.close()

        assertEquals("debian", distro?.name)
        assertEquals(true, distro?.isDefault)
        assertEquals(listOf("oled_black" to "true"), settings.map { it.key to it.value })
    }
}
