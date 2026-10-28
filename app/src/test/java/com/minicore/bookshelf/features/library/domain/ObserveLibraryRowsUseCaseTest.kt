package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private val FixedToday = LocalDate.of(2026, 6, 15)
private val FixedClock: Clock = Clock.fixed(
    FixedToday.atStartOfDay(ZoneOffset.UTC).toInstant(),
    ZoneOffset.UTC,
)

private fun book(id: Long, status: BookStatus = BookStatus.Reading) = Book(
    id = id,
    title = "Book $id",
    author = "Author $id",
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

private class FakeLibraryRepository : LibraryRepository {
    val reading = MutableStateFlow<List<LibraryBook>>(emptyList())
    val lent = MutableStateFlow<List<LentBook>>(emptyList())

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = reading
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = reading
    override fun observeLentOutBooks(): Flow<List<LentBook>> = lent
    override fun observeCounts(): Flow<LibraryCounts> =
        MutableStateFlow(LibraryCounts(0, 0, 0, 0, 0))

    override fun observeBook(id: Long): Flow<Book?> = throw NotImplementedError()
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = throw NotImplementedError()

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()
    override suspend fun removeBook(id: Long): Boolean = throw NotImplementedError()
}

/** AC1/AC2 (plan section 6): the use case combines the repository's flows verbatim, per date. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveLibraryRowsUseCaseTest {

    private val repository = FakeLibraryRepository()
    private val useCase = ObserveLibraryRowsUseCase(repository, CurrentDateProvider(Provider { FixedClock }))

    @Test
    fun currentlyReading_matchesRepositoryOrder_verbatim() = runTest {
        val b1 = book(1)
        val b2 = book(2)
        repository.reading.value = listOf(LibraryBook(b1, null), LibraryBook(b2, null))

        val rows = useCase().first()

        assertEquals(listOf(b1, b2), rows.currentlyReading)
    }

    @Test
    fun lentOut_mapsLoanToDueStatus() = runTest {
        val overdueBook = book(10, status = BookStatus.Reading)
        val dueTodayBook = book(11, status = BookStatus.Reading)
        repository.lent.value = listOf(
            LentBook(
                overdueBook,
                OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = FixedToday.minusDays(2)),
            ),
            LentBook(
                dueTodayBook,
                OpenLoan(loanId = 2, borrowerName = "Sam", loanedAt = Instant.EPOCH, dueAt = FixedToday),
            ),
        )

        val rows = useCase().first()

        assertEquals(2, rows.lentOut.size)
        assertEquals(LoanDueStatus.Overdue(2), rows.lentOut[0].dueStatus)
        assertEquals("Priya", rows.lentOut[0].borrowerName)
        assertEquals(LoanDueStatus.DueToday, rows.lentOut[1].dueStatus)
    }

    // F28: a reordered repository emission must not be re-sorted by the use case.
    @Test
    fun lentOut_doesNotReorderRepositoryOutput() = runTest {
        val a = book(1)
        val b = book(2)
        val loanFor = { book: Book, due: LocalDate ->
            LentBook(book, OpenLoan(book.id, "Name ${book.id}", Instant.EPOCH, due))
        }
        // Deliberately not sorted by due date - the repository already owns ordering.
        repository.lent.value = listOf(
            loanFor(b, FixedToday.plusDays(5)),
            loanFor(a, FixedToday.minusDays(1)),
        )

        val rows = useCase().first()

        assertEquals(listOf(b.id, a.id), rows.lentOut.map { it.book.id })
    }

    @Test
    fun reEmitsWhenRepositoryFlowChanges() = runTest {
        val results = mutableListOf<LibraryRows>()
        val job = launch {
            useCase().collect { results.add(it) }
        }
        runCurrent()

        repository.reading.value = listOf(LibraryBook(book(1), null))
        runCurrent()
        job.cancel()

        assertEquals(2, results.size)
        assertEquals(emptyList<Book>(), results[0].currentlyReading)
        assertEquals(listOf(book(1)), results[1].currentlyReading)
    }
}
