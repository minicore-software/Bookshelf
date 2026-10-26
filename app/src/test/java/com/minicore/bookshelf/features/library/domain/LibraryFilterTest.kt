package com.minicore.bookshelf.features.library.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AC1 (plan G10): the chip row renders `LibraryFilter.entries` verbatim
 * ([LibraryFilterChips][com.minicore.bookshelf.features.library.ui.LibraryFilterChips]), so the
 * on-screen chip order is exactly this enum's declaration order - asserted here as a plain
 * enum fact, rather than by measuring a possibly off-screen chip's pixel bounds.
 */
class LibraryFilterTest {

    @Test
    fun entries_areInScreenOrder() {
        assertEquals(
            listOf(
                LibraryFilter.All,
                LibraryFilter.Reading,
                LibraryFilter.Finished,
                LibraryFilter.WantToRead,
                LibraryFilter.LentOut,
            ),
            LibraryFilter.entries,
        )
    }
}
