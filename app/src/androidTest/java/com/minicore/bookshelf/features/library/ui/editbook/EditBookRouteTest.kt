package com.minicore.bookshelf.features.library.ui.editbook

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.coverimage.CoverImportException
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CurrentDateProvider
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.ObserveBookDetailUseCase
import com.minicore.bookshelf.features.library.domain.RemoveBookUseCase
import com.minicore.bookshelf.features.library.domain.UpdateBookUseCase
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import com.minicore.bookshelf.features.library.ui.bookform.BOOK_FORM_TITLE_FIELD_TEST_TAG
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class EditBookRouteFakeLibraryRepository : LibraryRepository {
    val byId = MutableStateFlow<LibraryBook?>(null)
    var lastUpdated: Book? = null
    var updateCallCount = 0

    /** When set, [updateBook] suspends on this until the test completes it, so a test can assert
     * on in-flight-save UI state. */
    var updateGate: CompletableDeferred<Unit>? = null

    var removeCallCount = 0
    var removeReturnsFalse = false
    var failRemove = false

    /** When set, [removeBook] suspends on this until the test completes it, so a test can assert
     * on in-flight-removing UI state. */
    var removeGate: CompletableDeferred<Unit>? = null

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeLentOutBooks(): Flow<List<LentBook>> = throw NotImplementedError()
    override fun observeCounts(): Flow<LibraryCounts> = throw NotImplementedError()
    override fun observeBook(id: Long): Flow<Book?> = MutableStateFlow(byId.value?.book)
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = byId
    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean {
        updateCallCount++
        updateGate?.await()
        lastUpdated = book
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

private class EditBookRouteFakeCoverImageStore : CoverImageStore {
    /** When set, [importDraft] suspends on this until the test completes it, so a test can
     * assert on in-flight `isImportingImage` UI state. */
    var importGate: CompletableDeferred<Unit>? = null

    override suspend fun importDraft(sourceUri: String): String {
        importGate?.await()
        throw CoverImportException("unused")
    }
    override suspend fun discardDraft(draftRef: String) = Unit
    override suspend fun commit(draftRef: String): String = "covers/new.jpg"
    override suspend fun uncommit(storedRef: String, draftRef: String) = Unit
    override suspend fun delete(storedRef: String) = Unit
    override suspend fun exists(ref: String): Boolean = true
}

/**
 * Compose UI tests for the Edit Book sheet (plan #33 section 7), driven by a directly constructed
 * [EditBookViewModel] (faked repository/cover store, no Hilt/NavHost needed), mirroring
 * `BookDetailRouteTest`'s pattern.
 */
class EditBookRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC)
    private val repository = EditBookRouteFakeLibraryRepository()
    private val coverImages = EditBookRouteFakeCoverImageStore()

    private fun book(id: Long = 1L) = Book(
        id = id,
        title = "Dune",
        author = "Frank Herbert",
        isbn = null,
        cover = Cover.Preset(CoverPreset.Preset1),
        genre = null,
        pageCount = null,
        publishedYear = 1965,
        status = BookStatus.Finished,
        rating = 4,
        synopsis = null,
        comment = "Loved it",
        createdAt = Instant.EPOCH,
    )

    private fun setContent(
        bookId: Long = 1L,
        onDone: (EditBookResult) -> Unit = {},
    ) {
        val viewModel = EditBookViewModel(
            ObserveBookDetailUseCase(repository, CurrentDateProvider(Provider { clock })),
            UpdateBookUseCase(repository, coverImages),
            RemoveBookUseCase(repository, coverImages),
            coverImages,
            clock,
            SavedStateHandle(mapOf("bookId" to bookId)),
        )
        composeRule.setContent {
            BookshelfTheme {
                EditBookRoute(onDone = onDone, viewModel = viewModel)
            }
        }
    }

    // F1: the form pre-fills from the existing book.
    @Test
    fun prefillsFromExistingBook() {
        repository.byId.value = LibraryBook(book(), null)
        setContent()

        composeRule.onNodeWithText("Dune").assertIsDisplayed()
        composeRule.onNodeWithText("Frank Herbert").assertIsDisplayed()
        composeRule.onNodeWithText("Loved it").assertIsDisplayed()
    }

    // AC1/R1: tapping "Remove book" opens the confirm dialog showing the *persisted* title, not
    // an in-progress edit; the sheet stays open and no repository call happens yet.
    @Test
    fun removeBookClick_opensConfirmDialog_showsPersistedTitle_doesNotCallRemoveYet() {
        repository.byId.value = LibraryBook(book(7L), null)
        setContent(bookId = 7L)

        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune II")

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).assertIsEnabled().performClick()

        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_DIALOG_TEST_TAG)
            .assertIsDisplayed()
        // Scoped to the dialog's own subtree: the title field behind it also contains "Dune"
        // (the in-progress "Dune II" edit), so an unscoped text query would match both nodes and
        // would not actually prove which title the dialog shows.
        val insideDialog = hasAnyAncestor(hasTestTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_DIALOG_TEST_TAG))
        composeRule.onNode(hasText("Dune", substring = true) and insideDialog).assertIsDisplayed()
        composeRule.onNode(hasText("Dune II", substring = true) and insideDialog).assertDoesNotExist()
        composeRule.onNodeWithTag(EDIT_BOOK_SHEET_TEST_TAG).assertIsDisplayed()
        assertEquals(0, repository.removeCallCount)
    }

    // AC2: no open loan -> no warning in the dialog.
    @Test
    fun removeBookDialog_noOpenLoan_noWarning() {
        repository.byId.value = LibraryBook(book(), null)
        setContent()

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).performClick()

        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG)
            .assertDoesNotExist()
    }

    // AC2: an open loan shows the warning with the borrower's name, Confirm stays enabled, and
    // confirming still calls removeBook exactly once - the warning informs, it never blocks.
    @Test
    fun removeBookDialog_openLoan_showsWarning_confirmStillRemoves() {
        val loan = OpenLoan(loanId = 1L, borrowerName = "Alex", loanedAt = Instant.EPOCH, dueAt = LocalDate.of(2026, 11, 1))
        repository.byId.value = LibraryBook(book(), loan)
        setContent()

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).performClick()
        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Alex", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG)
            .assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertEquals(1, repository.removeCallCount)
    }

    // AC1/AC2: confirming with no open loan calls removeBook exactly once.
    @Test
    fun removeBookDialog_confirmWithNoOpenLoan_callsRemoveOnce() {
        repository.byId.value = LibraryBook(book(), null)
        setContent()

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).performClick()
        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG)
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, repository.removeCallCount)
    }

    // AC4/R4: Cancel, outside tap (onDismissRequest) and system back on the confirm dialog are
    // all a no-op - unsaved edits survive, and removeBook is never called.
    @Test
    fun removeBookDialog_cancel_leavesUnsavedEditsIntact_noRemoveCall() {
        repository.byId.value = LibraryBook(book(), null)
        setContent()

        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune II")
        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).performClick()

        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_CANCEL_BUTTON_TEST_TAG)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_DIALOG_TEST_TAG)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(EDIT_BOOK_SHEET_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Dune II").assertIsDisplayed()
        assertEquals(0, repository.removeCallCount)
    }

    // AC4/R4: system back while the confirm dialog shows dismisses only the dialog. The dialog's
    // own window consumes the back event first (standard AlertDialog/Dialog behavior - it is
    // above the sheet in the window stack), so the sheet's own BackHandler (which stays enabled;
    // it is only gated on isSaving/isRemoving, not on the dialog) never even sees this back press.
    @Test
    fun removeBookDialog_systemBack_dismissesDialogOnly_sheetStaysOpen() {
        repository.byId.value = LibraryBook(book(), null)
        var result: EditBookResult? = null
        setContent(onDone = { result = it })

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).performClick()
        androidx.test.espresso.Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_DIALOG_TEST_TAG)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(EDIT_BOOK_SHEET_TEST_TAG).assertIsDisplayed()
        assertEquals(0, repository.removeCallCount)
        assertNull(result)
    }

    // R8 (should): RemoveFailed shows in the error slot and re-enables the controls, and a retry
    // succeeds.
    @Test
    fun removeFails_showsErrorInSlot_reEnablesControls_retrySucceeds() {
        repository.byId.value = LibraryBook(book(), null)
        repository.failRemove = true
        var result: EditBookResult? = null
        setContent(onDone = { result = it })

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).performClick()
        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_FAILED_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).assertIsEnabled()
        assertNull(result)

        repository.failRemove = false
        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).performClick()
        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG)
            .performClick()
        composeRule.waitForIdle()

        assertEquals(EditBookResult.Removed, result)
    }

    // AC3/F6: the Borrowing checkbox reflects the live loan state, both directions, and stays
    // disabled throughout (it is a display, never a control).
    @Test
    fun borrowingSection_reflectsLoanState() {
        repository.byId.value = LibraryBook(book(), null)
        setContent()

        composeRule.onNodeWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG).assertIsNotEnabled().assertIsOff()

        val loan = OpenLoan(loanId = 1L, borrowerName = "Alex", loanedAt = Instant.EPOCH, dueAt = LocalDate.of(2026, 11, 1))
        repository.byId.value = LibraryBook(book(), loan)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG).assertIsNotEnabled().assertIsOn()
    }

    // F11: Save persists edits and emits Saved.
    @Test
    fun save_persistsEdits_emitsSaved() {
        repository.byId.value = LibraryBook(book(), null)
        var result: EditBookResult? = null
        setContent(onDone = { result = it })

        composeRule.onNodeWithTag(EDIT_BOOK_SAVE_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(EditBookResult.Saved, result)
        assertTrue(requireNotNull(repository.lastUpdated).title == "Dune")
    }

    // F13: a clean Cancel (no edits) closes immediately, emitting Cancelled, no discard dialog.
    @Test
    fun cancel_whenClean_emitsCancelled_noDiscardDialog() {
        repository.byId.value = LibraryBook(book(), null)
        var result: EditBookResult? = null
        setContent(onDone = { result = it })

        composeRule.onNodeWithTag(EDIT_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(EditBookResult.Cancelled, result)
        composeRule.onNodeWithTag(EDIT_BOOK_DISCARD_DIALOG_TEST_TAG).assertDoesNotExist()
    }

    // AC4: a dirty Cancel shows the discard-confirm dialog first; confirming Discard closes the
    // sheet with Cancelled and never calls updateBook.
    @Test
    fun cancel_whenDirty_showsDiscardDialog_discardEmitsCancelled_noUpdateCall() {
        repository.byId.value = LibraryBook(book(), null)
        var result: EditBookResult? = null
        setContent(onDone = { result = it })

        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune II")
        composeRule.onNodeWithTag(EDIT_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_BOOK_DISCARD_DIALOG_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.waitForIdle()

        assertEquals(EditBookResult.Cancelled, result)
        assertEquals(0, repository.updateCallCount)
    }

    // A "Keep editing" tap on the discard dialog dismisses it and returns to the form with the
    // edit intact - no onDone, no update call.
    @Test
    fun cancel_whenDirty_keepEditing_returnsToFormWithEditsIntact() {
        repository.byId.value = LibraryBook(book(), null)
        var result: EditBookResult? = null
        setContent(onDone = { result = it })

        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune II")
        composeRule.onNodeWithTag(EDIT_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_BOOK_DISCARD_DIALOG_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(EDIT_BOOK_SHEET_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Dune II").assertIsDisplayed()
        assertNull(result)
        assertEquals(0, repository.updateCallCount)
    }

    // F17/F20: "Remove book" is disabled while a save is in flight, so a second write can never
    // race the removal.
    @Test
    fun removeButton_disabledWhileSaveInFlight() {
        repository.byId.value = LibraryBook(book(), null)
        val gate = CompletableDeferred<Unit>()
        repository.updateGate = gate
        setContent()

        composeRule.onNodeWithTag(EDIT_BOOK_SAVE_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).assertIsNotEnabled()

        gate.complete(Unit)
    }

    // Review finding: Remove and Cancel (and, by the same `enabled` gate, the rest of the form)
    // are disabled while an image import is in flight, not just left clickable-but-a-no-op - the
    // UI now matches the EditBookViewModel guards added for AC2/AC4.
    @Test
    fun removeAndCancelButtons_disabledWhileImportingImage() {
        repository.byId.value = LibraryBook(book(), null)
        val importGate = CompletableDeferred<Unit>()
        coverImages.importGate = importGate
        val viewModel = EditBookViewModel(
            ObserveBookDetailUseCase(repository, CurrentDateProvider(Provider { clock })),
            UpdateBookUseCase(repository, coverImages),
            RemoveBookUseCase(repository, coverImages),
            coverImages,
            clock,
            SavedStateHandle(mapOf("bookId" to 1L)),
        )
        composeRule.setContent {
            BookshelfTheme {
                EditBookRoute(onDone = {}, viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { viewModel.onImagePicked("content://new-photo") }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(EDIT_BOOK_CANCEL_BUTTON_TEST_TAG).assertIsNotEnabled()

        importGate.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(EDIT_BOOK_CANCEL_BUTTON_TEST_TAG).assertIsEnabled()
    }

    // F18/F21/F23: at 2x font scale on a 320dp-wide viewport, Remove/Cancel/Save all display
    // without overlapping - the footer wraps via FlowRow rather than clipping or overdrawing.
    @Test
    fun footer_atDoubleFontScaleAndNarrowWidth_allThreeButtonsDisplayWithoutOverlap() {
        repository.byId.value = LibraryBook(book(), null)
        val viewModel = EditBookViewModel(
            ObserveBookDetailUseCase(repository, CurrentDateProvider(Provider { clock })),
            UpdateBookUseCase(repository, coverImages),
            RemoveBookUseCase(repository, coverImages),
            coverImages,
            clock,
            SavedStateHandle(mapOf("bookId" to 1L)),
        )
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f)
                    .then(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp))),
            ) {
                BookshelfTheme {
                    EditBookRoute(onDone = {}, viewModel = viewModel)
                }
            }
        }
        composeRule.waitForIdle()

        val removeBounds = composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val cancelBounds = composeRule.onNodeWithTag(EDIT_BOOK_CANCEL_BUTTON_TEST_TAG).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val saveBounds = composeRule.onNodeWithTag(EDIT_BOOK_SAVE_BUTTON_TEST_TAG).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertFalse("Remove and Cancel must not overlap", removeBounds.overlaps(cancelBounds))
        assertFalse("Remove and Save must not overlap", removeBounds.overlaps(saveBounds))
        assertFalse("Cancel and Save must not overlap", cancelBounds.overlaps(saveBounds))
    }
}
