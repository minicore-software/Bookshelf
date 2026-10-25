package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.model.Book

/**
 * A [LentBook][com.minicore.bookshelf.features.library.domain.model.LentBook] with its
 * presentation-only due status attached (plan section 4). Never logged - [borrowerName] is
 * third-party personal data (plan section 7).
 */
data class LentOutRow(
    val book: Book,
    val borrowerName: String,
    val dueStatus: LoanDueStatus,
)

/**
 * The two rows shown on the Library screen below the header (plan R10/R11). Order is the
 * repository's own order, verbatim - this is never re-sorted here.
 */
data class LibraryRows(
    val currentlyReading: List<Book>,
    val lentOut: List<LentOutRow>,
)
