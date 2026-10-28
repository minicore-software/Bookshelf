package com.minicore.bookshelf.features.library.ui.editbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.coverimage.CoverImportException
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CoverDraft
import com.minicore.bookshelf.features.library.domain.CurrentDateProvider
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.ObserveBookDetailUseCase
import com.minicore.bookshelf.features.library.domain.RemoveBookUseCase
import com.minicore.bookshelf.features.library.domain.UpdateBookUseCase
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun populatedBook(id: Long = 1L) = Book(
    id = id,
    title = "Dune",
    author = "Frank Herbert",
    isbn = "9780441013593",
    cover = Cover.Image("covers/original.jpg"),
    genre = "Sci-fi",
    pageCount = 412,
    publishedYear = 1965,
    status = BookStatus.Finished,
    rating = 4,
    synopsis = "A desert planet.",
    comment = "Loved it",
    memoryWhy = "recommended",
    memoryWhere = "home",
    memoryWhat = "curious",
    createdAt = Instant.EPOCH,
)

private fun sparseBook(id: Long = 2L) = Book(
    id = id,
    title = "Untitled",
    author = "",
    isbn = null,
    cover = Cover.Preset(CoverPreset.forBookId(id)),
    genre = null,
    pageCount = null,
    publishedYear = null,
    status = BookStatus.WantToRead,
    rating = null,
    synopsis = null,
    createdAt = Instant.EPOCH,
)

private class EditBookFakeLibraryRepository : LibraryRepository {
    val byId = MutableStateFlow<LibraryBook?>(null)

    /** `> 0` makes [observeLibraryBook] suspend before its first emission, so a test can exercise
     * a fallback racing a real, slower upstream instead of this fake's otherwise-synchronous one. */
    var emissionDelayMillis: Long = 0
    var updateCallCount = 0
    var lastUpdated: Book? = null
    var updateReturnsFalse = false
    var failUpdate = false
    var removeCallCount = 0
    var removeReturnsFalse = false
    var failRemove = false

    /** Completed by a test to control exactly when [removeBook] resumes, so a test can assert on
     * in-flight `isRemoving` state before letting the use case finish. */
    var removeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** Completed by a test to control exactly when [updateBook] resumes, so a test can assert on
     * in-flight `isSaving` state before letting the save finish. */
    var updateGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeLentOutBooks(): Flow<List<LentBook>> = throw NotImplementedError()
    override fun observeCounts(): Flow<LibraryCounts> = throw NotImplementedError()
    override fun observeBook(id: Long): Flow<Book?> = MutableStateFlow(byId.value?.book)

    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> =
        if (emissionDelayMillis > 0) {
            flow {
                delay(emissionDelayMillis)
                emitAll(byId)
            }
        } else {
            byId
        }

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean {
        updateCallCount++
        updateGate?.await()
        if (failUpdate) throw IllegalStateException("update failed")
        lastUpdated = book
        if (updateReturnsFalse) return false
        byId.value = byId.value?.copy(book = book)
        return true
    }

    override suspend fun removeBook(id: Long): Boolean {
        removeCallCount++
        removeGate?.await()
        if (failRemove) throw IllegalStateException("remove failed")
        if (removeReturnsFalse) return false
        byId.value = null
        return true
    }
}

private class EditBookFakeCoverImageStore : CoverImageStore {
    val discarded = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var failImport = false
    var nextDraftRef = "cover-drafts/11111111-1111-1111-1111-111111111111.jpg"
    var draftExists = true

    override suspend fun importDraft(sourceUri: String): String {
        if (failImport) throw CoverImportException("decode failed")
        return nextDraftRef
    }

    override suspend fun discardDraft(draftRef: String) {
        discarded += draftRef
    }

    override suspend fun commit(draftRef: String): String = "covers/22222222-2222-2222-2222-222222222222.jpg"
    override suspend fun uncommit(storedRef: String, draftRef: String) = Unit
    override suspend fun delete(storedRef: String) {
        deleted += storedRef
    }
    override suspend fun exists(ref: String): Boolean = draftExists
}

/**
 * FF-E2 (plan #33 section 5): [EditBookViewModel]'s pre-fill, live lending, single-flight save
 * and process-death restore rules.
 *
 * Uses [StandardTestDispatcher] as `Dispatchers.Main`, matching `BookDetailViewModelTest`'s
 * pattern for the same `ObserveBookDetailUseCase`/`CurrentDateProvider` combo - a prior version of
 * this file used `UnconfinedTestDispatcher` on the (incorrect) theory that `combine()` over
 * `CurrentDateProvider`'s infinite `delay()`-based flow deadlocks `StandardTestDispatcher`;
 * `BookDetailViewModelTest` proves otherwise. The actual deadlock was in this file's own old
 * `nextResult()` helper, which called `runBlocking { vm.onDone.first() }` on the test thread while
 * waiting for a Main-dispatched event that only ever advances via `runCurrent()` - a
 * `StandardTestDispatcher` cannot advance from inside a nested `runBlocking` on the same thread.
 * `onDone` is now collected into a list from `backgroundScope`, which `runTest` drives and cancels
 * for us.
 *
 * [editBookTest] wraps every test body: [EditBookViewModel] keeps its own upstream
 * `ObserveBookDetailUseCase` subscription alive for the ViewModel's whole lifetime (the `init`
 * block's `onEach { }.launchIn(viewModelScope)`, independent of whether anyone collects
 * [EditBookViewModel.uiState]), and that subscription chains into [CurrentDateProvider.observe]'s
 * infinite `delay()`-based loop. Nothing in a plain JVM test ever calls `ViewModel.onCleared()`,
 * so if a created [EditBookViewModel] is left running, `runTest`'s own end-of-test
 * `advanceUntilIdle()` spins forever advancing through that loop's ever-rescheduled delay - a real
 * livelock, not a deadlock (confirmed via a thread dump: 100% CPU inside
 * `TestCoroutineScheduler.advanceUntilIdleOr`). [editBookTest] cancels every [EditBookViewModel]
 * created through [viewModel] before the test body returns, breaking that chain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditBookViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = EditBookFakeLibraryRepository()
    private val coverImages = EditBookFakeCoverImageStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC)
    private val createdViewModels = mutableListOf<EditBookViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun observeDetail() = ObserveBookDetailUseCase(repository, CurrentDateProvider(Provider { clock }))

    private fun viewModel(bookId: Long = 1L, savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("bookId" to bookId))) =
        EditBookViewModel(
            observeDetail(),
            UpdateBookUseCase(repository, coverImages),
            RemoveBookUseCase(repository, coverImages),
            coverImages,
            clock,
            savedStateHandle,
        ).also { createdViewModels += it }

    /** Runs [testBody] under [dispatcher], then cancels every [EditBookViewModel] created via
     * [viewModel] so `runTest`'s trailing drain can finish (see the class doc). */
    private fun editBookTest(testBody: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            testBody()
        } finally {
            createdViewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    // F1: a fully-populated book seeds every field, including a Stored cover.
    @Test
    fun fullyPopulatedBook_seedsEveryField() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        val state = vm.uiState.value
        assertTrue(state.isLoaded)
        assertEquals("Dune", state.title)
        assertEquals("Frank Herbert", state.author)
        assertEquals("1965", state.yearText)
        assertEquals(4, state.rating)
        assertEquals("Loved it", state.comment)
        assertEquals("recommended", state.memoryWhy)
        assertEquals("home", state.memoryWhere)
        assertEquals("curious", state.memoryWhat)
        assertEquals(CoverDraft.Stored("covers/original.jpg"), state.cover)
        assertFalse(state.isLentOut)
    }

    // F2: a sparse book (title only) seeds empty fields, an unset (null) rating and the
    // id-derived preset cover.
    @Test
    fun sparseBook_seedsEmptyFieldsAndUnsetRating() = editBookTest {
        repository.byId.value = LibraryBook(sparseBook(2L), null)
        val vm = viewModel(bookId = 2L)
        dispatcher.scheduler.runCurrent()

        val state = vm.uiState.value
        assertTrue(state.isLoaded)
        assertEquals("Untitled", state.title)
        assertEquals("", state.author)
        assertEquals("", state.yearText)
        assertNull(state.rating)
        assertEquals("", state.comment)
        assertEquals("", state.memoryWhy)
        assertEquals(CoverDraft.Preset(CoverPreset.forBookId(2L)), state.cover)
    }

    // F3/F8: typing in a field while the underlying row re-emits (e.g. a loan opens) does not
    // overwrite the typed text - only isLentOut updates, live, both directions.
    @Test
    fun roomReEmission_doesNotOverwriteTypedText_onlyUpdatesIsLentOut() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onTitleChange("Dune Messiah")
        assertFalse(vm.uiState.value.isLentOut)

        val loan = OpenLoan(loanId = 9L, borrowerName = "Alex", loanedAt = Instant.EPOCH, dueAt = LocalDate.of(1970, 1, 2))
        repository.byId.value = LibraryBook(populatedBook(1L), loan)
        dispatcher.scheduler.runCurrent()

        assertEquals("Dune Messiah", vm.uiState.value.title)
        assertTrue(vm.uiState.value.isLentOut)

        repository.byId.value = LibraryBook(populatedBook(1L), null)
        dispatcher.scheduler.runCurrent()

        assertEquals("Dune Messiah", vm.uiState.value.title)
        assertFalse(vm.uiState.value.isLentOut)
    }

    // F13: a clean dismiss (no edits) emits Cancelled without needing a confirm step.
    @Test
    fun cleanDismiss_emitsCancelled() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.uiState.value.isDirty)

        val results = mutableListOf<EditBookResult>()
        backgroundScope.launch { vm.onDone.toList(results) }

        vm.onDismissRequest()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(EditBookResult.Cancelled), results)
    }

    // F13: editing a field makes the form dirty.
    @Test
    fun editingAField_makesTheFormDirty() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onTitleChange("Dune Messiah")
        assertTrue(vm.uiState.value.isDirty)
    }

    // F17, FF-E2: two onSave() calls without a suspension point in between update exactly once.
    @Test
    fun doubleSave_updatesExactlyOnce() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onSave()
        vm.onSave()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, repository.updateCallCount)
    }

    @Test
    fun save_emitsSaved_onSuccess() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        val results = mutableListOf<EditBookResult>()
        backgroundScope.launch { vm.onDone.toList(results) }

        vm.onTitleChange("Dune Messiah")
        vm.onSave()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(EditBookResult.Saved), results)
        assertEquals("Dune Messiah", requireNotNull(repository.lastUpdated).title)
    }

    @Test
    fun save_keepsFieldsAndSetsError_onFailure() = editBookTest {
        repository.failUpdate = true
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onTitleChange("Dune Messiah")
        vm.onSave()
        dispatcher.scheduler.runCurrent()

        val state = vm.uiState.value
        assertFalse(state.isSaving)
        assertEquals(EditBookError.SaveFailed, state.error)
        assertEquals("Dune Messiah", state.title)
        assertTrue(state.canSave)
    }

    // F22: the row vanishing while Edit is open emits NotFound, with no crash.
    @Test
    fun rowDeletedWhileOpen_emitsNotFound() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        val results = mutableListOf<EditBookResult>()
        backgroundScope.launch { vm.onDone.toList(results) }

        repository.byId.value = null
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(EditBookResult.NotFound), results)
    }

    // Review finding #2: an in-progress CoverDraft.Image draft must not leak into
    // cache/cover-drafts/ when the row vanishes while it is selected.
    @Test
    fun rowDeletedWhileAnImageDraftIsSelected_discardsTheDraft() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onImagePicked("content://new-photo")
        dispatcher.scheduler.runCurrent()
        assertEquals(CoverDraft.Image(coverImages.nextDraftRef), vm.uiState.value.cover)

        repository.byId.value = null
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(coverImages.nextDraftRef), coverImages.discarded)
    }

    // Review finding #2: the same leak, reached through a failed save (updateBook returns false)
    // rather than a live Room re-emission.
    @Test
    fun saveReturnsNotFoundWhileAnImageDraftIsSelected_discardsTheDraft() = editBookTest {
        repository.updateReturnsFalse = true
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onImagePicked("content://new-photo")
        dispatcher.scheduler.runCurrent()
        assertEquals(CoverDraft.Image(coverImages.nextDraftRef), vm.uiState.value.cover)

        vm.onSave()
        dispatcher.scheduler.runCurrent()

        assertTrue(coverImages.discarded.contains(coverImages.nextDraftRef))
    }

    // F19: process-death restore round-trips every field via SavedStateHandle.
    @Test
    fun restoredFromSavedStateHandle_roundTripsFields() = editBookTest {
        val savedStateHandle = SavedStateHandle(mapOf("bookId" to 1L))
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val original = viewModel(bookId = 1L, savedStateHandle = savedStateHandle)
        dispatcher.scheduler.runCurrent()

        original.onTitleChange("Dune Messiah")
        original.onMemoryWhyChange("A road trip")

        val restored = viewModel(bookId = 1L, savedStateHandle = savedStateHandle)
        dispatcher.scheduler.runCurrent()

        val state = restored.uiState.value
        assertTrue(state.isLoaded)
        assertEquals("Dune Messiah", state.title)
        assertEquals("A road trip", state.memoryWhy)
        assertEquals(CoverDraft.Stored("covers/original.jpg"), state.cover)
    }

    // F19: a purged image draft on restore falls back to the book's own original cover, not a
    // preset.
    @Test
    fun restoredWithAPurgedImageDraft_fallsBackToTheOriginalStoredCover() = editBookTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "bookId" to 1L,
                "seeded" to true,
                "title" to "Dune",
                "cover_draft_ref" to "cover-drafts/does-not-exist.jpg",
            ),
        )
        coverImages.draftExists = false
        repository.byId.value = LibraryBook(populatedBook(1L), null)

        val restored = viewModel(bookId = 1L, savedStateHandle = savedStateHandle)
        dispatcher.scheduler.runCurrent()

        assertEquals(CoverDraft.Stored("covers/original.jpg"), restored.uiState.value.cover)
    }

    // F19 regression: a purged draft, falling back against a *slower* upstream, must not resolve
    // to a wrong preset default just because the (synchronous) file-exists check finishes first -
    // it must wait for the real row before touching the cover at all.
    @Test
    fun restoredWithAPurgedImageDraft_andADelayedUpstream_waitsForTheRealCoverBeforeFallingBack() = editBookTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "bookId" to 1L,
                "seeded" to true,
                "title" to "Dune",
                "cover_draft_ref" to "cover-drafts/does-not-exist.jpg",
            ),
        )
        coverImages.draftExists = false
        repository.emissionDelayMillis = 1_000
        repository.byId.value = LibraryBook(populatedBook(1L), null)

        val restored = viewModel(bookId = 1L, savedStateHandle = savedStateHandle)
        dispatcher.scheduler.runCurrent()

        // The file-exists check has already resolved (no delay on it), but the upstream has not
        // emitted yet - the cover must still be the stale restored draft reference, never a
        // preset default standing in for "not known yet".
        assertEquals(CoverDraft.Image("cover-drafts/does-not-exist.jpg"), restored.uiState.value.cover)

        // Not advanceUntilIdle(): the ViewModel's own upstream subscription never goes idle (its
        // init block subscribes to CurrentDateProvider's infinite delay()-loop for as long as it
        // lives, see the class doc), so advanceUntilIdle() would spin forever here. Advancing past
        // only the fake repository's 1s emission delay is enough to let it emit.
        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()

        assertEquals(CoverDraft.Stored("covers/original.jpg"), restored.uiState.value.cover)
    }

    // Review finding #3: if the user picks a new cover before the purged-draft fallback resolves
    // (process-death restore racing a slow upstream), the fallback must not clobber that newer
    // choice once it finally resolves.
    @Test
    fun purgedDraftFallback_doesNotClobberANewerUserSelection_madeBeforeItResolves() = editBookTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "bookId" to 1L,
                "seeded" to true,
                "title" to "Dune",
                "cover_draft_ref" to "cover-drafts/does-not-exist.jpg",
            ),
        )
        coverImages.draftExists = false
        repository.emissionDelayMillis = 1_000
        repository.byId.value = LibraryBook(populatedBook(1L), null)

        val restored = viewModel(bookId = 1L, savedStateHandle = savedStateHandle)
        dispatcher.scheduler.runCurrent()
        assertEquals(CoverDraft.Image("cover-drafts/does-not-exist.jpg"), restored.uiState.value.cover)

        // The user picks a preset while the fallback coroutine is still waiting on the upstream.
        restored.onPresetSelected(CoverPreset.Preset3)
        assertEquals(CoverDraft.Preset(CoverPreset.Preset3), restored.uiState.value.cover)

        // Now let the upstream (and the fallback waiting on it) resolve.
        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()

        // The fallback must see that the cover is no longer the stale restored draft and leave
        // the user's newer choice alone.
        assertEquals(CoverDraft.Preset(CoverPreset.Preset3), restored.uiState.value.cover)
    }

    // Cancel/Remove/preset-select after a Stored cover never touches the stored file (E5).
    @Test
    fun presetSelectedOverAStoredCover_doesNotDiscardAnyFile() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.cover is CoverDraft.Stored)

        vm.onPresetSelected(CoverPreset.Preset3)
        dispatcher.scheduler.runCurrent()

        assertEquals(CoverDraft.Preset(CoverPreset.Preset3), vm.uiState.value.cover)
        assertTrue(coverImages.discarded.isEmpty())
    }

    @Test
    fun removeImageOverAStoredCover_doesNotDiscardAnyFile() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onRemoveImage()
        dispatcher.scheduler.runCurrent()

        assertTrue(vm.uiState.value.cover is CoverDraft.Preset)
        assertTrue(coverImages.discarded.isEmpty())
    }

    @Test
    fun dismissRequest_withAStoredCover_doesNotDiscardAnyFile() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onDismissRequest()
        dispatcher.scheduler.runCurrent()

        assertTrue(coverImages.discarded.isEmpty())
    }

    // Picking a new image over a Stored cover, then discarding: the newly imported draft is
    // discarded like any other CoverDraft.Image draft, but the original stored file is never
    // touched - no delete, no discard call against the stored ref (E5).
    @Test
    fun pickingANewImageOverAStoredCover_thenDiscarding_discardsOnlyTheNewDraft() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.cover is CoverDraft.Stored)

        vm.onImagePicked("content://new-photo")
        dispatcher.scheduler.runCurrent()
        assertEquals(CoverDraft.Image(coverImages.nextDraftRef), vm.uiState.value.cover)

        vm.onDismissRequest()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(coverImages.nextDraftRef), coverImages.discarded)
        assertTrue(coverImages.deleted.isEmpty())
    }

    // V1: two onRemoveConfirmed() calls without a suspension point in between remove exactly
    // once (mirrors F17's doubleSave_updatesExactlyOnce).
    @Test
    fun doubleRemoveConfirmed_removesExactlyOnce() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onRemoveConfirmed()
        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, repository.removeCallCount)
    }

    // V2: onRemoveConfirmed() is a no-op while a save is in flight.
    @Test
    fun removeConfirmed_whileSaving_isNoOp() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        repository.updateGate = kotlinx.coroutines.CompletableDeferred()
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.isSaving)

        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertEquals(0, repository.removeCallCount)
        repository.updateGate?.complete(Unit)
    }

    // V3: onSave() is a no-op while a remove is in flight.
    @Test
    fun save_whileRemoving_isNoOp() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        repository.removeGate = kotlinx.coroutines.CompletableDeferred()
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.isRemoving)

        vm.onSave()
        dispatcher.scheduler.runCurrent()

        assertEquals(0, repository.updateCallCount)
        repository.removeGate?.complete(Unit)
    }

    // V4: onDismissRequest() is a no-op while a remove is in flight.
    @Test
    fun dismissRequest_whileRemoving_isNoOp() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        repository.removeGate = kotlinx.coroutines.CompletableDeferred()
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.isRemoving)

        val results = mutableListOf<EditBookResult>()
        backgroundScope.launch { vm.onDone.toList(results) }

        vm.onDismissRequest()
        dispatcher.scheduler.runCurrent()

        assertTrue(results.isEmpty())
        repository.removeGate?.complete(Unit)
    }

    // V5/R7: a null row emission racing an in-flight remove is suppressed - only the use case's
    // own Removed result is emitted, never NotFound.
    @Test
    fun nullEmission_whileRemoving_isSuppressed_onlyRemovedIsEmitted() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        repository.removeGate = kotlinx.coroutines.CompletableDeferred()
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        val results = mutableListOf<EditBookResult>()
        backgroundScope.launch { vm.onDone.toList(results) }

        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.isRemoving)

        // A live Room re-emission can delay-arrive with null before the use case's own
        // coroutine resumes.
        repository.byId.value = null
        dispatcher.scheduler.runCurrent()
        assertTrue(results.isEmpty())

        repository.removeGate?.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(EditBookResult.Removed), results)
    }

    // V6: an unsaved CoverDraft.Image draft is discarded when the remove succeeds.
    @Test
    fun removeSucceeds_withUnsavedImageDraft_discardsTheDraft() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onImagePicked("content://new-photo")
        dispatcher.scheduler.runCurrent()
        assertEquals(CoverDraft.Image(coverImages.nextDraftRef), vm.uiState.value.cover)

        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(coverImages.nextDraftRef), coverImages.discarded)
    }

    // V7: a CoverDraft.Stored cover is never touched by the VM when the remove succeeds - the
    // use case already deleted the row's own stored cover.
    @Test
    fun removeSucceeds_withStoredCover_doesNotDiscardAnyFile() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.cover is CoverDraft.Stored)

        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertTrue(coverImages.discarded.isEmpty())
    }

    // V8/V9: the use case throwing sets RemoveFailed and re-enables the controls; emits nothing;
    // a retry afterwards succeeds.
    @Test
    fun removeThrows_setsRemoveFailed_reEnables_retrySucceeds() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        repository.failRemove = true
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        val results = mutableListOf<EditBookResult>()
        backgroundScope.launch { vm.onDone.toList(results) }

        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertFalse(vm.uiState.value.isRemoving)
        assertEquals(EditBookError.RemoveFailed, vm.uiState.value.error)
        assertTrue(results.isEmpty())

        repository.failRemove = false
        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(EditBookResult.Removed), results)
    }

    // V10: cancelling the ViewModel's scope while a remove is in flight (the realistic
    // cancellation path, e.g. onCleared) propagates as a CancellationException - it must not be
    // swallowed into EditBookError.RemoveFailed, which would otherwise show a wrong "failed"
    // message for what is really just the screen going away.
    @Test
    fun removeCancelled_viaScopeCancellation_isNotSwallowedAsRemoveFailed() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        repository.removeGate = kotlinx.coroutines.CompletableDeferred()
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()

        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.isRemoving)

        vm.viewModelScope.cancel()
        dispatcher.scheduler.runCurrent()

        // Cancelled mid-flight, never resolved to RemoveFailed.
        assertTrue(vm.uiState.value.isRemoving)
        assertNull(vm.uiState.value.error)
    }

    // V11: borrowerName follows the live loan state, both directions.
    @Test
    fun borrowerName_followsLiveLoanState() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        dispatcher.scheduler.runCurrent()
        assertNull(vm.uiState.value.borrowerName)

        val loan = OpenLoan(loanId = 9L, borrowerName = "Alex", loanedAt = Instant.EPOCH, dueAt = LocalDate.of(1970, 1, 2))
        repository.byId.value = LibraryBook(populatedBook(1L), loan)
        dispatcher.scheduler.runCurrent()
        assertEquals("Alex", vm.uiState.value.borrowerName)

        repository.byId.value = LibraryBook(populatedBook(1L), null)
        dispatcher.scheduler.runCurrent()
        assertNull(vm.uiState.value.borrowerName)
    }

    // V12: onRemoveConfirmed() is a no-op before the form has loaded.
    @Test
    fun removeConfirmed_beforeLoaded_isNoOp() = editBookTest {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        // Deliberately no runCurrent(): isLoaded is still false.
        assertFalse(vm.uiState.value.isLoaded)

        vm.onRemoveConfirmed()
        dispatcher.scheduler.runCurrent()

        assertEquals(0, repository.removeCallCount)
    }
}
