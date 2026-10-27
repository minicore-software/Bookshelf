package com.minicore.bookshelf.core.model

/**
 * One of the 6 solid-colour cover placeholders a [Book] can use instead of an uploaded image
 * (plan section 2 S2, ADR-002). Persisted as the stable `TEXT` literals `preset_1`..`preset_6` by
 * `com.minicore.bookshelf.core.database.converter.BookshelfConverters`, never by [name] or
 * ordinal, so reordering or renaming this enum cannot remap stored rows (same rule as
 * [BookStatus]).
 */
enum class CoverPreset {
    Preset1,
    Preset2,
    Preset3,
    Preset4,
    Preset5,
    Preset6,
    ;

    companion object {
        /**
         * The deterministic fallback preset for a book with neither a cover image nor a stored
         * cover colour (plan S3/S4): `'preset_' + ((id % 6) + 1)`, matching the SQL backfill
         * formula exactly - a test pins the two together.
         */
        fun forBookId(bookId: Long): CoverPreset = entries[Math.floorMod(bookId, entries.size.toLong()).toInt()]
    }
}
