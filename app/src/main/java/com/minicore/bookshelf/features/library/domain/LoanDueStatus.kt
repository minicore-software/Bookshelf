package com.minicore.bookshelf.features.library.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The rendered due/overdue state of an open loan, derived per render date (plan section 4/R4).
 * Never persisted - the repository KDoc warns that a flag computed once goes stale at midnight,
 * so this is always recomputed from [OpenLoan.dueAt][com.minicore.bookshelf.features.library.domain.model.OpenLoan.dueAt]
 * and the current date ([CurrentDateProvider]).
 */
sealed interface LoanDueStatus {

    val isOverdue: Boolean get() = this is Overdue

    /** [days] is the number of whole days past due, always `>= 1`. */
    data class Overdue(val days: Long) : LoanDueStatus

    data object DueToday : LoanDueStatus

    /** [days] is `1..DUE_SOON_MAX_DAYS`. */
    data class DueSoon(val days: Long) : LoanDueStatus

    data class DueLater(val dueAt: LocalDate, val showYear: Boolean) : LoanDueStatus

    companion object {
        const val DUE_SOON_MAX_DAYS = 7L

        /** @param today the render date, from [CurrentDateProvider] - never `LocalDate.now()` directly. */
        fun of(dueAt: LocalDate, today: LocalDate): LoanDueStatus {
            val days = ChronoUnit.DAYS.between(today, dueAt)
            return when {
                days < 0 -> Overdue(-days)
                days == 0L -> DueToday
                days <= DUE_SOON_MAX_DAYS -> DueSoon(days)
                else -> DueLater(dueAt, showYear = dueAt.year != today.year)
            }
        }
    }
}
