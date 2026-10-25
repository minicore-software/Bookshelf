package com.minicore.bookshelf.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.minicore.bookshelf.core.database.entity.BookEntity
import java.time.Instant
import java.time.LocalDate

/**
 * Projection of a book joined with its open loan, if any (plan section 4/5). `open_loan_id` is
 * null when there is no open loan for [book] - the `LEFT JOIN` overlay that [LibraryBookRow]
 * reads from guarantees at most one matching loan row per book (ADR-001 S5).
 */
data class LibraryBookRow(
    @Embedded
    val book: BookEntity,
    @ColumnInfo(name = "open_loan_id")
    val openLoanId: Long?,
    @ColumnInfo(name = "open_loan_borrower_name")
    val openLoanBorrowerName: String?,
    @ColumnInfo(name = "open_loan_loaned_at")
    val openLoanLoanedAt: Instant?,
    @ColumnInfo(name = "open_loan_due_at")
    val openLoanDueAt: LocalDate?,
)
