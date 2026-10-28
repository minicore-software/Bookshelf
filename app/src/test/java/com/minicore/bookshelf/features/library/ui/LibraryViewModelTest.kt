package com.minicore.bookshelf.features.library.ui

import androidx.lifecycle.SavedStateHandle
import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CurrentDateProvider
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.ObserveLibraryGridUseCase
import com.minicore.bookshelf.features.library.domain.ObserveLibraryRowsUseCase
import com.minicore.bookshelf.features.library.domain.RemoveBookUseCase
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeLibraryRepository : LibraryRepository {
    val reading = MutableStateFlow<List<LibraryBook>>(emptyList())
    val lent = MutableStateFlow<List<LentBook>>(emptyList())
    val books = MutableStateFlow<List<LibraryBook>>(emptyList())
    val counts = MutableStateFlow(LibraryCounts(0, 0, 0, 0, 0))

    var removeCallCount = 0
    var failRemoveWith: Throwable? = null

    /** Lets a test observe the exact moment `removeBook` is suspended, to prove the prompt was
     * cleared and `isRemoving` set *before* this point (single-flight, B5/B6). */
    var removeGate: CompletableDeferred<Unit>? = null

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> =
        books.map { all -> if (status == null) all else all.filter { it.book.status == status } }

    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = reading
    override fun observeLentOutBooks(): Flow<List<LentBook>> = lent
    override fun observeCounts(): Flow<LibraryCounts> = counts

    override fun observeBook(id: Long): Flow<Book?> = books.map { all -> all.firstOrNull { it.book.id == id }?.book }
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = books.map { all -> all.firstOrNull { it.book.id == id } }

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()

    override suspend fun removeBook(id: Long): Boolean {
        removeCallCount++
        removeGate?.await()
        failRemoveWith?.let { throw it }
        val before = books.value
        val after = before.filterNot { it.book.id == id }
        books.value = after
        return after.size != before.size
    }
}

private class FakeCoverImageStore : CoverImageStore {
    override suspend fun importDraft(sourceUri: String): String = throw NotImplementedError()
    override suspend fun discardDraft(draftRef: String) = Unit
    override suspend fun commit(draftRef: String): String = throw NotImplementedError()
    override suspend fun uncommit(storedRef: String, draftRef: String) = Unit
    override suspend fun delete(storedRef: String) = Unit
    override suspend fun exists(ref: String): Boolean = true
}

/** AC1/AC2 (plan FF2); AC3/B1-B10 (plan #35 section 6): [LibraryViewModel]'s remove-prompt flow. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeLibraryRepository()
    private val coverImages = FakeCoverImageStore()
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
        RemoveBookUseCase(repository, coverImages),
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

    private val loan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.now())

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

    private fun kotlinx.coroutines.test.TestScope.primeGrid(viewModel: LibraryViewModel, books: List<LibraryBook>) {
        repository.books.value = books
        dispatcher.scheduler.runCurrent()
        // Force a subscription to uiState so it reflects the latest books (WhileSubscribed).
        val job = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.runCurrent()
        job.cancel()
    }

    // B1: a lent book's prompt carries the borrower name.
    @Test
    fun onRemoveRequested_lentBook_promptHasBorrowerName() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1, BookStatus.Finished), loan)))

        viewModel.onRemoveRequested(1)

        val prompt = viewModel.removePrompt.value
        assertEquals(RemoveBookPrompt(1, "Book 1", "Priya"), prompt)
    }

    // B2: a book with no open loan has a null borrowerName in its prompt.
    @Test
    fun onRemoveRequested_bookNotLent_promptBorrowerNameIsNull() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1), null)))

        viewModel.onRemoveRequested(1)

        assertEquals(RemoveBookPrompt(1, "Book 1", null), viewModel.removePrompt.value)
    }

    // B3: a stale/unknown id is a no-op guard.
    @Test
    fun onRemoveRequested_unknownId_isNoOp() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1), null)))

        viewModel.onRemoveRequested(999)

        assertNull(viewModel.removePrompt.value)
    }

    // B4: dismiss clears the prompt and the use case is never called.
    @Test
    fun onRemoveDismissed_clearsPrompt_useCaseNeverCalled() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1), null)))
        viewModel.onRemoveRequested(1)

        viewModel.onRemoveDismissed()

        assertNull(viewModel.removePrompt.value)
        assertEquals(0, repository.removeCallCount)
    }

    // B5: confirm invokes the use case exactly once, and clears the prompt before the suspend
    // point resolves (the gate proves the prompt is already null while `removeBook` is suspended).
    @Test
    fun onRemoveConfirmed_invokesUseCaseOnce_clearsPromptBeforeSuspendResolves() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1), null)))
        viewModel.onRemoveRequested(1)
        repository.removeGate = CompletableDeferred()

        viewModel.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertNull("prompt must be cleared before the suspend point resolves", viewModel.removePrompt.value)
        assertEquals(1, repository.removeCallCount)

        repository.removeGate?.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, repository.removeCallCount)
    }

    // B6: two confirms in the same frame call the use case once (single-flight).
    @Test
    fun onRemoveConfirmed_calledTwiceInSameFrame_callsUseCaseOnce() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1), null)))
        viewModel.onRemoveRequested(1)

        viewModel.onRemoveConfirmed()
        viewModel.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, repository.removeCallCount)
    }

    // B7: a new request while a remove is in flight is a no-op.
    @Test
    fun onRemoveRequested_whileRemoving_isNoOp() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1), null), LibraryBook(book(2), null)))
        viewModel.onRemoveRequested(1)
        repository.removeGate = CompletableDeferred()
        viewModel.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        viewModel.onRemoveRequested(2)

        assertNull("a request while removing must stay a no-op", viewModel.removePrompt.value)

        repository.removeGate?.complete(Unit)
        dispatcher.scheduler.runCurrent()
    }

    // B8: NotFound is a silent no-op - no RemoveFailed event.
    @Test
    fun onRemoveConfirmed_notFound_isSilent_noEvent() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1), null)))
        viewModel.onRemoveRequested(1)
        // Removed out from under the prompt before confirm resolves.
        repository.books.value = emptyList()

        val events = mutableListOf<LibraryEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertTrue(events.isEmpty())
        job.cancel()
    }

    // B9: a throw sends one RemoveFailed event, and the VM is not stuck - a later request works.
    @Test
    fun onRemoveConfirmed_throws_sendsRemoveFailed_vmNotStuck() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1), null), LibraryBook(book(2), null)))
        repository.failRemoveWith = IllegalStateException("boom")

        val events = mutableListOf<LibraryEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onRemoveRequested(1)
        viewModel.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(LibraryEvent.RemoveFailed), events)

        repository.failRemoveWith = null
        viewModel.onRemoveRequested(2)
        assertEquals(RemoveBookPrompt(2, "Book 2", null), viewModel.removePrompt.value)
        viewModel.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()
        assertEquals(2, repository.removeCallCount)

        job.cancel()
    }

    // B10 (should): a CancellationException is rethrown, not swallowed into a RemoveFailed event.
    @Test
    fun onRemoveConfirmed_cancellationException_isRethrown_notSwallowed() = runTest(dispatcher) {
        val viewModel = viewModel()
        primeGrid(viewModel, listOf(LibraryBook(book(1), null)))
        repository.failRemoveWith = CancellationException("cancelled")

        val events = mutableListOf<LibraryEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onRemoveRequested(1)
        viewModel.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertTrue("a CancellationException must never surface as RemoveFailed", events.isEmpty())
        job.cancel()
    }

    // Rotation/process-death (B18): the prompt restores from SavedStateHandle.
    @Test
    fun removePrompt_restoresFromSavedStateHandle() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "library_remove_book_id" to 1L,
                "library_remove_book_title" to "Book 1",
                "library_remove_book_borrower" to "Priya",
            ),
        )
        val viewModel = viewModel(savedStateHandle)

        assertEquals(RemoveBookPrompt(1, "Book 1", "Priya"), viewModel.removePrompt.value)
    }

    // B19: dismissing clears all three saved keys, so a later restore sees no ghost prompt.
    @Test
    fun onRemoveDismissed_clearsSavedStateHandleKeys() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle()
        val viewModel = viewModel(savedStateHandle)
        primeGrid(viewModel, listOf(LibraryBook(book(1), null)))
        viewModel.onRemoveRequested(1)

        viewModel.onRemoveDismissed()

        assertNull(savedStateHandle.get<Long>("library_remove_book_id"))
    }
}
