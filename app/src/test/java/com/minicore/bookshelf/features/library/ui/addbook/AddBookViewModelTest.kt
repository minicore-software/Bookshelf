package com.minicore.bookshelf.features.library.ui.addbook

import androidx.lifecycle.SavedStateHandle
import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.coverimage.CoverImportException
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.AddBookUseCase
import com.minicore.bookshelf.features.library.domain.CoverDraft
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

private class FakeLibraryRepository : LibraryRepository {
    var failInsert = false
    var insertCount = 0

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = MutableStateFlow(emptyList())
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = MutableStateFlow(emptyList())
    override fun observeLentOutBooks(): Flow<List<LentBook>> = MutableStateFlow(emptyList())
    override fun observeCounts(): Flow<LibraryCounts> = MutableStateFlow(LibraryCounts(0, 0, 0, 0, 0))
    override fun observeBook(id: Long): Flow<Book?> = MutableStateFlow(null)
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = MutableStateFlow(null)

    override suspend fun addBook(book: Book): Long {
        insertCount++
        if (failInsert) throw IllegalStateException("insert failed")
        return insertCount.toLong()
    }

    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()
}

private class FakeCoverImageStore : CoverImageStore {
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

@OptIn(ExperimentalCoroutinesApi::class)
class AddBookViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeLibraryRepository()
    private val coverImages = FakeCoverImageStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = AddBookViewModel(
        AddBookUseCase(repository, coverImages, clock),
        coverImages,
        clock,
        savedStateHandle,
    )

    // F1-F3: title gates canSave; whitespace-only counts as empty.
    @Test
    fun canSave_falseForEmptyOrWhitespaceTitle_trueOtherwise() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canSave)

        vm.onTitleChange("   ")
        assertFalse(vm.uiState.value.canSave)

        vm.onTitleChange("Dune")
        assertTrue(vm.uiState.value.canSave)
    }

    // F6-F8: unset rating vs 1 star, tap-to-clear.
    @Test
    fun rating_tapSetsIt_tapSameStarClearsIt() {
        val vm = viewModel()
        assertNull(vm.uiState.value.rating)

        vm.onRatingTap(3)
        assertEquals(3, vm.uiState.value.rating)

        vm.onRatingTap(3)
        assertNull(vm.uiState.value.rating)

        vm.onRatingTap(1)
        assertEquals(1, vm.uiState.value.rating)
    }

    // F9: preset -> image -> preset ends as Preset, discarding the image draft.
    @Test
    fun switchingFromImageToPreset_discardsTheImageDraft() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onImagePicked("content://picked")
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.cover is CoverDraft.Image)

        vm.onPresetSelected(CoverPreset.Preset3)
        dispatcher.scheduler.runCurrent()

        assertEquals(CoverDraft.Preset(CoverPreset.Preset3), vm.uiState.value.cover)
        assertEquals(listOf(coverImages.nextDraftRef), coverImages.discarded)
    }

    // F10/F11: Remove reverts to the last-selected preset and discards the image draft.
    @Test
    fun removeImage_revertsToLastPreset() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onPresetSelected(CoverPreset.Preset4)
        vm.onImagePicked("content://picked")
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.cover is CoverDraft.Image)

        vm.onRemoveImage()
        dispatcher.scheduler.runCurrent()

        assertEquals(CoverDraft.Preset(CoverPreset.Preset4), vm.uiState.value.cover)
        assertEquals(listOf(coverImages.nextDraftRef), coverImages.discarded)
    }

    // Default last preset, with no preset explicitly chosen, is Preset1.
    @Test
    fun removeImage_withNoPriorPresetChoice_revertsToPreset1() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onImagePicked("content://picked")
        dispatcher.scheduler.runCurrent()

        vm.onRemoveImage()
        dispatcher.scheduler.runCurrent()

        assertEquals(CoverDraft.Preset(CoverPreset.Preset1), vm.uiState.value.cover)
    }

    // Image import failure: keeps the previous cover and sets ImageImportFailed.
    @Test
    fun imageImportFailure_keepsPreviousCover_setsError() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onPresetSelected(CoverPreset.Preset2)
        coverImages.failImport = true

        vm.onImagePicked("content://picked")
        dispatcher.scheduler.runCurrent()

        assertEquals(CoverDraft.Preset(CoverPreset.Preset2), vm.uiState.value.cover)
        assertEquals(AddBookError.ImageImportFailed, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isImportingImage)
    }

    // Top suggestion #2: if the user picks a different cover while an import is still in flight,
    // the import's success must not clobber that newer choice - the freshly imported draft is
    // discarded instead.
    @Test
    fun imagePicked_userPicksAPresetMidImport_successDoesNotOverrideTheNewerChoice() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onImagePicked("content://picked")
        vm.onPresetSelected(CoverPreset.Preset5)
        dispatcher.scheduler.runCurrent()

        assertEquals(CoverDraft.Preset(CoverPreset.Preset5), vm.uiState.value.cover)
        assertFalse(vm.uiState.value.isImportingImage)
        assertEquals(listOf(coverImages.nextDraftRef), coverImages.discarded)
    }

    // canSave is false while an import is in flight.
    @Test
    fun canSave_falseWhileImporting() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTitleChange("Dune")
        vm.onImagePicked("content://picked")

        assertTrue(vm.uiState.value.isImportingImage)
        assertFalse(vm.uiState.value.canSave)

        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.canSave)
    }

    // F15/F16, FF-A2: two onSave() calls without a suspension in between insert exactly once.
    @Test
    fun doubleSave_insertsExactlyOnce() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTitleChange("Dune")

        vm.onSave()
        vm.onSave()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, repository.insertCount)
    }

    // F17: back/cancel/scrim are no-ops while saving (A9).
    @Test
    fun dismissRequest_ignoredWhileSaving() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTitleChange("Dune")
        vm.onSave()

        assertTrue(vm.uiState.value.isSaving)
        vm.onDismissRequest()
        assertTrue(vm.uiState.value.isSaving)

        dispatcher.scheduler.runCurrent()
    }

    // Optional finding (round 2): onDismissRequest and onSave racing in the same frame - before
    // either suspends - must not both go through. onDismissRequest discards the draft cover
    // synchronously (its own coroutine launch happens before onSave's body runs in this
    // single-threaded test dispatcher), so a save that still ran after it would insert a book
    // referencing a cover that is about to be (or already was) discarded.
    @Test
    fun dismissThenSave_inTheSameFrame_doesNotInsert() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTitleChange("Dune")

        vm.onDismissRequest()
        vm.onSave()
        dispatcher.scheduler.runCurrent()

        assertEquals(0, repository.insertCount)
        assertFalse(vm.uiState.value.isSaving)
    }

    // Save failure: fields are kept and the button re-enables.
    @Test
    fun saveFailure_keepsFields_reenablesSave() = runTest(dispatcher) {
        repository.failInsert = true
        val vm = viewModel()
        vm.onTitleChange("Dune")
        vm.onAuthorChange("Frank Herbert")

        vm.onSave()
        dispatcher.scheduler.runCurrent()

        val state = vm.uiState.value
        assertFalse(state.isSaving)
        assertEquals(AddBookError.SaveFailed, state.error)
        assertEquals("Dune", state.title)
        assertEquals("Frank Herbert", state.author)
        assertTrue(state.canSave)
    }

    // Top suggestion #3: two dismiss taps in the same frame must not both send Cancelled (which
    // would otherwise risk popping the back stack twice).
    @Test
    fun doubleDismissRequest_emitsCancelledExactlyOnce() = runTest(dispatcher) {
        val vm = viewModel()
        val results = mutableListOf<AddBookResult>()
        val job = CoroutineScope(dispatcher).launch { vm.onDone.collect { results += it } }

        vm.onDismissRequest()
        vm.onDismissRequest()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(AddBookResult.Cancelled), results)
        job.cancel()
    }

    // AC5: Cancel discards - no insert, draft image discarded, Cancelled emitted.
    @Test
    fun dismissRequest_whenClean_emitsCancelled_noInsert() = runTest(dispatcher) {
        val vm = viewModel()
        var result: AddBookResult? = null
        val job = CoroutineScope(dispatcher).launch { result = vm.onDone.first() }

        vm.onDismissRequest()
        dispatcher.scheduler.runCurrent()

        assertEquals(AddBookResult.Cancelled, result)
        assertEquals(0, repository.insertCount)
        job.cancel()
    }

    @Test
    fun dismissRequest_withImagePicked_discardsTheDraftFile() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onImagePicked("content://picked")
        dispatcher.scheduler.runCurrent()

        vm.onDismissRequest()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(coverImages.nextDraftRef), coverImages.discarded)
    }

    // Year stepper: first tap on empty seeds the current year (from the injected Clock).
    @Test
    fun yearStep_onEmptyField_seedsCurrentYear() {
        val vm = viewModel()
        vm.onYearStep(-1)
        assertEquals("2026", vm.uiState.value.yearText)
    }

    @Test
    fun yearStep_onZero_reseedsRatherThanStepping() {
        val vm = viewModel()
        vm.onYearTextChange("0")
        vm.onYearStep(1)
        assertEquals("2026", vm.uiState.value.yearText)
    }

    @Test
    fun yearStep_stepsAndClampsToRange() {
        val vm = viewModel()
        vm.onYearTextChange("9999")
        vm.onYearStep(1)
        assertEquals("9999", vm.uiState.value.yearText)

        vm.onYearTextChange("1")
        vm.onYearStep(-1)
        assertEquals("1", vm.uiState.value.yearText)
    }

    @Test
    fun yearTextChange_rejectsNonDigitsAndTooLong() {
        val vm = viewModel()
        vm.onYearTextChange("19a5")
        assertEquals("", vm.uiState.value.yearText)

        vm.onYearTextChange("19999")
        assertEquals("", vm.uiState.value.yearText)

        vm.onYearTextChange("1995")
        assertEquals("1995", vm.uiState.value.yearText)
    }

    // F29/F30: trimming happens in the use case at save time, not live (plan A6/4.2).
    @Test
    fun fieldsAreNotTrimmedWhileEditing() {
        val vm = viewModel()
        vm.onTitleChange("  Dune  ")
        assertEquals("  Dune  ", vm.uiState.value.title)
    }

    // F23/F24: process-death restore round-trips the draft; a missing draft file falls back to
    // the last preset.
    @Test
    fun restoredFromSavedStateHandle_roundTripsFields() {
        val savedStateHandle = SavedStateHandle()
        val original = viewModel(savedStateHandle)
        original.onTitleChange("Dune")
        original.onAuthorChange("Frank Herbert")
        original.onYearTextChange("1965")
        original.onRatingTap(4)
        original.onCommentChange("A classic")
        original.onPresetSelected(CoverPreset.Preset5)

        val restored = viewModel(savedStateHandle)
        val state = restored.uiState.value
        assertEquals("Dune", state.title)
        assertEquals("Frank Herbert", state.author)
        assertEquals("1965", state.yearText)
        assertEquals(4, state.rating)
        assertEquals("A classic", state.comment)
        assertEquals(CoverDraft.Preset(CoverPreset.Preset5), state.cover)
    }

    @Test
    fun restoredWithAMissingDraftFile_fallsBackToLastPreset() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle()
        savedStateHandle["cover_draft_ref"] = "cover-drafts/does-not-exist.jpg"
        savedStateHandle["last_preset"] = CoverPreset.Preset2.name
        coverImages.draftExists = false

        val restored = viewModel(savedStateHandle)
        // Optimistic initial value trusts the ref synchronously...
        assertTrue(restored.uiState.value.cover is CoverDraft.Image)

        dispatcher.scheduler.runCurrent() // ...then self-corrects once `exists` resolves.

        assertEquals(CoverDraft.Preset(CoverPreset.Preset2), restored.uiState.value.cover)
    }
}
