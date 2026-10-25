package com.minicore.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A13 (QA): trigger durability. In-memory databases cannot survive close/reopen, so this uses a
 * file-backed test database (ADR-001 "onOpen re-asserting IF NOT EXISTS matters").
 */
@RunWith(AndroidJUnit4::class)
class OpenLoanInvariantCallbackTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "open_loan_invariant_test.db"

    private fun buildDatabase(): BookshelfDatabase =
        Room.databaseBuilder(context, BookshelfDatabase::class.java, databaseName)
            .applyBookshelfConfig()
            .build()

    @After
    fun deleteDatabaseFile() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun droppedTrigger_isRestored_whenDatabaseReopenedThroughSameBuilder() {
        val database = buildDatabase()
        database.openHelper.writableDatabase // force creation/open so onCreate/onOpen run
        database.close()

        val reopenedForTamper = buildDatabase()
        reopenedForTamper.openHelper.writableDatabase
            .execSQL("DROP TRIGGER loans_single_open_loan_insert")
        assertFalse(triggerExists(reopenedForTamper, "loans_single_open_loan_insert"))
        reopenedForTamper.close()

        val reopened = buildDatabase()
        reopened.openHelper.writableDatabase // triggers onOpen

        assertTrue(triggerExists(reopened, "loans_single_open_loan_insert"))
        assertTrue(triggerExists(reopened, "loans_single_open_loan_update"))
        reopened.close()
    }

    private fun triggerExists(database: BookshelfDatabase, triggerName: String): Boolean {
        database.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'trigger' AND name = '$triggerName'")
            .use { cursor -> return cursor.moveToFirst() }
    }
}
