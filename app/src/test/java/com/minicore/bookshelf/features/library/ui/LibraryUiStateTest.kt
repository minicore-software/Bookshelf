package com.minicore.bookshelf.features.library.ui

import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.LentOutRow
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.LibraryGrid
import com.minicore.bookshelf.features.library.domain.LibraryRows
import com.minicore.bookshelf.features.library.domain.LoanDueStatus
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FF1 (plan `docs/plans/22-empty-state-library-screen.md` section 6): the [isLibraryEmpty] truth table. */
class LibraryUiStateTest {

    private fun book(id: Long = 1, status: BookStatus = BookStatus.Reading) = Book(
        id = id,
        title = "Book $id",
        author = "Author",
        isbn = null,
        cover = Cover.Preset(CoverPreset.forBookId(id)),
        genre = null,
        pageCount = null,
        publishedYear = null,
        status = status,
        rating = null,
        synopsis = null,
        createdAt = Instant.EPOCH,
    )

    private fun counts(all: Int) = LibraryCounts(all = all, reading = 0, finished = 0, wantToRead = 0, lentOut = 0)

    private fun grid(all: Int) = LibraryGrid(filter = LibraryFilter.All, counts = counts(all), books = emptyList())

    @Test
    fun zeroCountsAndEmptyRows_isEmpty() {
        val content = LibraryUiState.Content(LibraryRows(emptyList(), emptyList()), grid(all = 0))

        assertTrue(content.isLibraryEmpty)
    }

    @Test
    fun countsAllGreaterThanZero_isNotEmpty() {
        val content = LibraryUiState.Content(LibraryRows(emptyList(), emptyList()), grid(all = 1))

        assertFalse(content.isLibraryEmpty)
    }

    @Test
    fun zeroCountsButReadingRowNonEmpty_isNotEmpty() {
        val content = LibraryUiState.Content(
            rows = LibraryRows(currentlyReading = listOf(book()), lentOut = emptyList()),
            grid = grid(all = 0),
        )

        assertFalse(content.isLibraryEmpty)
    }

    @Test
    fun zeroCountsButLentRowNonEmpty_isNotEmpty() {
        val lentRow = LentOutRow(book = book(), borrowerName = "Priya", dueStatus = LoanDueStatus.DueToday)
        val content = LibraryUiState.Content(
            rows = LibraryRows(currentlyReading = emptyList(), lentOut = listOf(lentRow)),
            grid = grid(all = 0),
        )

        assertFalse(content.isLibraryEmpty)
    }
}
