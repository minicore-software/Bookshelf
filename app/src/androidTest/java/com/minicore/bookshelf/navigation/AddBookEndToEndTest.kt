package com.minicore.bookshelf.navigation

import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.di.DatabaseModule
import com.minicore.bookshelf.features.library.ui.LIBRARY_ADD_BOOK_FAB_TEST_TAG
import com.minicore.bookshelf.features.library.ui.addbook.ADD_BOOK_SAVE_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.addbook.ADD_BOOK_SHEET_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookform.BOOK_FORM_TITLE_FIELD_TEST_TAG
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real FAB -> sheet -> save -> close -> Snackbar round trip (plan #31 decision A12, UX spec
 * #31 section 7 "Success / confirmation feedback"), against a fresh in-memory database (pattern
 * from `LibraryEmptyStateEndToEndTest`). Proves the `previousBackStackEntry.savedStateHandle`
 * hand-off `BookshelfNavGraph` performs between `AddBookRoute` and `LibraryRoute` actually shows
 * the Snackbar, not just that the sheet closes.
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class)
@RunWith(AndroidJUnit4::class)
class AddBookEndToEndTest {

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

    private fun setContent(): TestNavHostController {
        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfTheme {
                BookshelfNavGraph(navController)
            }
        }
        return navController
    }

    private fun allNodesWithTag(testTag: String): SemanticsNodeInteractionCollection =
        composeRule.onAllNodesWithTag(testTag)

    // AC1/AC2/A12: filling only the required Title, saving, closes the sheet, lands the book in
    // the library and shows "Added to your shelf".
    @Test
    fun savingAMinimalBook_closesSheet_showsAddedSnackbar() {
        setContent()

        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(ADD_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }

        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune")
        composeRule.onNodeWithTag(ADD_BOOK_SAVE_BUTTON_TEST_TAG).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(ADD_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }

        composeRule.onNodeWithText("Added to your shelf").assertIsDisplayed()
    }

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
