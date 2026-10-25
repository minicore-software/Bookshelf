package com.minicore.bookshelf.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Creates the two triggers that enforce "at most one open loan per book" at the database engine
 * level (ADR-001 S5, Option 1). Runs in both [onCreate] and [onOpen]: `onOpen` re-asserts the
 * triggers with `CREATE TRIGGER IF NOT EXISTS` so a future migration that recreates `loans`
 * cannot silently drop the invariant.
 *
 * On Android `RAISE(ABORT, ...)` surfaces as [android.database.sqlite.SQLiteConstraintException].
 */
object OpenLoanInvariantCallback : RoomDatabase.Callback() {

    private const val CREATE_INSERT_TRIGGER = """
        CREATE TRIGGER IF NOT EXISTS loans_single_open_loan_insert
        BEFORE INSERT ON loans
        WHEN NEW.returned_at IS NULL
         AND EXISTS (SELECT 1 FROM loans WHERE book_id = NEW.book_id AND returned_at IS NULL)
        BEGIN SELECT RAISE(ABORT, 'book already has an open loan'); END;
    """

    private const val CREATE_UPDATE_TRIGGER = """
        CREATE TRIGGER IF NOT EXISTS loans_single_open_loan_update
        BEFORE UPDATE OF book_id, returned_at ON loans
        WHEN NEW.returned_at IS NULL
         AND EXISTS (SELECT 1 FROM loans WHERE book_id = NEW.book_id AND returned_at IS NULL AND id <> NEW.id)
        BEGIN SELECT RAISE(ABORT, 'book already has an open loan'); END;
    """

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        createTriggers(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        createTriggers(db)
    }

    private fun createTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_INSERT_TRIGGER)
        db.execSQL(CREATE_UPDATE_TRIGGER)
    }
}
