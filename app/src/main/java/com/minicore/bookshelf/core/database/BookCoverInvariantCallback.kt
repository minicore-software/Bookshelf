package com.minicore.bookshelf.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Creates the two triggers that enforce "never both a cover image and a cover colour" on `books`
 * at the database engine level (ADR-002 S1, plan section 3.1). Runs in both [onCreate] and
 * [onOpen], same pattern as [OpenLoanInvariantCallback]: `onOpen` re-asserts the triggers with
 * `CREATE TRIGGER IF NOT EXISTS` so a future migration that recreates `books` cannot silently
 * drop the invariant. [MIGRATION_1_2][com.minicore.bookshelf.core.database.migration.MIGRATION_1_2]
 * calls [createTriggers] directly so the SQL exists in one place (plan section 3.2).
 *
 * On Android `RAISE(ABORT, ...)` surfaces as [android.database.sqlite.SQLiteConstraintException].
 */
object BookCoverInvariantCallback : RoomDatabase.Callback() {

    private const val CREATE_INSERT_TRIGGER = """
        CREATE TRIGGER IF NOT EXISTS books_single_cover_insert
        BEFORE INSERT ON books
        WHEN NEW.cover_uri IS NOT NULL AND NEW.cover_color IS NOT NULL
        BEGIN SELECT RAISE(ABORT, 'book cover has both uri and color'); END;
    """

    private const val CREATE_UPDATE_TRIGGER = """
        CREATE TRIGGER IF NOT EXISTS books_single_cover_update
        BEFORE UPDATE OF cover_uri, cover_color ON books
        WHEN NEW.cover_uri IS NOT NULL AND NEW.cover_color IS NOT NULL
        BEGIN SELECT RAISE(ABORT, 'book cover has both uri and color'); END;
    """

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        createTriggers(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        createTriggers(db)
    }

    fun createTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_INSERT_TRIGGER)
        db.execSQL(CREATE_UPDATE_TRIGGER)
    }
}
