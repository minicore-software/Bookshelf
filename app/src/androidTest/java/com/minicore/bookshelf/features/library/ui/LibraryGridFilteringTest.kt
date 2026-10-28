package com.minicore.bookshelf.features.library.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.minicore.bookshelf.HiltTestActivity
import com.minicore.bookshelf.performSemanticsClick
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.di.LibraryDataModule
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * AC1/AC2 end to end (plan section 8): a status-aware fake repository, hosted through the real
 * [LibraryRoute] + [LibraryViewModel] + [com.minicore.bookshelf.features.library.domain.ObserveLibraryGridUseCase],
 * proves live counts and the lent-out join path - never [LibraryRepository.observeBooks].
 */
@HiltAndroidTest
@UninstallModules(LibraryDataModule::class)
@RunWith(AndroidJUnit4::class)
class LibraryGridFilteringTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @BindValue
    @JvmField
    val fakeRepository: LibraryRepository = StatusAwareFakeLibraryRepository()

    private val repo get() = fakeRepository as StatusAwareFakeLibraryRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun book(id: Long, title: String, status: BookStatus) = Book(
        id = id,
        title = title,
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

    private fun setContent() {
        composeRule.setContent {
            BookshelfTheme {
                LibraryRoute(onSearchClick = {}, onBookClick = {}, onSeeAllLoansClick = {}, onAddBookClick = {})
            }
        }
    }

    // The filter chip row and the grid both scroll (plan G10/G11) and can be taller than the
    // device viewport once #20's rows are above them, so both the chip tap and the card check
    // need to scroll their target into view - Compose's lazy layouts only compose (and
    // hit-test) nodes near the viewport.
    //
    // With both #20's rows seeded, the chip row's minimal "scroll into view" position can
    // coincide with the "Add a book" FAB's fixed bottom-end corner (BOOKSHELF-0022) - see
    // ComposeTestActions.performSemanticsClick's KDoc.
    private fun tapChip(filter: LibraryFilter) {
        composeRule.onNodeWithTag(libraryFilterChipTag(filter))
            .performScrollTo()
            .performSemanticsClick()
    }

    private val gridCardTagPrefixMatcher = SemanticsMatcher("TestTag starts with 'library_grid_card_'") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library_grid_card_") == true
    }

    // Scrolls all the way to the end of the grid (the seed is kept small enough to fit one
    // viewport once scrolled there) and asserts the exact set of composed grid cards - stronger
    // than individual assertDoesNotExist calls, which a lazy container could satisfy for the
    // wrong reason (an item simply not yet scrolled into view, rather than genuinely excluded).
    private fun assertExactlyTheseCardsAreComposed(vararg bookIds: Long) {
        composeRule.onNodeWithTag(LIBRARY_BODY_TEST_TAG)
            .performScrollToNode(hasTestTag(LIBRARY_GRID_BOTTOM_TEST_TAG))
        composeRule.onAllNodes(gridCardTagPrefixMatcher)
            .assertCountEquals(bookIds.size)
        for (bookId in bookIds) {
            composeRule.onNodeWithTag(libraryGridCardTag(bookId)).assertExists()
        }
    }


    // AC1: live counts - pushing a new emission into the fake updates the chip text with no
    // interaction required.
    @Test
    fun counts_updateLive_withNoInteraction() {
        val r1 = book(1, "Reading One", BookStatus.Reading)
        val f1 = book(2, "Finished One", BookStatus.Finished)
        repo.seed(
            books = listOf(LibraryBook(r1, null), LibraryBook(f1, OpenLoan(1, "Priya", Instant.EPOCH, LocalDate.now()))),
        )
        setContent()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Lent out · 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lent out · 1").assertExists()

        val f2 = book(3, "Finished Two", BookStatus.Finished)
        repo.seed(
            books = listOf(
                LibraryBook(r1, null),
                LibraryBook(f1, OpenLoan(1, "Priya", Instant.EPOCH, LocalDate.now())),
                LibraryBook(f2, OpenLoan(2, "Diego", Instant.EPOCH, LocalDate.now())),
            ),
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Lent out · 2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lent out · 2").assertExists()
    }

    // AC2, the critical end-to-end proof: the "Lent out" chip shows only books with an open
    // loan, via the join - a Finished book keeps its "Lent to Priya" caption and lent dot under
    // every chip, never only under "Lent out".
    @Test
    fun tappingChips_filtersByTheCorrectQuery_lentOutUsesTheOpenLoanJoin() {
        val reading = book(1, "Reading Book", BookStatus.Reading)
        val finishedLent = book(2, "Finished Lent Book", BookStatus.Finished)
        val finishedPlain = book(3, "Finished Plain Book", BookStatus.Finished)
        val wantToRead = book(4, "Want Book", BookStatus.WantToRead)
        val loan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.now())

        repo.seed(
            books = listOf(
                LibraryBook(reading, null),
                LibraryBook(finishedLent, loan),
                LibraryBook(finishedPlain, null),
                LibraryBook(wantToRead, null),
            ),
        )
        setContent()
        composeRule.waitForIdle()

        tapChip(LibraryFilter.LentOut)
        assertExactlyTheseCardsAreComposed(finishedLent.id)

        tapChip(LibraryFilter.Finished)
        assertExactlyTheseCardsAreComposed(finishedLent.id, finishedPlain.id)
        composeRule.onNodeWithTag(libraryGridLentCaptionTag(finishedLent.id), useUnmergedTree = true).assertExists()
        composeRule.onNode(
            SemanticsMatcher.expectValue(LibraryStatusDotKindKey, StatusDotKind.Lent)
                and hasAnyAncestor(hasTestTag(libraryGridCardTag(finishedLent.id))),
            useUnmergedTree = true,
        ).assertExists()

        tapChip(LibraryFilter.WantToRead)
        assertExactlyTheseCardsAreComposed(wantToRead.id)

        tapChip(LibraryFilter.All)
        assertExactlyTheseCardsAreComposed(reading.id, finishedLent.id, finishedPlain.id, wantToRead.id)
    }
}

/**
 * A single source-of-truth seed list drives every repository method, so it is impossible for
 * "Lent out" to accidentally fall back to [LibraryRepository.observeBooks] and still pass: the
 * open-loan join is the only path to [LibraryRepository.observeLentOutBooks] (plan Q1/Q2).
 */
class StatusAwareFakeLibraryRepository @Inject constructor() : LibraryRepository {

    private val books = MutableStateFlow<List<LibraryBook>>(emptyList())

    fun seed(books: List<LibraryBook>) {
        this.books.value = books
    }

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> =
        books.map { all -> if (status == null) all else all.filter { it.book.status == status } }

    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> =
        books.map { all -> all.filter { it.book.status == BookStatus.Reading } }

    override fun observeLentOutBooks(): Flow<List<LentBook>> =
        books.map { all -> all.mapNotNull { libraryBook -> libraryBook.openLoan?.let { LentBook(libraryBook.book, it) } } }

    override fun observeCounts(): Flow<LibraryCounts> = books.map { all ->
        LibraryCounts(
            all = all.size,
            reading = all.count { it.book.status == BookStatus.Reading },
            finished = all.count { it.book.status == BookStatus.Finished },
            wantToRead = all.count { it.book.status == BookStatus.WantToRead },
            lentOut = all.count { it.openLoan != null },
        )
    }

    override fun observeBook(id: Long): Flow<Book?> = throw NotImplementedError()
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = throw NotImplementedError()

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()
    override suspend fun removeBook(id: Long): Boolean = throw NotImplementedError()
}
