package com.minicore.bookshelf.features.library.ui

import androidx.lifecycle.SavedStateHandle
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CurrentDateProvider
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.ObserveLibraryGridUseCase
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
import kotlinx.coroutines.flow.map
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
    val books = MutableStateFlow<List<LibraryBook>>(emptyList())
    val counts = MutableStateFlow(LibraryCounts(0, 0, 0, 0, 0))

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> =
        books.map { all -> if (status == null) all else all.filter { it.book.status == status } }

    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = reading
    override fun observeLentOutBooks(): Flow<List<LentBook>> = lent
    override fun observeCounts(): Flow<LibraryCounts> = counts

    override fun observeBook(id: Long): Flow<Book?> = throw NotImplementedError()

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()
}

/** AC1/AC2 (plan FF2): [LibraryViewModel] starts [LibraryUiState.Loading], then [LibraryUiState.Content]. */
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

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = LibraryViewModel(
        ObserveLibraryRowsUseCase(repository, CurrentDateProvider(Provider { clock })),
        ObserveLibraryGridUseCase(repository),
        savedStateHandle,
    )

    private fun book(id: Long, status: BookStatus = BookStatus.Reading) = Book(
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

    @Test
    fun initialState_isLoading() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun afterRepositoryEmits_stateBecomesContent_withGridFilterAll() = runTest(dispatcher) {
        val viewModel = viewModel()
        val collector = mutableListOf<LibraryUiState>()
        val job = launch { viewModel.uiState.collect { collector.add(it) } }

        val book = book(1)
        repository.reading.value = listOf(LibraryBook(book, null))
        dispatcher.scheduler.runCurrent()

        val content = collector.last()
        assertTrue(content is LibraryUiState.Content)
        assertEquals(listOf(book), (content as LibraryUiState.Content).rows.currentlyReading)
        assertEquals(LibraryFilter.All, content.grid.filter)

        job.cancel()
    }

    // FF2: rapid taps - only the latest-selected filter's content is ever emitted.
    @Test
    fun rapidFilterTaps_onlyTheLatestFilterIsEverEmitted() = runTest(dispatcher) {
        val viewModel = viewModel()
        val collector = mutableListOf<LibraryUiState>()
        val job = launch { viewModel.uiState.collect { collector.add(it) } }
        dispatcher.scheduler.runCurrent()

        viewModel.onFilterSelected(LibraryFilter.Finished)
        viewModel.onFilterSelected(LibraryFilter.LentOut)
        dispatcher.scheduler.runCurrent()

        val filters = collector.filterIsInstance<LibraryUiState.Content>().map { it.grid.filter }
        assertTrue("Finished must never appear once LentOut was selected before it landed", LibraryFilter.Finished !in filters)
        assertEquals(LibraryFilter.LentOut, (collector.last() as LibraryUiState.Content).grid.filter)
        val loadingEmissionsAfterFirstContent = collector.dropWhile { it == LibraryUiState.Loading }.count { it == LibraryUiState.Loading }
        assertEquals("state must never fall back to Loading on a chip tap", 0, loadingEmissionsAfterFirstContent)

        job.cancel()
    }

    // FF2: `SavedStateHandle.getStateFlow` conflates away an intermediate filter value before
    // collection, so a test that merely never sees "Finished" content after switching straight
    // to LentOut cannot tell flatMapLatest apart from flatMapMerge (an intermediate filter is
    // never even emitted upstream under either operator). This test lets the Finished filter's
    // inner flow actually start (an explicit runCurrent after selecting it), then switches to
    // LentOut and pushes a books change that would affect Finished's own query. Only
    // flatMapLatest guarantees the Finished inner flow is cancelled and can never re-emit.
    @Test
    fun switchingAwayFromAFilter_cancelsItsInnerFlow_soItCanNeverReEmit() = runTest(dispatcher) {
        val viewModel = viewModel()
        val collector = mutableListOf<LibraryUiState>()
        val job = launch { viewModel.uiState.collect { collector.add(it) } }
        dispatcher.scheduler.runCurrent()

        viewModel.onFilterSelected(LibraryFilter.Finished)
        dispatcher.scheduler.runCurrent() // Finished's inner flow is now actually subscribed.

        viewModel.onFilterSelected(LibraryFilter.LentOut)
        dispatcher.scheduler.runCurrent() // flatMapLatest must cancel Finished's inner flow here.

        collector.clear()
        repository.books.value = listOf(LibraryBook(book(99, BookStatus.Finished), null))
        dispatcher.scheduler.runCurrent()

        val filtersSeen = collector.filterIsInstance<LibraryUiState.Content>().map { it.grid.filter }
        assertTrue(
            "the cancelled Finished filter's inner flow must never re-emit after switching to LentOut",
            LibraryFilter.Finished !in filtersSeen,
        )
        assertTrue(filtersSeen.isEmpty() || filtersSeen.all { it == LibraryFilter.LentOut })

        job.cancel()
    }

    // FF2/Q8: a ViewModel restored with LentOut pre-selected starts on LentOut, even when that
    // filter's result is empty.
    @Test
    fun restoredFromSavedStateHandle_startsOnLentOut_evenWhenItsResultIsEmpty() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("library_filter" to LibraryFilter.LentOut))
        val viewModel = viewModel(savedStateHandle)
        val collector = mutableListOf<LibraryUiState>()
        val job = launch { viewModel.uiState.collect { collector.add(it) } }
        dispatcher.scheduler.runCurrent()

        val content = collector.last() as LibraryUiState.Content
        assertEquals(LibraryFilter.LentOut, content.grid.filter)
        assertTrue(content.grid.books.isEmpty())

        job.cancel()
    }
}
