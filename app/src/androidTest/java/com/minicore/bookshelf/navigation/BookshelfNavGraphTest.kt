package com.minicore.bookshelf.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    // F19/AC4(b): with one reading and one lent book from a @BindValue fake repository, tapping a
    // card and tapping "See all" each leave the route and back-stack size unchanged.
    @Test
    fun populatedRows_tappingCardOrSeeAll_doesNotChangeTheBackStack() {
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

        composeRule.onNodeWithTag(libraryReadingCardTag(readingBook.id)).performClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)

        composeRule.onNodeWithTag(libraryLentCardTag(lentBook.id)).performClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)

        composeRule.onNodeWithTag(LIBRARY_SEE_ALL_LOANS_TEST_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)
    }

    // F8/F9 (plan AC4b): tapping a grid card, and tapping a chip, each leave the route and
    // back-stack size unchanged - grid taps and chip taps are both no-ops at the nav level.
    @Test
    fun populatedGrid_tappingCardOrChip_doesNotChangeTheBackStack() {
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

        // performScrollTo()'s minimal "scroll into view" can land a target right at the
        // viewport's bottom-end corner, which the always-on "Add a book" FAB also occupies
        // (BOOKSHELF-0022) - performSemanticsClick() invokes the same onClick handler without
        // depending on coordinate-based touch dispatch an unrelated overlay can intercept (see
        // ComposeTestActions.performSemanticsClick's KDoc).
        composeRule.onNodeWithTag(libraryGridCardTag(readingBook.id)).performScrollTo().performSemanticsClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)

        composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.Finished)).performScrollTo().performSemanticsClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)

        composeRule.onNodeWithTag(libraryGridCardTag(finishedBook.id)).performScrollTo().performSemanticsClick()
        composeRule.waitForIdle()
        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)
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

        composeRule.onNodeWithTag(BOOK_DETAIL_SCRIM_TEST_TAG).performClick()
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

    override fun observeBook(id: Long): Flow<Book?> = throw NotImplementedError()
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> =
        allBooks.map { all -> all.find { it.book.id == id } }

    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()
}
