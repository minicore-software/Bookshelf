package com.minicore.bookshelf.features.library.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.HiltTestActivity
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.database.dao.BookDao
import com.minicore.bookshelf.core.database.dao.LoanDao
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.di.DatabaseModule
import com.minicore.bookshelf.navigation.BookshelfDestinations
import com.minicore.bookshelf.navigation.BookshelfNavGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AC5 (plan `docs/plans/22-empty-state-library-screen.md` section 8): the real
 * UI -> ViewModel -> use cases -> [com.minicore.bookshelf.features.library.data.RoomLibraryRepository]
 * -> DAO path on a fresh, empty in-memory database, end to end through [BookshelfNavGraph]
 * (plan #20 R13's `@UninstallModules`/in-memory-Room pattern, applied to [DatabaseModule]
 * instead of `LibraryDataModule` so the real repository stays wired).
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class)
@RunWith(AndroidJUnit4::class)
class LibraryEmptyStateEndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var database: BookshelfDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .applyBookshelfConfig()
            .build()
        TestDatabaseHolder.database = database
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun setContentAndWaitForEmptyState(): TestNavHostController {
        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme {
                BookshelfNavGraph(navController)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTagCompat(LIBRARY_EMPTY_STATE_TEST_TAG).isNotEmpty()
        }
        return navController
    }

    // Fresh/empty DB: the empty state, hidden rows/chips, and the FAB all render end to end;
    // tapping the FAB leaves the route and back-stack size unchanged, and the empty state is
    // still displayed afterward.
    @Test
    fun freshEmptyDatabase_rendersEmptyStateAndFab_fabTapIsNoOp() {
        val navController = setContentAndWaitForEmptyState()

        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_READING_ROW_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LIBRARY_LENT_ROW_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LIBRARY_FILTER_CHIPS_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).assertIsDisplayed()

        val backStackSizeBefore = navController.currentBackStack.value.size

        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(BookshelfDestinations.Home.route, navController.currentDestination?.route)
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)
        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertIsDisplayed()
    }

    // Inserting one Reading book live replaces the empty state with the "All · 1" chip and the
    // reading row, with no restart needed.
    @Test
    fun insertingOneBook_replacesEmptyStateWithChipAndRow_live() {
        setContentAndWaitForEmptyState()

        runBlocking { insertBook(status = BookStatus.Reading) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTagCompat(LIBRARY_READING_ROW_TEST_TAG).isNotEmpty()
        }

        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("All · 1").assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_READING_ROW_TEST_TAG).assertIsDisplayed()
    }

    // Recovery path QA flagged (not in the original plan): deleting the only book live brings
    // the empty-state panel back, and hides the rows/chips again, with no duplicate panel and no
    // stale row left behind.
    @Test
    fun deletingTheOnlyBook_bringsBackEmptyState_live() {
        setContentAndWaitForEmptyState()

        val bookId = runBlocking { insertBook(status = BookStatus.Reading) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTagCompat(LIBRARY_READING_ROW_TEST_TAG).isNotEmpty()
        }
        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertDoesNotExist()

        runBlocking { database.bookDao().deleteById(bookId) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTagCompat(LIBRARY_EMPTY_STATE_TEST_TAG).isNotEmpty()
        }

        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTagCompat(LIBRARY_EMPTY_STATE_TEST_TAG).let {
            assertEquals(1, it.size)
        }
        composeRule.onNodeWithTag(LIBRARY_READING_ROW_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LIBRARY_FILTER_CHIPS_TEST_TAG).assertDoesNotExist()
    }

    private suspend fun insertBook(status: BookStatus): Long = database.bookDao().insert(
        BookEntity(
            title = "Book",
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
    )

    /**
     * Hands the single in-memory [BookshelfDatabase] built by `setUp` to [TestDatabaseModule],
     * since a Hilt module's `@Provides` functions cannot read a test instance field directly.
     * Nested (not top-level) so this module is only installed into *this* test class's
     * generated component, not every `@HiltAndroidTest` in the androidTest source set - a
     * top-level `@InstallIn(SingletonComponent::class)` module is aggregated into every test's
     * component and collided with the real [DatabaseModule] still installed for other tests.
     */
    private object TestDatabaseHolder {
        lateinit var database: BookshelfDatabase
    }

    @Module
    @InstallIn(SingletonComponent::class)
    internal object TestDatabaseModule {

        @Provides
        @Singleton
        fun provideDatabase(): BookshelfDatabase = TestDatabaseHolder.database

        @Provides
        fun provideBookDao(database: BookshelfDatabase): BookDao = database.bookDao()

        @Provides
        fun provideLoanDao(database: BookshelfDatabase): LoanDao = database.loanDao()
    }
}

private fun ComposeTestRule.onAllNodesWithTagCompat(testTag: String) =
    onAllNodesWithTag(testTag).fetchSemanticsNodes(atLeastOneRootRequired = false)
