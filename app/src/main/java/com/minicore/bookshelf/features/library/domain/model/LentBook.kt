package com.minicore.bookshelf.features.library.domain.model

import com.minicore.bookshelf.core.model.Book

/** A book with a guaranteed (non-null) open loan - the "Lent out" row and chip (plan section 5). */
data class LentBook(
    val book: Book,
    val loan: OpenLoan,
)
