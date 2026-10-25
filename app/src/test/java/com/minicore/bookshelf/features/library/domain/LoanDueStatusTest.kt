package com.minicore.bookshelf.features.library.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** FF1 (plan section 6): the day-boundary table and the two risk cases (year/leap). */
class LoanDueStatusTest {

    private val today = LocalDate.of(2026, 6, 15)

    // F1
    @Test
    fun oneDayPast_isOverdueWithOneDay() {
        val status = LoanDueStatus.of(dueAt = today.minusDays(1), today = today)

        assertEquals(LoanDueStatus.Overdue(1), status)
    }

    // F2 - the highest-risk off-by-one: due today must never be Overdue.
    @Test
    fun sameDay_isDueToday_notOverdue() {
        val status = LoanDueStatus.of(dueAt = today, today = today)

        assertEquals(LoanDueStatus.DueToday, status)
    }

    // F3
    @Test
    fun oneDayAhead_isDueSoonWithOneDay() {
        val status = LoanDueStatus.of(dueAt = today.plusDays(1), today = today)

        assertEquals(LoanDueStatus.DueSoon(1), status)
    }

    // F4
    @Test
    fun sevenDaysAhead_isDueSoonWithSevenDays() {
        val status = LoanDueStatus.of(dueAt = today.plusDays(7), today = today)

        assertEquals(LoanDueStatus.DueSoon(7), status)
    }

    // F5
    @Test
    fun eightDaysAhead_isDueLater() {
        val status = LoanDueStatus.of(dueAt = today.plusDays(8), today = today)

        assertEquals(LoanDueStatus.DueLater(today.plusDays(8), showYear = false), status)
    }

    @Test
    fun farOverdue_countsDaysCorrectly() {
        val status = LoanDueStatus.of(dueAt = today.minusDays(400), today = today)

        assertEquals(LoanDueStatus.Overdue(400), status)
    }

    // F6: year-boundary - the caption must show the year when dueAt's year differs from today's.
    // 8 days out (beyond the 7-day DueSoon window, R4) so this lands in DueLater.
    @Test
    fun dueLaterAcrossYearBoundary_showsYear() {
        val yearEndToday = LocalDate.of(2026, 12, 28)
        val dueNextYear = LocalDate.of(2027, 1, 5)

        val status = LoanDueStatus.of(dueAt = dueNextYear, today = yearEndToday)

        assertEquals(LoanDueStatus.DueLater(dueNextYear, showYear = true), status)
    }

    @Test
    fun dueLaterWithinSameYear_doesNotShowYear() {
        val status = LoanDueStatus.of(dueAt = LocalDate.of(2026, 12, 31), today = LocalDate.of(2026, 1, 1))

        assertEquals(false, (status as LoanDueStatus.DueLater).showYear)
    }

    // F7: leap day - no crash, correct day count.
    @Test
    fun leapDay_countsCorrectly_andDoesNotCrash() {
        val leapYearToday = LocalDate.of(2024, 2, 28)
        val leapDay = LocalDate.of(2024, 2, 29)

        val status = LoanDueStatus.of(dueAt = leapDay, today = leapYearToday)

        assertEquals(LoanDueStatus.DueSoon(1), status)
    }

    @Test
    fun isOverdue_isTrueOnlyForOverdueState() {
        assertEquals(true, LoanDueStatus.Overdue(1).isOverdue)
        assertEquals(false, LoanDueStatus.DueToday.isOverdue)
        assertEquals(false, LoanDueStatus.DueSoon(1).isOverdue)
        assertEquals(false, LoanDueStatus.DueLater(today, false).isOverdue)
    }
}
