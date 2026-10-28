package com.minicore.bookshelf.features.library.ui.editbook

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
    var updateCallCount = 0
    var lastUpdated: Book? = null
    var updateReturnsFalse = false
    var failUpdate = false

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeLentOutBooks(): Flow<List<LentBook>> = throw NotImplementedError()
    override fun observeCounts(): Flow<LibraryCounts> = throw NotImplementedError()
    override fun observeBook(id: Long): Flow<Book?> = MutableStateFlow(byId.value?.book)
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = byId
    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean {
        updateCallCount++
        if (failUpdate) throw IllegalStateException("update failed")
        lastUpdated = book
        if (updateReturnsFalse) return false
        byId.value = byId.value?.copy(book = book)
        return true
    }
}

private class EditBookFakeCoverImageStore : CoverImageStore {
    val discarded = mutableListOf<String>()
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
    override suspend fun delete(storedRef: String) = Unit
    override suspend fun exists(ref: String): Boolean = draftExists
}

/**
 * FF-E2 (plan #33 section 5): [EditBookViewModel]'s pre-fill, live lending, single-flight save
 * and process-death restore rules.
 *
 * Uses [UnconfinedTestDispatcher] as `Dispatchers.Main`, not `StandardTestDispatcher`
 * (`AddBookViewModelTest`'s choice): [EditBookViewModel] shares its single upstream
 * `ObserveBookDetailUseCase` subscription through `stateIn`, and that subscription's underlying
 * `combine(...)` reliably deadlocks `StandardTestDispatcher.scheduler.runCurrent()` in this
 * environment (reproduced in isolation, independent of any logic in this ticket - a `combine()`
 * over an infinite `delay()`-based flow collected via `Dispatchers.Main.immediate`, i.e.
 * `viewModelScope`, never resolves under `runCurrent()`, only under eager/unconfined dispatch).
 * `Unconfined` runs every `viewModelScope.launch` eagerly, so no explicit
 * `dispatcher.scheduler.runCurrent()` step is needed anywhere in this file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditBookViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = EditBookFakeLibraryRepository()
    private val coverImages = EditBookFakeCoverImageStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC)

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
            coverImages,
            clock,
            savedStateHandle,
        )

    /** Pulls the next (already-buffered, under `Unconfined`) [EditBookResult] off [vm]'s
     * one-shot event channel without risking a real suspend. */
    private fun nextResult(vm: EditBookViewModel): EditBookResult = runBlocking { vm.onDone.first() }

    // F1: a fully-populated book seeds every field, including a Stored cover.
    @Test
    fun fullyPopulatedBook_seedsEveryField() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)

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
    fun sparseBook_seedsEmptyFieldsAndUnsetRating() {
        repository.byId.value = LibraryBook(sparseBook(2L), null)
        val vm = viewModel(bookId = 2L)

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
    fun roomReEmission_doesNotOverwriteTypedText_onlyUpdatesIsLentOut() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)

        vm.onTitleChange("Dune Messiah")
        assertFalse(vm.uiState.value.isLentOut)

        val loan = OpenLoan(loanId = 9L, borrowerName = "Alex", loanedAt = Instant.EPOCH, dueAt = LocalDate.of(1970, 1, 2))
        repository.byId.value = LibraryBook(populatedBook(1L), loan)

        assertEquals("Dune Messiah", vm.uiState.value.title)
        assertTrue(vm.uiState.value.isLentOut)

        repository.byId.value = LibraryBook(populatedBook(1L), null)

        assertEquals("Dune Messiah", vm.uiState.value.title)
        assertFalse(vm.uiState.value.isLentOut)
    }

    // F13: a clean dismiss (no edits) emits Cancelled without needing a confirm step.
    @Test
    fun cleanDismiss_emitsCancelled() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        assertFalse(vm.uiState.value.isDirty)

        vm.onDismissRequest()

        assertEquals(EditBookResult.Cancelled, nextResult(vm))
    }

    // F13: editing a field makes the form dirty.
    @Test
    fun editingAField_makesTheFormDirty() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)

        vm.onTitleChange("Dune Messiah")
        assertTrue(vm.uiState.value.isDirty)
    }

    // F17, FF-E2: two onSave() calls without a suspension point in between update exactly once.
    @Test
    fun doubleSave_updatesExactlyOnce() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)

        vm.onSave()
        vm.onSave()

        assertEquals(1, repository.updateCallCount)
    }

    @Test
    fun save_emitsSaved_onSuccess() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)

        vm.onTitleChange("Dune Messiah")
        vm.onSave()

        assertEquals(EditBookResult.Saved, nextResult(vm))
        assertEquals("Dune Messiah", requireNotNull(repository.lastUpdated).title)
    }

    @Test
    fun save_keepsFieldsAndSetsError_onFailure() {
        repository.failUpdate = true
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)

        vm.onTitleChange("Dune Messiah")
        vm.onSave()

        val state = vm.uiState.value
        assertFalse(state.isSaving)
        assertEquals(EditBookError.SaveFailed, state.error)
        assertEquals("Dune Messiah", state.title)
        assertTrue(state.canSave)
    }

    // F22: the row vanishing while Edit is open emits NotFound, with no crash.
    @Test
    fun rowDeletedWhileOpen_emitsNotFound() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)

        repository.byId.value = null

        assertEquals(EditBookResult.NotFound, nextResult(vm))
    }

    // F19: process-death restore round-trips every field via SavedStateHandle.
    @Test
    fun restoredFromSavedStateHandle_roundTripsFields() {
        val savedStateHandle = SavedStateHandle(mapOf("bookId" to 1L))
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val original = viewModel(bookId = 1L, savedStateHandle = savedStateHandle)

        original.onTitleChange("Dune Messiah")
        original.onMemoryWhyChange("A road trip")

        val restored = viewModel(bookId = 1L, savedStateHandle = savedStateHandle)
        val state = restored.uiState.value
        assertTrue(state.isLoaded)
        assertEquals("Dune Messiah", state.title)
        assertEquals("A road trip", state.memoryWhy)
        assertEquals(CoverDraft.Stored("covers/original.jpg"), state.cover)
    }

    // F19: a purged image draft on restore falls back to the book's own original cover, not a
    // preset.
    @Test
    fun restoredWithAPurgedImageDraft_fallsBackToTheOriginalStoredCover() {
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

        assertEquals(CoverDraft.Stored("covers/original.jpg"), restored.uiState.value.cover)
    }

    // Cancel/Remove/preset-select after a Stored cover never touches the stored file (E5).
    @Test
    fun presetSelectedOverAStoredCover_doesNotDiscardAnyFile() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)
        assertTrue(vm.uiState.value.cover is CoverDraft.Stored)

        vm.onPresetSelected(CoverPreset.Preset3)

        assertEquals(CoverDraft.Preset(CoverPreset.Preset3), vm.uiState.value.cover)
        assertTrue(coverImages.discarded.isEmpty())
    }

    @Test
    fun removeImageOverAStoredCover_doesNotDiscardAnyFile() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)

        vm.onRemoveImage()

        assertTrue(vm.uiState.value.cover is CoverDraft.Preset)
        assertTrue(coverImages.discarded.isEmpty())
    }

    @Test
    fun dismissRequest_withAStoredCover_doesNotDiscardAnyFile() {
        repository.byId.value = LibraryBook(populatedBook(1L), null)
        val vm = viewModel(bookId = 1L)

        vm.onDismissRequest()

        assertTrue(coverImages.discarded.isEmpty())
    }
}
