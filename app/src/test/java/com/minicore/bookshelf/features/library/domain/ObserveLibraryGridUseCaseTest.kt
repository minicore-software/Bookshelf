package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun book(id: Long, status: BookStatus) = Book(
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

/**
 * Records every query the use case issues, backed by [MutableStateFlow]s so re-emission can be
 * driven from a test (plan FF1).
 */
private class RecordingLibraryRepository : LibraryRepository {
    val calls = mutableListOf<BookStatus?>()
    var lentOutCalled = false

    val books = MutableStateFlow<List<LibraryBook>>(emptyList())
    val lent = MutableStateFlow<List<LentBook>>(emptyList())
    val counts = MutableStateFlow(LibraryCounts(0, 0, 0, 0, 0))

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> {
        calls.add(status)
        return books
    }

    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = books

    override fun observeLentOutBooks(): Flow<List<LentBook>> {
        lentOutCalled = true
        return lent
    }

    override fun observeCounts(): Flow<LibraryCounts> = counts

    override fun observeBook(id: Long): Flow<Book?> = throw NotImplementedError()

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()
}

/** AC1/AC2, plan FF1: the correct query per filter, lent-out never touches [LibraryRepository.observeBooks]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveLibraryGridUseCaseTest {

    private val repository = RecordingLibraryRepository()
    private val useCase = ObserveLibraryGridUseCase(repository)

    @Test
    fun all_callsObserveBooksWithNullStatus() = runTest {
        val job = launch { useCase(LibraryFilter.All).collect {} }
        runCurrent()
        job.cancel()

        assertEquals(listOf(null), repository.calls)
        assertFalse(repository.lentOutCalled)
    }

    @Test
    fun reading_callsObserveBooksWithReadingStatus() = runTest {
        val job = launch { useCase(LibraryFilter.Reading).collect {} }
        runCurrent()
        job.cancel()

        assertEquals(listOf(BookStatus.Reading), repository.calls)
    }

    @Test
    fun finished_callsObserveBooksWithFinishedStatus() = runTest {
        val job = launch { useCase(LibraryFilter.Finished).collect {} }
        runCurrent()
        job.cancel()

        assertEquals(listOf(BookStatus.Finished), repository.calls)
    }

    @Test
    fun wantToRead_callsObserveBooksWithWantToReadStatus() = runTest {
        val job = launch { useCase(LibraryFilter.WantToRead).collect {} }
        runCurrent()
        job.cancel()

        assertEquals(listOf(BookStatus.WantToRead), repository.calls)
    }

    // The critical AC2 proof: LentOut never calls observeBooks, under any circumstance.
    @Test
    fun lentOut_neverCallsObserveBooks_onlyObserveLentOutBooks() = runTest {
        val job = launch { useCase(LibraryFilter.LentOut).collect {} }
        runCurrent()
        job.cancel()

        assertTrue(repository.calls.isEmpty())
        assertTrue(repository.lentOutCalled)
    }

    @Test
    fun lentOut_includesAFinishedBookWithAnOpenLoan_andExcludesAReadingBookWithoutOne() = runTest {
        val finishedLent = book(1, BookStatus.Finished)
        val loan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.now())
        repository.lent.value = listOf(LentBook(finishedLent, loan))
        repository.books.value = listOf(LibraryBook(book(2, BookStatus.Reading), null))

        val grid = useCase(LibraryFilter.LentOut).let {
            var result: LibraryGrid? = null
            val job = launch { it.collect { emission -> result = emission } }
            runCurrent()
            job.cancel()
            result!!
        }

        assertEquals(listOf(finishedLent.id), grid.books.map { it.book.id })
        assertEquals(loan, grid.books.single().openLoan)
    }

    @Test
    fun newCountsEmission_reEmitsLibraryGrid_withTheSameFilter() = runTest {
        val results = mutableListOf<LibraryGrid>()
        val job = launch { useCase(LibraryFilter.Finished).collect { results.add(it) } }
        runCurrent()

        repository.counts.value = LibraryCounts(5, 1, 3, 1, 0)
        runCurrent()
        job.cancel()

        assertEquals(2, results.size)
        assertTrue(results.all { it.filter == LibraryFilter.Finished })
        assertEquals(LibraryCounts(5, 1, 3, 1, 0), results.last().counts)
    }

    @Test
    fun countFor_mapsEveryFilterEntry() {
        val counts = LibraryCounts(all = 27, reading = 2, finished = 19, wantToRead = 6, lentOut = 3)

        for (filter in LibraryFilter.entries) {
            val expected = when (filter) {
                LibraryFilter.All -> 27
                LibraryFilter.Reading -> 2
                LibraryFilter.Finished -> 19
                LibraryFilter.WantToRead -> 6
                LibraryFilter.LentOut -> 3
            }
            assertEquals(expected, counts.countFor(filter))
        }
    }
}
