package com.minicore.bookshelf.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.HiltTestActivity
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.performSemanticsClick
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.di.LibraryDataModule
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.ui.LIBRARY_LENT_ROW_TEST_TAG
import com.minicore.bookshelf.features.library.ui.LIBRARY_READING_ROW_TEST_TAG
import com.minicore.bookshelf.features.library.ui.LIBRARY_SEARCH_BAR_TEST_TAG
import com.minicore.bookshelf.features.library.ui.LIBRARY_SEE_ALL_LOANS_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookdetail.BOOK_DETAIL_CLOSE_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookdetail.BOOK_DETAIL_CLOSE_ICON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookdetail.BOOK_DETAIL_EDIT_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookdetail.BOOK_DETAIL_LEND_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookdetail.BOOK_DETAIL_SCRIM_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookdetail.BOOK_DETAIL_SHEET_TEST_TAG
import com.minicore.bookshelf.features.library.ui.libraryFilterChipTag
import com.minicore.bookshelf.features.library.ui.libraryGridCardTag
import com.minicore.bookshelf.features.library.ui.libraryLentCardTag
import com.minicore.bookshelf.features.library.ui.libraryReadingCardTag
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(LibraryDataModule::class)
@RunWith(AndroidJUnit4::class)
class BookshelfNavGraphTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @BindValue
    @JvmField
    val fakeRepository: LibraryRepository = FakeLibraryRepository()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    // F1: the Home route is the graph's start destination, not a pushed screen.
    @Test
    fun startDestination_isHomeRoute() {
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme {
                BookshelfNavGraph(navController)
            }
        }

        composeRule.waitForIdle()

        assertEquals(
            BookshelfDestinations.Home.route,
            navController.currentDestination?.route
        )
    }

    // F10/AC4(b): tapping the stub search bar is a no-op at the navigation level — the route and
    // back-stack size are unchanged.
    @Test
    fun tappingSearchBar_doesNotChangeTheBackStack() {
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme {
                BookshelfNavGraph(navController)
            }
        }

        composeRule.waitForIdle()
        val backStackSizeBefore = navController.currentBackStack.value.size

        composeRule.onNodeWithTag(LIBRARY_SEARCH_BAR_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(
            BookshelfDestinations.Home.route,
            navController.currentDestination?.route
        )
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)
    }

    // F19/AC4(b): "See all" leaves the route and back-stack size unchanged - it has no Book
    // Detail destination of its own (epic #17 D19).
    @Test
    fun tappingSeeAllLoans_doesNotChangeTheBackStack() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        val lentBook = book(id = 2, status = BookStatus.Reading)
        repo.reading.value = listOf(LibraryBook(readingBook, null))
        repo.lent.value = listOf(
            LentBook(lentBook, OpenLoan(1, "Priya", Instant.EPOCH, LocalDate.now().plusDays(3))),
        )

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme {
                BookshelfNavGraph(navController)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LIBRARY_READING_ROW_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(LIBRARY_LENT_ROW_TEST_TAG).assertExists()

        val backStackSizeBefore = navController.currentBackStack.value.size

        composeRule.onNodeWithTag(LIBRARY_SEE_ALL_LOANS_TEST_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)
    }

    // F15/AC4: tapping the lent-out row's card navigates to book/{bookId} with the right id, and
    // the sheet is shown (mirrors tappingAReadingCard_navigatesToBookDetail_withCorrectId, which
    // covers the currently-reading row; this is the other #20 row kind).
    @Test
    fun tappingALentCard_navigatesToBookDetail_withCorrectId() {
        val repo = fakeRepository as FakeLibraryRepository
        val lentBook = book(id = 2, status = BookStatus.Reading)
        val openLoan = OpenLoan(1, "Priya", Instant.EPOCH, LocalDate.now().plusDays(3))
        repo.lent.value = listOf(LentBook(lentBook, openLoan))
        repo.allBooks.value = listOf(LibraryBook(lentBook, openLoan))

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme {
                BookshelfNavGraph(navController)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(libraryLentCardTag(lentBook.id)).performClick()
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.BookDetail.route, navController.currentDestination?.route)
        assertEquals(lentBook.id, navController.currentBackStackEntry?.arguments?.getLong(BookshelfDestinations.BookDetail.ARG_BOOK_ID))
        composeRule.onNodeWithTag(BOOK_DETAIL_SHEET_TEST_TAG).assertExists()

        navController.popBackStack()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
    }

    // F8/F9 (plan AC4b): tapping a chip leaves the route and back-stack size unchanged - the
    // filter is local UI state, not a navigation event.
    @Test
    fun tappingAFilterChip_doesNotChangeTheBackStack() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        val finishedBook = book(id = 2, status = BookStatus.Finished)
        repo.allBooks.value = listOf(
            LibraryBook(readingBook, null),
            LibraryBook(finishedBook, null),
        )

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme {
                BookshelfNavGraph(navController)
            }
        }
        composeRule.waitForIdle()

        val backStackSizeBefore = navController.currentBackStack.value.size

        composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.Finished)).performScrollTo().performSemanticsClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)
    }

    // F15/AC4: tapping either grid card (a reading-status book and a finished-status book, in
    // turn) navigates to book/{bookId} with the right id, closing the modal between taps.
    @Test
    fun tappingAGridCard_navigatesToBookDetail_withCorrectId() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        val finishedBook = book(id = 2, status = BookStatus.Finished)
        repo.allBooks.value = listOf(
            LibraryBook(readingBook, null),
            LibraryBook(finishedBook, null),
        )

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme {
                BookshelfNavGraph(navController)
            }
        }
        composeRule.waitForIdle()

        // performScrollTo()'s minimal "scroll into view" can land a target right at the
        // viewport's bottom-end corner, which the always-on "Add a book" FAB also occupies
        // (BOOKSHELF-0022) - performSemanticsClick() invokes the same onClick handler without
        // depending on coordinate-based touch dispatch an unrelated overlay can intercept (see
        // ComposeTestActions.performSemanticsClick's KDoc).
        composeRule.onNodeWithTag(libraryGridCardTag(readingBook.id)).performScrollTo().performSemanticsClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.BookDetail.route, navController.currentDestination?.route)
        assertEquals(readingBook.id, navController.currentBackStackEntry?.arguments?.getLong(BookshelfDestinations.BookDetail.ARG_BOOK_ID))

        navController.popBackStack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(libraryGridCardTag(finishedBook.id)).performScrollTo().performSemanticsClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.BookDetail.route, navController.currentDestination?.route)
        assertEquals(finishedBook.id, navController.currentBackStackEntry?.arguments?.getLong(BookshelfDestinations.BookDetail.ARG_BOOK_ID))
    }

    // F15/AC4: tapping a Library card (grid, reading row and lent row) navigates to
    // book/{bookId} with the right id, and the sheet is shown.
    @Test
    fun tappingAReadingCard_navigatesToBookDetail_withCorrectId() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        repo.reading.value = listOf(LibraryBook(readingBook, null))
        repo.allBooks.value = repo.reading.value

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme { BookshelfNavGraph(navController) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(libraryReadingCardTag(readingBook.id)).performClick()
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.BookDetail.route, navController.currentDestination?.route)
        assertEquals(readingBook.id, navController.currentBackStackEntry?.arguments?.getLong(BookshelfDestinations.BookDetail.ARG_BOOK_ID))
        composeRule.onNodeWithTag(BOOK_DETAIL_SHEET_TEST_TAG).assertExists()
    }

    // F16: a fast double tap on the same card leaves exactly one book/{bookId} entry on the
    // back stack (launchSingleTop).
    @Test
    fun doubleTappingACard_leavesExactlyOneDetailEntry() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        repo.reading.value = listOf(LibraryBook(readingBook, null))
        repo.allBooks.value = repo.reading.value

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme { BookshelfNavGraph(navController) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(libraryReadingCardTag(readingBook.id)).performClick()
        composeRule.waitForIdle()
        val backStackSizeAfterFirstTap = navController.currentBackStack.value.size

        // The card is behind the modal now; re-navigating programmatically through the same
        // seam exercises launchSingleTop the way a fast double tap would.
        navController.navigateToBookDetail(readingBook.id)
        composeRule.waitForIdle()

        assertEquals(backStackSizeAfterFirstTap, navController.currentBackStack.value.size)
        assertEquals(BookshelfDestinations.BookDetail.route, navController.currentDestination?.route)
    }

    // F14/AC4: Close, the X icon, the scrim and system back all pop back to Home.
    @Test
    fun closeButton_popsBackToHome() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        repo.reading.value = listOf(LibraryBook(readingBook, null))
        repo.allBooks.value = repo.reading.value

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme { BookshelfNavGraph(navController) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(libraryReadingCardTag(readingBook.id)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BOOK_DETAIL_CLOSE_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
    }

    @Test
    fun closeIcon_popsBackToHome() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        repo.reading.value = listOf(LibraryBook(readingBook, null))
        repo.allBooks.value = repo.reading.value

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme { BookshelfNavGraph(navController) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(libraryReadingCardTag(readingBook.id)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BOOK_DETAIL_CLOSE_ICON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
    }

    @Test
    fun scrimTap_popsBackToHome() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        repo.reading.value = listOf(LibraryBook(readingBook, null))
        repo.allBooks.value = repo.reading.value

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme { BookshelfNavGraph(navController) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(libraryReadingCardTag(readingBook.id)).performClick()
        composeRule.waitForIdle()

        // The scrim and the bottom-aligned sheet are siblings covering the whole composable; the
        // sheet is capped at 92% of height-minus-statusbar, so a default center-click lands on
        // the Surface (which blocks pass-through), not the scrim. Click in the top band - always
        // scrim, never the sheet - instead (critical finding, review round 1).
        composeRule.onNodeWithTag(BOOK_DETAIL_SCRIM_TEST_TAG).performTouchInput {
            click(Offset(centerX, height * 0.03f))
        }
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
    }

    @Test
    fun systemBack_popsBackToHome() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        repo.reading.value = listOf(LibraryBook(readingBook, null))
        repo.allBooks.value = repo.reading.value

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme { BookshelfNavGraph(navController) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(libraryReadingCardTag(readingBook.id)).performClick()
        composeRule.waitForIdle()

        pressBack()
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
    }

    // F13: tapping Edit invokes the no-op navigateToEditBook seam - no crash, route unchanged.
    @Test
    fun tappingEdit_doesNotCrash_routeUnchanged() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        repo.reading.value = listOf(LibraryBook(readingBook, null))
        repo.allBooks.value = repo.reading.value

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme { BookshelfNavGraph(navController) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(libraryReadingCardTag(readingBook.id)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BOOK_DETAIL_EDIT_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.BookDetail.route, navController.currentDestination?.route)
    }

    // AC2 (plan #32): tapping Lend Out invokes the no-op onLendClick seam - no crash, route
    // unchanged (mirrors tappingEdit_doesNotCrash_routeUnchanged).
    @Test
    fun tappingLendOut_routeUnchanged() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        repo.reading.value = listOf(LibraryBook(readingBook, null))
        repo.allBooks.value = repo.reading.value

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme { BookshelfNavGraph(navController) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(libraryReadingCardTag(readingBook.id)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BOOK_DETAIL_LEND_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.BookDetail.route, navController.currentDestination?.route)
    }

    // R6/R7 (plan #34 decision R8): confirming Remove on Edit (stacked over Detail) pops both at
    // once - the user lands on Home, not on a dead Detail. This drives the real onDone handler
    // in BookshelfNavGraph.kt end to end (not a simulated popBackStack sequence): Detail and Edit
    // observe the same repository row, so this is also the only realistic way either order of
    // "Detail self-closes first" vs "Edit's own popBackStack(Detail, inclusive) wins" actually
    // happens - whichever wins, the handler's fallback (popBackStack(EditBook, inclusive) when
    // popBackStack(BookDetail, inclusive) returns false) is exercised or made moot, and either
    // way the assertions below hold. A separate test that calls `navController.popBackStack(...)`
    // directly to force that race would not exercise the handler in BookshelfNavGraph.kt at all,
    // so it is deliberately not duplicated here - see the fallback's own KDoc-style comment at
    // its call site for why it is defence-in-depth rather than a reachable gap.
    @Test
    fun removingABook_popsBothEditAndDetail_landsOnHome() {
        val repo = fakeRepository as FakeLibraryRepository
        val readingBook = book(id = 1, status = BookStatus.Reading)
        repo.allBooks.value = listOf(LibraryBook(readingBook, null))

        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme { BookshelfNavGraph(navController) }
        }
        composeRule.waitForIdle()

        navController.navigateToBookDetail(readingBook.id)
        composeRule.waitForIdle()
        navController.navigateToEditBook(readingBook.id)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.editbook.EDIT_BOOK_REMOVE_BUTTON_TEST_TAG)
            .performClick()
        composeRule.onNodeWithTag(com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG)
            .performClick()
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(1, navController.currentBackStack.value.count { it.destination.route == BookshelfDestinations.Home.route })
        assertEquals(0, navController.currentBackStack.value.count { it.destination.route == BookshelfDestinations.BookDetail.route })
        assertEquals(0, navController.currentBackStack.value.count { it.destination.route == BookshelfDestinations.EditBook.route })
    }

    private fun book(id: Long, status: BookStatus) = Book(
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
}

/**
 * Status-aware (plan Q1/F8): [reading] and [lent] still feed #20's currently-reading and
 * lent-out rows exactly as before, but [allBooks] is the single seed for [observeBooks] and the
 * grid's counts - it is never the same list for every status, unlike a naive fake.
 */
class FakeLibraryRepository @Inject constructor() : LibraryRepository {
    val reading = MutableStateFlow<List<LibraryBook>>(emptyList())
    val lent = MutableStateFlow<List<LentBook>>(emptyList())
    val allBooks = MutableStateFlow<List<LibraryBook>>(emptyList())

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> =
        allBooks.map { all -> if (status == null) all else all.filter { it.book.status == status } }

    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = reading
    override fun observeLentOutBooks(): Flow<List<LentBook>> = lent
    override fun observeCounts(): Flow<LibraryCounts> = combine(allBooks, lent) { books, lentBooks ->
        LibraryCounts(
            all = books.size,
            reading = books.count { it.book.status == BookStatus.Reading },
            finished = books.count { it.book.status == BookStatus.Finished },
            wantToRead = books.count { it.book.status == BookStatus.WantToRead },
            lentOut = lentBooks.size,
        )
    }

    override fun observeBook(id: Long): Flow<Book?> =
        allBooks.map { all -> all.find { it.book.id == id }?.book }
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> =
        allBooks.map { all -> all.find { it.book.id == id } }

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()

    override suspend fun removeBook(id: Long): Boolean {
        val existed = allBooks.value.any { it.book.id == id }
        allBooks.value = allBooks.value.filterNot { it.book.id == id }
        lent.value = lent.value.filterNot { it.book.id == id }
        reading.value = reading.value.filterNot { it.book.id == id }
        return existed
    }
}
