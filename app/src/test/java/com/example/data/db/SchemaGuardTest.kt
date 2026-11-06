package com.example.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Guards the committed Room schema (app/schemas/.../1.json). Players' progress lives in this
 * database, so it must never be rebuilt: a schema change needs a version bump plus a Migration, and
 * the new schema file committed. This fails if the code and the committed v1 schema drift apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SchemaGuardTest {

    private fun schemaFile(): File =
        listOf("schemas/com.example.data.db.AppDatabase/1.json", "app/schemas/com.example.data.db.AppDatabase/1.json")
            .map(::File).firstOrNull { it.exists() }
            ?: error("Committed Room schema 1.json not found (expected app/schemas/com.example.data.db.AppDatabase/1.json)")

    @Suppress("UNCHECKED_CAST")
    private fun schemaEntities(): Map<String, Set<String>> {
        val root = Moshi.Builder().build().adapter(Any::class.java).fromJson(schemaFile().readText()) as Map<String, Any?>
        val database = root["database"] as Map<String, Any?>
        assertEquals("The released schema is version 1", 1.0, (database["version"] as Number).toDouble(), 0.0)
        val entities = database["entities"] as List<Map<String, Any?>>
        return entities.associate { entity ->
            val fields = entity["fields"] as List<Map<String, Any?>>
            (entity["tableName"] as String) to fields.map { it["columnName"] as String }.toSet()
        }
    }

    @Test
    fun committedSchemaIsVersionOneWithTheShippedTables() {
        assertEquals(setOf("questions", "user_stats", "quiz_history", "achievements"), schemaEntities().keys)
    }

    @Test
    fun databaseCodeStillMatchesTheCommittedSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val sqlite = db.openHelper.writableDatabase
            assertEquals("Database version changed without a Migration", 1, sqlite.version)
            for ((table, columns) in schemaEntities()) {
                val actual = HashSet<String>()
                sqlite.query("PRAGMA table_info(`$table`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) actual.add(cursor.getString(nameIndex))
                }
                assertTrue("Table $table is missing", actual.isNotEmpty())
                assertEquals("Columns of $table changed without a Migration", columns, actual)
            }
        } finally {
            db.close()
        }
    }
}
