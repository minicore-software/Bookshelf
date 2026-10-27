package com.minicore.bookshelf.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.minicore.bookshelf.core.database.BookCoverInvariantCallback

/**
 * `books` 1 -> 2 (plan section 3.2, ADR-002): adds `comment`, the three `memory_*` fields and
 * `cover_color`, all nullable `TEXT` with no `@ColumnInfo(defaultValue)` so they match
 * `ALTER TABLE ... ADD COLUMN ... TEXT` exactly and pass Room's post-migration `TableInfo`
 * validation (plan R1). Backfills `cover_color` for rows with neither a cover image nor a cover
 * colour today, using the same `'preset_' + ((id % 6) + 1)` formula [CoverPreset.forBookId][com.minicore.bookshelf.core.model.CoverPreset.forBookId]
 * applies at read time (plan S4) - a test pins the two together. Finally (re)creates the
 * both-covers-forbidden triggers via [BookCoverInvariantCallback.createTriggers], shared so the
 * SQL exists once. Room wraps [migrate] in a transaction.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN comment TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN memory_why TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN memory_where TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN memory_what TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN cover_color TEXT")
        db.execSQL(
            """
            UPDATE books SET cover_color = 'preset_' || ((id % 6) + 1)
            WHERE cover_uri IS NULL AND cover_color IS NULL
            """.trimIndent()
        )
        BookCoverInvariantCallback.createTriggers(db)
    }
}

/** Every step `N -> N+1` the database has ever shipped (plan S5, FF-M3). Never empty. */
object BookshelfMigrations {
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
