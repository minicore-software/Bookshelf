package com.minicore.bookshelf.core.model

/**
 * Reading status of a [Book]. Persisted as stable `TEXT` values by
 * `com.minicore.bookshelf.core.database.converter.BookshelfConverters` (never by [name] or
 * ordinal) - see ADR-001 and plan section 2 (S4).
 */
enum class BookStatus {
    WantToRead,
    Reading,
    Finished,
}
