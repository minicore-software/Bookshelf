package com.minicore.bookshelf.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.HiltTestActivity
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.di.LibraryDataModule
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import com.minicore.bookshelf.features.library.ui.LIBRARY_LENT_ROW_TEST_TAG
import com.minicore.bookshelf.features.library.ui.LIBRARY_READING_ROW_TEST_TAG
import com.minicore.bookshelf.features.library.ui.LIBRARY_SEARCH_BAR_TEST_TAG
import com.minicore.bookshelf.features.library.ui.LIBRARY_SEE_ALL_LOANS_TEST_TAG
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

    private fun book(id: Long, status: BookStatus) = Book(
        id = id,
        title = "Book $id",
        author = "Author",
        isbn = null,
        coverUri = null,
        genre = null,
        pageCount = null,
        publishedYear = null,
        status = status,
        rating = null,
        synopsis = null,
        createdAt = Instant.EPOCH,
    )
}

class FakeLibraryRepository @Inject constructor() : LibraryRepository {
    val reading = MutableStateFlow<List<LibraryBook>>(emptyList())
    val lent = MutableStateFlow<List<LentBook>>(emptyList())

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = reading
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = reading
    override fun observeLentOutBooks(): Flow<List<LentBook>> = lent
    override fun observeCounts(): Flow<LibraryCounts> = MutableStateFlow(LibraryCounts(0, 0, 0, 0, 0))
}
