package com.minicore.bookshelf.features.library.ui.bookform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure rule coverage shared by Add and Edit (plan #33 decision E9). */
class BookFormRulesTest {

    @Test
    fun sanitizeYearText_rejectsNonDigitsAndTooLong() {
        assertNull(BookFormRules.sanitizeYearText("19a5"))
        assertNull(BookFormRules.sanitizeYearText("19999"))
        assertEquals("1995", BookFormRules.sanitizeYearText("1995"))
        assertEquals("", BookFormRules.sanitizeYearText(""))
    }

    @Test
    fun stepYear_onEmptyOrZero_seedsCurrentYear() {
        assertEquals("2026", BookFormRules.stepYear("", -1, 2026))
        assertEquals("2026", BookFormRules.stepYear("0", 1, 2026))
    }

    @Test
    fun stepYear_stepsAndClamps() {
        assertEquals("9999", BookFormRules.stepYear("9999", 1, 2026))
        assertEquals("1", BookFormRules.stepYear("1", -1, 2026))
        assertEquals("1966", BookFormRules.stepYear("1965", 1, 2026))
    }

    @Test
    fun parsePublishedYear_outOfRangeOrUnparsable_isNull() {
        assertNull(BookFormRules.parsePublishedYear(""))
        assertNull(BookFormRules.parsePublishedYear("0"))
        assertNull(BookFormRules.parsePublishedYear("10000"))
        assertEquals(1965, BookFormRules.parsePublishedYear("1965"))
    }

    @Test
    fun toggleRating_tappingSameStarClearsIt() {
        assertEquals(3, BookFormRules.toggleRating(null, 3))
        assertNull(BookFormRules.toggleRating(3, 3))
        assertEquals(1, BookFormRules.toggleRating(3, 1))
    }
}
