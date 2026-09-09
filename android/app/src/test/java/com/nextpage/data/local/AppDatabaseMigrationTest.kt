package com.nextpage.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private fun query(db: SupportSQLiteDatabase, sql: String): Long {
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            return cursor.getLong(0)
        }
    }

    private fun testDbPath(): String =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getDatabasePath("migration-26-27")
            .absolutePath

    @Test
    fun `migration 26 to 27 creates installed_addons and preserves existing rows`() {
        val dbPath = testDbPath()
        val outboxBefore: Long
        helper.createDatabase(dbPath, 26).use { db ->
            db.execSQL(
                "INSERT INTO discover_cache (key, payload, fetched_at, ttl_s) " +
                    "VALUES ('p:test:q:1', '{\"n\":1}', 1000, 86400)"
            )
            outboxBefore = query(db, "SELECT COUNT(*) FROM sync_outbox")
        }

        val db = helper.runMigrationsAndValidate(dbPath, 27, true, AppDatabaseMigrations.MIGRATION_26_27)

        db.query("SELECT payload FROM discover_cache WHERE `key` = 'p:test:q:1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("{\"n\":1}", cursor.getString(0))
        }

        db.query("SELECT id, url, manifest_json, enabled, added_at FROM installed_addons").use { cursor ->
            assertEquals(0, cursor.count)
        }

        db.query("PRAGMA table_info(installed_addons)").use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names.add(cursor.getString(1))
            assertEquals(listOf("id", "url", "manifest_json", "enabled", "added_at"), names)
        }

        val outboxAfter = query(db, "SELECT COUNT(*) FROM sync_outbox")
        assertEquals(outboxBefore, outboxAfter)
        db.close()
    }
}
