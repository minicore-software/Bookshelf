package com.minicore.bookshelf.core.database

import androidx.room.RoomDatabase

/**
 * Single build path for every [BookshelfDatabase] instance - production and tests alike (plan
 * section 3, ADR-001 constraint). A database built without this function silently lacks the
 * open-loan invariant (ADR-001 risk R1).
 */
fun RoomDatabase.Builder<BookshelfDatabase>.applyBookshelfConfig(): RoomDatabase.Builder<BookshelfDatabase> =
    addCallback(OpenLoanInvariantCallback)
