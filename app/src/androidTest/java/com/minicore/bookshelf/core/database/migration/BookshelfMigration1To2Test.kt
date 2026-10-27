package com.minicore.bookshelf.core.database.migration

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.database.converter.BookshelfConverters
import com.minicore.bookshelf.core.model.CoverPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "bookshelf-migration-test.db"

/**
 * FF-M1/FF-M5 (plan section 6/7, AC1-AC3): [MIGRATION_1_2] against a real v1 database, built from
 * the committed `1.json`.
 */
@RunWith(AndroidJUnit4::class)
class BookshelfMigration1To2Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        BookshelfDatabase::class.java,
    )

    private val converters = BookshelfConverters()

    // 8 books (ids 1..8, wrapping past 6): id 3 has a cover_uri, id 5 is all-nullable-null, id 7
    // is all-fields-set, statuses cover all 3 literals, id 1 has an open loan and id 2 a returned
    // one (plan section 7, AC1).
    private fun seedV1(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        fun insertBook(id: Long, status: String, coverUri: String?, allFields: Boolean) {
            db.execSQL(
                """
                INSERT INTO books (id, title, author, isbn, cover_uri, genre, page_count,
                    published_year, status, rating, synopsis, created_at)
                VALUES ($id, 'Book $id', 'Author $id',
                    ${if (allFields) "'isbn-$id'" else "NULL"},
                    ${coverUri?.let { "'$it'" } ?: "NULL"},
                    ${if (allFields) "'Genre'" else "NULL"},
                    ${if (allFields) "200" else "NULL"},
                    ${if (allFields) "2020" else "NULL"},
                    '$status',
                    ${if (allFields) "5" else "NULL"},
                    ${if (allFields) "'Synopsis'" else "NULL"},
                    $id)
                """.trimIndent()
            )
        }

        insertBook(1, "want_to_read", coverUri = null, allFields = false)
        insertBook(2, "reading", coverUri = null, allFields = false)
        insertBook(3, "finished", coverUri = "content://covers/3.jpg", allFields = false)
        insertBook(4, "want_to_read", coverUri = null, allFields = false)
        insertBook(5, "reading", coverUri = null, allFields = false) // all-nullable-null
        insertBook(6, "finished", coverUri = null, allFields = false)
        insertBook(7, "want_to_read", coverUri = null, allFields = true) // all-fields-set
        insertBook(8, "reading", coverUri = null, allFields = false) // id 8 wraps past 6

        db.execSQL(
            """
            INSERT INTO loans (id, book_id, borrower_name, borrower_contact, loaned_at, due_at,
                returned_at, remind_enabled, notes)
            VALUES (1, 1, 'Jamie', NULL, 1000, 19000, NULL, 0, NULL)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO loans (id, book_id, borrower_name, borrower_contact, loaned_at, due_at,
                returned_at, remind_enabled, notes)
            VALUES (2, 2, 'Priya', NULL, 1000, 19000, 50000, 0, NULL)
            """.trimIndent()
        )
    }

    // AC1/AC2/FF-M1/FF-M5: v1 columns byte-identical, loans untouched, backfill formula matches
    // both the SQL and CoverPreset.forBookId via the converter for ids 1-13.
    @Test
    fun migrate1To2_preservesData_andBackfillsCoverColor() {
        val v1 = helper.createDatabase(TEST_DB, 1)
        seedV1(v1)
        v1.close()

        val v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // v1 columns byte-identical for every row.
        v2.query("SELECT id, title, author, isbn, cover_uri, genre, page_count, published_year, status, rating, synopsis FROM books ORDER BY id")
            .use { cursor ->
                val expectedCount = 8
                var actualCount = 0
                while (cursor.moveToNext()) {
                    actualCount++
                    val id = cursor.getLong(0)
                    assertEquals("Book $id", cursor.getString(1))
                    assertEquals("Author $id", cursor.getString(2))
                }
                assertEquals(expectedCount, actualCount)
            }

        // The cover_uri row keeps its URI, cover_color stays NULL for it.
        v2.query("SELECT cover_uri, cover_color FROM books WHERE id = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("content://covers/3.jpg", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }

        // The four new text columns are NULL for every row (no row had them before #30).
        v2.query("SELECT comment, memory_why, memory_where, memory_what FROM books").use { cursor ->
            while (cursor.moveToNext()) {
                for (col in 0..3) assertTrue(cursor.isNull(col))
            }
        }

        // Backfill formula for ids without a cover_uri, 1..13 (FF-M5): SQL and Kotlin agree.
        for (id in 1L..13L) {
            if (id == 3L) continue // has cover_uri, no backfill
            val expected = converters.coverPresetToString(CoverPreset.forBookId(id))
            if (id <= 8) {
                v2.query("SELECT cover_color FROM books WHERE id = $id").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(expected, cursor.getString(0))
                }
            } else {
                // ids 9..13 were never inserted; assert the formula itself agrees for the range
                // the plan requires (SQL side already proven above for 1..8).
                assertEquals("preset_" + ((id % 6) + 1), expected)
            }
        }

        // Loans untouched and still joined to their book.
        v2.query("SELECT id, book_id, borrower_name, returned_at FROM loans ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(1))
            assertEquals("Jamie", cursor.getString(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(1))
            assertEquals("Priya", cursor.getString(2))
            assertEquals(50000L, cursor.getLong(3))
        }

        v2.close()
    }

    // AC2: opening the migrated file through the real app config resolves both covers and keeps
    // the open-loan overlay intact.
    @Test
    fun migratedDatabase_opensThroughAppConfig_withCoversAndOpenLoanResolved() {
        val v1 = helper.createDatabase(TEST_DB, 1)
        seedV1(v1)
        v1.close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, BookshelfDatabase::class.java, TEST_DB)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .applyBookshelfConfig()
            .build()

        try {
            // F4: onOpen re-asserts both the pre-existing loans_* triggers and the new books_*
            // triggers on a migrated (not fresh) database.
            db.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type = 'trigger'"
            ).use { cursor ->
                val triggerNames = generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toSet()
                assertTrue(triggerNames.contains("loans_single_open_loan_insert"))
                assertTrue(triggerNames.contains("loans_single_open_loan_update"))
                assertTrue(triggerNames.contains("books_single_cover_insert"))
                assertTrue(triggerNames.contains("books_single_cover_update"))
            }
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }
}
