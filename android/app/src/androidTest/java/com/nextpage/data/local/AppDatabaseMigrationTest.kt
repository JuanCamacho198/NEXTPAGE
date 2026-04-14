package com.nextpage.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_preservesExistingBookRows() {
        val dbName = "migration-test"

        helper.createDatabase(dbName, 4).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS books (
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    author TEXT,
                    cover_path TEXT,
                    file_path TEXT NOT NULL,
                    format TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO books (id, title, author, cover_path, file_path, format, updated_at)
                VALUES ('book-1', 'Title 1', 'Author 1', NULL, '/tmp/book-1.epub', 'epub', 123)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            AppDatabaseMigrations.MIGRATION_4_5
        ).query("SELECT id, deleted_at FROM books WHERE id = 'book-1'").use { cursor ->
            check(cursor.moveToFirst()) { "Expected migrated book row to exist" }
            val deletedAtColumn = cursor.getColumnIndex("deleted_at")
            check(deletedAtColumn >= 0) { "Expected deleted_at column to exist" }
            check(cursor.isNull(deletedAtColumn)) { "Expected deleted_at to be null for existing rows" }
        }
    }

    @Test
    fun migrate4To5_usingRegisteredMigrations_succeeds() {
        val dbName = "migration-test-all"

        helper.createDatabase(dbName, 4).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS books (
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    author TEXT,
                    cover_path TEXT,
                    file_path TEXT NOT NULL,
                    format TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            *AppDatabaseMigrations.ALL
        ).query("PRAGMA table_info(books)").use { cursor ->
            var hasDeletedAt = false
            val nameColumn = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == "deleted_at") {
                    hasDeletedAt = true
                    break
                }
            }
            assertEquals(true, hasDeletedAt)
        }
    }
}
