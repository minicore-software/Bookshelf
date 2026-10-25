package com.minicore.bookshelf.features.library.ui

import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.CurrentDateProvider
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.ObserveLibraryRowsUseCase
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

private class FakeLibraryRepository : LibraryRepository {
    val reading = MutableStateFlow<List<LibraryBook>>(emptyList())
    val lent = MutableStateFlow<List<LentBook>>(emptyList())

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = reading
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = reading
    override fun observeLentOutBooks(): Flow<List<LentBook>> = lent
    override fun observeCounts(): Flow<LibraryCounts> = MutableStateFlow(LibraryCounts(0, 0, 0, 0, 0))
}

/** AC1/AC2: [LibraryViewModel] starts [LibraryUiState.Loading], then emits [LibraryUiState.Content]. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

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

    private fun viewModel() = LibraryViewModel(
        ObserveLibraryRowsUseCase(repository, CurrentDateProvider(Provider { clock })),
    )

    @Test
    fun initialState_isLoading() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun afterRepositoryEmits_stateBecomesContent() = runTest(dispatcher) {
        val viewModel = viewModel()
        val collector = mutableListOf<LibraryUiState>()
        val job = launch { viewModel.uiState.collect { collector.add(it) } }

        val book = Book(
            id = 1,
            title = "A Book",
            author = "Author",
            isbn = null,
            coverUri = null,
            genre = null,
            pageCount = null,
            publishedYear = null,
            status = BookStatus.Reading,
            rating = null,
            synopsis = null,
            createdAt = Instant.EPOCH,
        )
        repository.reading.value = listOf(LibraryBook(book, null))
        dispatcher.scheduler.runCurrent()

        assertTrue(collector.last() is LibraryUiState.Content)
        assertEquals(listOf(book), (collector.last() as LibraryUiState.Content).rows.currentlyReading)

        job.cancel()
    }
}
