package com.minicore.bookshelf.features.library.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * The open loan overlay on a [LibraryBook] (plan section 5). Deliberately omits
 * `borrowerContact` and `notes` - the Library screen never shows them (CLAUDE.md data
 * minimisation, plan section 6).
 */
data class OpenLoan(
    val loanId: Long,
    val borrowerName: String,
    val loanedAt: Instant,
    val dueAt: LocalDate,
)
