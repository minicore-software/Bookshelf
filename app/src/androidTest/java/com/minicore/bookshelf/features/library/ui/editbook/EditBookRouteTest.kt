package com.minicore.bookshelf.features.library.ui.editbook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.minicore.bookshelf.features.library.domain.UpdateBookUseCase
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class EditBookRouteFakeLibraryRepository : LibraryRepository {
    val byId = MutableStateFlow<LibraryBook?>(null)
    var lastUpdated: Book? = null

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeLentOutBooks(): Flow<List<LentBook>> = throw NotImplementedError()
    override fun observeCounts(): Flow<LibraryCounts> = throw NotImplementedError()
    override fun observeBook(id: Long): Flow<Book?> = MutableStateFlow(byId.value?.book)
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = byId
    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean {
        lastUpdated = book
        byId.value = byId.value?.copy(book = book)
        return true
    }
}

private class EditBookRouteFakeCoverImageStore : CoverImageStore {
    override suspend fun importDraft(sourceUri: String): String = throw CoverImportException("unused")
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
        onRemoveClick: (Long) -> Unit = {},
    ) {
        val viewModel = EditBookViewModel(
            ObserveBookDetailUseCase(repository, CurrentDateProvider(Provider { clock })),
            UpdateBookUseCase(repository, coverImages),
            coverImages,
            clock,
            SavedStateHandle(mapOf("bookId" to bookId)),
        )
        composeRule.setContent {
            BookshelfTheme {
                EditBookRoute(onDone = onDone, onRemoveClick = onRemoveClick, viewModel = viewModel)
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

    // F20: "Remove book" is displayed, enabled, and tapping it calls onRemoveClick(bookId) only.
    @Test
    fun removeBook_callsOnRemoveClickOnly_sheetStaysOpen() {
        repository.byId.value = LibraryBook(book(7L), null)
        var removedId: Long? = null
        setContent(bookId = 7L, onRemoveClick = { removedId = it })

        composeRule.onNodeWithTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG).assertIsEnabled().performClick()

        assertEquals(7L, removedId)
        composeRule.onNodeWithTag(EDIT_BOOK_SHEET_TEST_TAG).assertIsDisplayed()
    }

    // AC3/F6: the Borrowing checkbox reflects the live loan state.
    @Test
    fun borrowingSection_reflectsLoanState() {
        repository.byId.value = LibraryBook(book(), null)
        setContent()

        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.editbook.BORROWING_CHECKBOX_ROW_TEST_TAG)
            .assertIsNotEnabled()
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
}
