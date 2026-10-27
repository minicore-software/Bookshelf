package com.minicore.bookshelf.core.database

import androidx.room.RoomDatabase
import com.minicore.bookshelf.core.database.migration.BookshelfMigrations

/**
 * Single build path for every [BookshelfDatabase] instance - production and tests alike (plan
 * section 3, ADR-001 constraint, extended by ADR-002). A database built without this function
 * silently lacks the open-loan invariant (ADR-001 risk R1), the cover invariant, and every
 * registered migration.
 */
fun RoomDatabase.Builder<BookshelfDatabase>.applyBookshelfConfig(): RoomDatabase.Builder<BookshelfDatabase> =
    addCallback(OpenLoanInvariantCallback)
        .addCallback(BookCoverInvariantCallback)
        .addMigrations(*BookshelfMigrations.ALL)
