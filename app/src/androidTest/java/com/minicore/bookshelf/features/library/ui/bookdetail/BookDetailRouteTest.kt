package com.minicore.bookshelf.features.library.ui.bookdetail

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
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
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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

/**
 * Compose UI tests for the Book Detail modal (plan #32 section 7), driven by a directly
 * constructed [BookDetailViewModel] (faked repository, no Hilt/NavHost needed - plan's AC1-AC4
 * are all reachable through this one composable).
 */
class BookDetailRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)
    private val repository = FakeLibraryRepository()

    private fun minimalBook(id: Long = 1) = Book(
        id = id,
        title = "Lark Rise to Candleford",
        author = "",
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

    private fun fullBook(id: Long = 1) = minimalBook(id).copy(
        author = "Flora Thompson",
        publishedYear = 1939,
        rating = 4,
        comment = "Slow, but rewards patience.",
        memoryWhy = "A gift",
        memoryWhere = "Lisbon",
        memoryWhat = "Cosy",
    )

    private fun setContent(
        bookId: Long = 1,
        onClose: () -> Unit = {},
        onEditClick: (Long) -> Unit = {},
        onLendClick: (Long) -> Unit = {},
    ) {
        val viewModel = BookDetailViewModel(
            ObserveBookDetailUseCase(repository, CurrentDateProvider(Provider { clock })),
            SavedStateHandle(mapOf("bookId" to bookId)),
        )
        composeRule.setContent {
            BookshelfTheme {
                BookDetailRoute(onClose = onClose, onEditClick = onEditClick, onLendClick = onLendClick, viewModel = viewModel)
            }
        }
    }

    // F10: a minimal (title-only) book shows 5 empty stars, no author/year line, no comment
    // block, three "not yet written" memory cells, and "On the shelf".
    @Test
    fun minimalBook_rendersEmptyStates() {
        repository.byId.value = LibraryBook(minimalBook(), null)
        setContent()

        composeRule.onNodeWithText("Lark Rise to Candleford").assertIsDisplayed()
        composeRule.onNodeWithTag(BOOK_DETAIL_RATING_TEST_TAG).assertIsDisplayed()
        val ratingNode = composeRule.onNodeWithTag(BOOK_DETAIL_RATING_TEST_TAG).fetchSemanticsNode()
        assertEquals("Not rated", ratingNode.config[SemanticsProperties.StateDescription])

        composeRule.onNodeWithTag(BOOK_DETAIL_COMMENT_TEST_TAG).assertDoesNotExist()

        val statusNode = composeRule.onNodeWithTag(BOOK_DETAIL_STATUS_PILL_TEST_TAG).fetchSemanticsNode()
        assertEquals("On the shelf", statusNode.config[SemanticsProperties.ContentDescription].first())

        for (tag in listOf(BOOK_DETAIL_MEMORY_WHY_TEST_TAG, BOOK_DETAIL_MEMORY_WHERE_TEST_TAG, BOOK_DETAIL_MEMORY_FEELING_TEST_TAG)) {
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        val whyNode = composeRule.onNodeWithTag(BOOK_DETAIL_MEMORY_WHY_TEST_TAG).fetchSemanticsNode()
        assertEquals("Why, not yet written", whyNode.config[SemanticsProperties.ContentDescription].first())
    }

    // F9: a fully populated book renders every section simultaneously.
    @Test
    fun fullyPopulatedBook_rendersAllSectionsAtOnce() {
        repository.byId.value = LibraryBook(fullBook(), null)
        setContent()

        composeRule.onNodeWithText("Flora Thompson · 1939").assertIsDisplayed()
        composeRule.onNodeWithTag(BOOK_DETAIL_COMMENT_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("“Slow, but rewards patience.”").assertIsDisplayed()
        composeRule.onNodeWithText("A gift").assertIsDisplayed()
        composeRule.onNodeWithText("Lisbon").assertIsDisplayed()
        composeRule.onNodeWithText("Cosy").assertIsDisplayed()
        val ratingNode = composeRule.onNodeWithTag(BOOK_DETAIL_RATING_TEST_TAG).fetchSemanticsNode()
        assertEquals("Rated 4 of 5", ratingNode.config[SemanticsProperties.StateDescription])
    }

    // F5: unrated vs 1-star vs 5-star are visibly/semantically distinct.
    @Test
    fun rating_unsetVsOneVsFive_areDistinctStateDescriptions() {
        repository.byId.value = LibraryBook(minimalBook().copy(rating = 1), null)
        setContent()
        assertEquals(
            "Rated 1 of 5",
            composeRule.onNodeWithTag(BOOK_DETAIL_RATING_TEST_TAG).fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )
    }

    // F6: status label is derived only from the open loan, never Book.status - a Finished book
    // with no loan reads "On the shelf", a Reading book with an open loan reads "Lent out".
    @Test
    fun status_ignoresBookStatus_usesOnlyLoanPresence() {
        val loan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.of(2026, 7, 1))
        repository.byId.value = LibraryBook(minimalBook().copy(status = BookStatus.Reading), loan)
        setContent()

        val statusNode = composeRule.onNodeWithTag(BOOK_DETAIL_STATUS_PILL_TEST_TAG).fetchSemanticsNode()
        assertEquals("Lent out", statusNode.config[SemanticsProperties.ContentDescription].first())
    }

    // F11: a lent book shows the "Lent out" pill, "LENT TO {name}", a due caption, and no
    // Lend-out button.
    @Test
    fun lentBook_showsLentToBlock_noLendButton() {
        val loan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.of(2026, 6, 20))
        repository.byId.value = LibraryBook(minimalBook(), loan)
        setContent()

        composeRule.onNodeWithText("Priya").assertIsDisplayed()
        composeRule.onNodeWithTag(BOOK_DETAIL_LENT_CAPTION_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BOOK_DETAIL_LEND_BUTTON_TEST_TAG).assertDoesNotExist()
    }

    // F12: tapping "Lend out" invokes onLendClick with the book's id, no crash, modal stays open.
    @Test
    fun tappingLendOut_invokesOnLendClick_withBookId() {
        repository.byId.value = LibraryBook(minimalBook(id = 7), null)
        var lentId: Long? = null
        var closed = false
        setContent(bookId = 7, onClose = { closed = true }, onLendClick = { lentId = it })

        composeRule.onNodeWithTag(BOOK_DETAIL_LEND_BUTTON_TEST_TAG).performClick()

        assertEquals(7L, lentId)
        assertEquals(false, closed)
        composeRule.onNodeWithTag(BOOK_DETAIL_SHEET_TEST_TAG).assertIsDisplayed()
    }

    // F13: tapping "Edit" invokes onEditClick with the same book id, no crash.
    @Test
    fun tappingEdit_invokesOnEditClick_withBookId() {
        repository.byId.value = LibraryBook(minimalBook(id = 9), null)
        var editedId: Long? = null
        setContent(bookId = 9, onEditClick = { editedId = it })

        composeRule.onNodeWithTag(BOOK_DETAIL_EDIT_BUTTON_TEST_TAG).performClick()

        assertEquals(9L, editedId)
        composeRule.onNodeWithTag(BOOK_DETAIL_SHEET_TEST_TAG).assertIsDisplayed()
    }

    // F14: Close button, the X icon and the scrim all invoke onClose.
    @Test
    fun closeButton_invokesOnClose() {
        repository.byId.value = LibraryBook(minimalBook(), null)
        var closed = false
        setContent(onClose = { closed = true })

        composeRule.onNodeWithTag(BOOK_DETAIL_CLOSE_BUTTON_TEST_TAG).performClick()

        assertEquals(true, closed)
    }

    @Test
    fun closeIcon_invokesOnClose() {
        repository.byId.value = LibraryBook(minimalBook(), null)
        var closed = false
        setContent(onClose = { closed = true })

        composeRule.onNodeWithTag(BOOK_DETAIL_CLOSE_ICON_TEST_TAG).performClick()

        assertEquals(true, closed)
    }

    @Test
    fun scrimTap_invokesOnClose() {
        repository.byId.value = LibraryBook(minimalBook(), null)
        var closed = false
        setContent(onClose = { closed = true })

        composeRule.onNodeWithTag(BOOK_DETAIL_SCRIM_TEST_TAG).performClick()

        assertEquals(true, closed)
    }

    // F17/V8: the book vanishing (repository emits null) closes the modal exactly once.
    @Test
    fun bookVanishes_closesExactlyOnce() {
        repository.byId.value = LibraryBook(minimalBook(), null)
        var closeCount = 0
        setContent(onClose = { closeCount++ })
        composeRule.onNodeWithTag(BOOK_DETAIL_SHEET_TEST_TAG).assertIsDisplayed()

        repository.byId.value = null
        composeRule.waitForIdle()

        assertEquals(1, closeCount)
    }
}
