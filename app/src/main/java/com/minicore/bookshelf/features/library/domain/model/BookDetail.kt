package com.minicore.bookshelf.features.library.domain.model

import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.features.library.domain.LoanDueStatus

/** The Book Detail modal's open-loan overlay (plan #32 section 4.2). Deliberately omits
 * `loanId`/`loanedAt` - the modal shows neither. */
data class BookDetailLoan(val borrowerName: String, val dueStatus: LoanDueStatus)

/** [loan] `!= null` is the "lent out" overlay (plan #32 decision V5) - never [Book.status]. */
data class BookDetail(val book: Book, val loan: BookDetailLoan?)
