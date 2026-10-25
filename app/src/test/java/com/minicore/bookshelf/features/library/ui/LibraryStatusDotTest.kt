package com.minicore.bookshelf.features.library.ui

import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

private fun book(status: BookStatus) = Book(
    id = 1,
    title = "A Book",
    author = "Author",
    isbn = null,
    coverUri = null,
    genre = null,
    pageCount = null,
    publishedYear = null,
    status = status,
    rating = null,
    synopsis = null,
    createdAt = Instant.EPOCH,
)

private val loan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.now())

/** AC3/plan G6: the dot/caption rule, including the F5 critical proof. */
class LibraryStatusDotTest {

    @Test
    fun reading_withoutLoan_isReadingKind() {
        assertEquals(StatusDotKind.Reading, LibraryBook(book(BookStatus.Reading), null).statusDotKind())
    }

    @Test
    fun finished_withoutLoan_isFinishedKind() {
        assertEquals(StatusDotKind.Finished, LibraryBook(book(BookStatus.Finished), null).statusDotKind())
    }

    @Test
    fun wantToRead_withoutLoan_isWantToReadKind() {
        assertEquals(StatusDotKind.WantToRead, LibraryBook(book(BookStatus.WantToRead), null).statusDotKind())
    }

    // F5: every status with an open loan returns Lent, regardless of the underlying status.
    @Test
    fun anyStatus_withAnOpenLoan_isLentKind_regardlessOfStatus() {
        for (status in BookStatus.entries) {
            assertEquals(
                "status=$status must be Lent when it has an open loan",
                StatusDotKind.Lent,
                LibraryBook(book(status), loan).statusDotKind(),
            )
        }
    }
}
