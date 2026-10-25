package com.minicore.bookshelf.features.library.domain.model

import com.minicore.bookshelf.core.model.Book

/** `openLoan != null` is the "lent" overlay (epic #17 decision D10) - it is not a [Book.status]. */
data class LibraryBook(
    val book: Book,
    val openLoan: OpenLoan?,
)
