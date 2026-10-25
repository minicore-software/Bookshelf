package com.minicore.bookshelf.core.database.converter

import com.minicore.bookshelf.core.model.BookStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FF3 (plan section 7): converter round-trips plus an exact-value pin on the persisted
 * [BookStatus] strings (ADR-001 S4) - changing a stored value fails this test instead of
 * silently orphaning rows.
 */
class BookshelfConvertersTest {

    private val converters = BookshelfConverters()

    @Test
    fun instant_roundTrips() {
        val instant = Instant.ofEpochMilli(1_700_000_000_123L)

        val millis = converters.instantToEpochMillis(instant)
        val restored = converters.epochMillisToInstant(millis)

        assertEquals(instant, restored)
    }

    @Test
    fun nullableInstant_roundTrips_whenNull() {
        assertNull(converters.instantToEpochMillis(null))
        assertNull(converters.epochMillisToInstant(null))
    }

    @Test
    fun localDate_roundTrips_viaEpochDay() {
        val date = LocalDate.of(2026, 3, 8)

        val epochDay = converters.localDateToEpochDay(date)
        val restored = converters.epochDayToLocalDate(epochDay)

        assertEquals(date, restored)
        assertEquals(date.toEpochDay(), epochDay)
    }

    @Test
    fun nullableLocalDate_roundTrips_whenNull() {
        assertNull(converters.localDateToEpochDay(null))
        assertNull(converters.epochDayToLocalDate(null))
    }

    @Test
    fun bookStatus_roundTrips_forAllValues() {
        BookStatus.entries.forEach { status ->
            val stored = converters.bookStatusToString(status)
            val restored = converters.stringToBookStatus(stored)
            assertEquals(status, restored)
        }
    }

    @Test
    fun bookStatus_persistsAsStableLiterals_notOrdinalOrPascalCase() {
        assertEquals("want_to_read", converters.bookStatusToString(BookStatus.WantToRead))
        assertEquals("reading", converters.bookStatusToString(BookStatus.Reading))
        assertEquals("finished", converters.bookStatusToString(BookStatus.Finished))
    }

    @Test(expected = IllegalArgumentException::class)
    fun bookStatus_throws_onUnknownStoredValue() {
        converters.stringToBookStatus("archived")
    }
}
