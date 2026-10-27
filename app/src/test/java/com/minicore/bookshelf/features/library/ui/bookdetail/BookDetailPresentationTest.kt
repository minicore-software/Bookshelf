package com.minicore.bookshelf.features.library.ui.bookdetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plan #32 section 3/7 (F4/F6): the pure presentation rules, independent of Compose. */
class BookDetailPresentationTest {

    @Test
    fun authorYearLine_authorAndYear_joinsWithMiddleDot() {
        assertEquals("Flora Thompson · 1939", authorYearLine("Flora Thompson", 1939))
    }

    @Test
    fun authorYearLine_yearOnly_whenAuthorBlank() {
        assertEquals("1939", authorYearLine("", 1939))
        assertEquals("1939", authorYearLine(null, 1939))
        assertEquals("1939", authorYearLine("   ", 1939))
    }

    @Test
    fun authorYearLine_authorOnly_whenYearNull() {
        assertEquals("Flora Thompson", authorYearLine("Flora Thompson", null))
    }

    @Test
    fun authorYearLine_null_whenBothMissing() {
        assertNull(authorYearLine(null, null))
        assertNull(authorYearLine("", null))
    }

    // F6: status is derived only from loan presence, never Book.status - the function's own
    // signature (a plain Boolean, not a Book) makes mis-wiring to Book.status a compile error at
    // the call site, but this still proves the two cases render distinctly.
    @Test
    fun bookDetailStatus_noOpenLoan_isOnTheShelf() {
        assertEquals(BookDetailStatus.OnTheShelf, bookDetailStatus(hasOpenLoan = false))
    }

    @Test
    fun bookDetailStatus_hasOpenLoan_isLentOut() {
        assertEquals(BookDetailStatus.LentOut, bookDetailStatus(hasOpenLoan = true))
    }

    // F5: unrated vs 1-star vs 5-star must be visibly/semantically distinct.
    @Test
    fun ratingAccessibilityLabel_null_isNotRated() {
        assertEquals("Not rated", ratingAccessibilityLabel(null, "Not rated", "Rated %d of 5"))
    }

    @Test
    fun ratingAccessibilityLabel_one_isRatedOneOfFive() {
        assertEquals("Rated 1 of 5", ratingAccessibilityLabel(1, "Not rated", "Rated %d of 5"))
    }

    @Test
    fun ratingAccessibilityLabel_five_isRatedFiveOfFive() {
        assertEquals("Rated 5 of 5", ratingAccessibilityLabel(5, "Not rated", "Rated %d of 5"))
    }
}
