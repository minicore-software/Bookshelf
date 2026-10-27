package com.minicore.bookshelf.features.library.ui.bookdetail

import androidx.lifecycle.SavedStateHandle
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CurrentDateProvider
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.ObserveBookDetailUseCase
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun book(id: Long) = Book(
    id = id,
    title = "Book $id",
    author = "Author",
    isbn = null,
    cover = Cover.Preset(CoverPreset.forBookId(id)),
    genre = null,
    pageCount = null,
    publishedYear = null,
    status = BookStatus.Finished,
    rating = null,
    synopsis = null,
    createdAt = Instant.EPOCH,
)

private class FakeLibraryRepository : LibraryRepository {
    val byId = MutableStateFlow<LibraryBook?>(null)

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeLentOutBooks(): Flow<List<LentBook>> = throw NotImplementedError()
    override fun observeCounts(): Flow<LibraryCounts> = throw NotImplementedError()
    override fun observeBook(id: Long): Flow<Book?> = throw NotImplementedError()
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = byId

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()
    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()
}

/** Plan #32 decision V8: [BookDetailViewModel] starts [BookDetailUiState.Loading], then reflects
 * the repository's overlay as [BookDetailUiState.Loaded] or [BookDetailUiState.NotFound]. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeLibraryRepository()
    private val clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(bookId: Long = 1L) = BookDetailViewModel(
        ObserveBookDetailUseCase(repository, CurrentDateProvider(Provider { clock })),
        SavedStateHandle(mapOf("bookId" to bookId)),
    )

    @Test
    fun initialState_isLoading() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertEquals(BookDetailUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun repositoryEmitsBook_stateBecomesLoaded() = runTest(dispatcher) {
        val viewModel = viewModel(bookId = 1)
        val collector = mutableListOf<BookDetailUiState>()
        val job = launch { viewModel.uiState.collect { collector.add(it) } }

        repository.byId.value = LibraryBook(book(1), null)
        dispatcher.scheduler.runCurrent()

        val last = collector.last()
        assertTrue(last is BookDetailUiState.Loaded)
        assertEquals(book(1), (last as BookDetailUiState.Loaded).detail.book)

        job.cancel()
    }

    @Test
    fun repositoryEmitsNull_stateBecomesNotFound() = runTest(dispatcher) {
        val viewModel = viewModel(bookId = 404)
        val collector = mutableListOf<BookDetailUiState>()
        val job = launch { viewModel.uiState.collect { collector.add(it) } }
        dispatcher.scheduler.runCurrent()

        repository.byId.value = null
        dispatcher.scheduler.runCurrent()

        assertEquals(BookDetailUiState.NotFound, collector.last())

        job.cancel()
    }

    // QA risk "vanished book": a book present then deleted flips Loaded -> NotFound live.
    @Test
    fun bookDeletedWhileObserving_flipsFromLoadedToNotFound() = runTest(dispatcher) {
        val viewModel = viewModel(bookId = 1)
        val collector = mutableListOf<BookDetailUiState>()
        val job = launch { viewModel.uiState.collect { collector.add(it) } }

        repository.byId.value = LibraryBook(book(1), null)
        dispatcher.scheduler.runCurrent()
        assertTrue(collector.last() is BookDetailUiState.Loaded)

        repository.byId.value = null
        dispatcher.scheduler.runCurrent()
        assertEquals(BookDetailUiState.NotFound, collector.last())

        job.cancel()
    }
}
