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
import org.junit.Assert.assertNull
import org.junit.Test

private val FixedToday = LocalDate.of(2026, 6, 15)
private val FixedClock: Clock = Clock.fixed(
    FixedToday.atStartOfDay(ZoneOffset.UTC).toInstant(),
    ZoneOffset.UTC,
)

private fun book(id: Long, status: BookStatus = BookStatus.Finished) = Book(
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

private class FakeBookDetailLibraryRepository : LibraryRepository {
    val byId = MutableStateFlow<LibraryBook?>(null)

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeLentOutBooks(): Flow<List<LentBook>> = throw NotImplementedError()
    override fun observeCounts(): Flow<LibraryCounts> = throw NotImplementedError()
    override fun observeBook(id: Long): Flow<Book?> = throw NotImplementedError()
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = byId

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()
    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()
    override suspend fun removeBook(id: Long): Boolean = throw NotImplementedError()
}

/** Plan #32 section 7 AC1: [ObserveBookDetailUseCase] maps the repository's overlay and the
 * current render date into [com.minicore.bookshelf.features.library.domain.model.BookDetail]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBookDetailUseCaseTest {

    private val repository = FakeBookDetailLibraryRepository()
    private val useCase = ObserveBookDetailUseCase(repository, CurrentDateProvider(Provider { FixedClock }))

    @Test
    fun repositoryEmitsNull_useCaseEmitsNull() = runTest {
        repository.byId.value = null

        assertNull(useCase(1).first())
    }

    @Test
    fun noOpenLoan_mapsToDetailWithNullLoan() = runTest {
        repository.byId.value = LibraryBook(book(1), null)

        val detail = useCase(1).first()

        assertEquals(book(1), detail?.book)
        assertNull(detail?.loan)
    }

    @Test
    fun openLoan_mapsToDetailWithDueStatus() = runTest {
        val loan = OpenLoan(
            loanId = 1,
            borrowerName = "Priya",
            loanedAt = Instant.EPOCH,
            dueAt = FixedToday.plusDays(3),
        )
        repository.byId.value = LibraryBook(book(1), loan)

        val detail = useCase(1).first()

        assertEquals("Priya", detail?.loan?.borrowerName)
        assertEquals(LoanDueStatus.DueSoon(3), detail?.loan?.dueStatus)
    }

    @Test
    fun reEmitsWhenRepositoryFlowChanges() = runTest {
        val results = mutableListOf<Any?>()
        val job = launch { useCase(1).collect { results.add(it) } }
        runCurrent()

        repository.byId.value = LibraryBook(book(1), null)
        runCurrent()
        job.cancel()

        assertEquals(2, results.size)
        assertNull(results[0])
        assertEquals(book(1), (results[1] as com.minicore.bookshelf.features.library.domain.model.BookDetail).book)
    }
}
