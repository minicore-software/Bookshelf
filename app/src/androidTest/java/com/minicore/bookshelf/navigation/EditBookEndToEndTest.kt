package com.minicore.bookshelf.navigation

import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.database.dao.BookDao
import com.minicore.bookshelf.core.database.dao.LoanDao
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.database.entity.LoanEntity
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.di.DatabaseModule
import com.minicore.bookshelf.features.library.ui.bookdetail.BOOK_DETAIL_EDIT_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookdetail.BOOK_DETAIL_SHEET_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookform.BOOK_FORM_TITLE_FIELD_TEST_TAG
import com.minicore.bookshelf.features.library.ui.bookform.MEMORY_WHY_FIELD_TEST_TAG
import com.minicore.bookshelf.features.library.ui.editbook.BORROWING_CHECKBOX_ROW_TEST_TAG
import com.minicore.bookshelf.features.library.ui.editbook.EDIT_BOOK_CANCEL_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.editbook.EDIT_BOOK_DISCARD_DIALOG_TEST_TAG
import com.minicore.bookshelf.features.library.ui.editbook.EDIT_BOOK_SAVE_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.editbook.EDIT_BOOK_SHEET_TEST_TAG
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real pencil -> Edit -> Save/Cancel -> back-to-Detail round trip (plan #33 section 7 AC4,
 * decision E2), against a fresh in-memory database, mirroring `AddBookEndToEndTest`'s pattern.
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class)
@RunWith(AndroidJUnit4::class)
class EditBookEndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<com.minicore.bookshelf.HiltTestActivity>()

    private lateinit var database: BookshelfDatabase
    private var bookId: Long = 0L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .applyBookshelfConfig()
            .build()
        TestDatabaseHolder.database = database
        hiltRule.inject()

        bookId = runBlocking {
            database.bookDao().insert(
                BookEntity(
                    title = "Dune",
                    author = "Frank Herbert",
                    isbn = null,
                    coverUri = null,
                    coverColor = null,
                    genre = null,
                    pageCount = null,
                    publishedYear = 1965,
                    status = BookStatus.Finished,
                    rating = 4,
                    synopsis = null,
                    comment = "Loved it",
                    createdAt = java.time.Instant.EPOCH,
                ),
            )
        }
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

    private fun allNodesWithTag(testTag: String): SemanticsNodeInteractionCollection = composeRule.onAllNodesWithTag(testTag)

    private fun openBookDetailThenEdit(navController: TestNavHostController) {
        navController.navigate(BookshelfDestinations.BookDetail.route(bookId))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(BOOK_DETAIL_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        composeRule.onNodeWithTag(BOOK_DETAIL_EDIT_BUTTON_TEST_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(EDIT_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    // F11: Save persists edits, closes Edit and returns to Detail - which shows the new title and
    // the new memory field (plan #33 section 7's test strategy calls for title + memory, not
    // title alone) - not Home.
    @Test
    fun savingAnEditedTitleAndMemory_persists_returnsToDetailShowingBoth() {
        val navController = setContent()
        openBookDetailThenEdit(navController)

        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune Messiah")
        composeRule.onNodeWithTag(MEMORY_WHY_FIELD_TEST_TAG)
            .performTextInput("A friend lent it to me")
        composeRule.onNodeWithTag(EDIT_BOOK_SAVE_BUTTON_TEST_TAG).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(EDIT_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }

        composeRule.onNodeWithTag(BOOK_DETAIL_SHEET_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Dune Messiah").assertIsDisplayed()
        composeRule.onNodeWithText("A friend lent it to me").assertIsDisplayed()
        assertEquals(BookshelfDestinations.BookDetail.route, navController.currentDestination?.route)
        assertEquals(bookId, navController.currentBackStackEntry?.arguments?.getLong(BookshelfDestinations.BookDetail.ARG_BOOK_ID))
    }

    // F25/R9: Cancel on Edit stacked over Detail lands back on Detail, not Home.
    @Test
    fun cancellingEdit_returnsToDetail_notHome() {
        val navController = setContent()
        openBookDetailThenEdit(navController)

        composeRule.onNodeWithTag(EDIT_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(EDIT_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        composeRule.onNodeWithTag(BOOK_DETAIL_SHEET_TEST_TAG).assertIsDisplayed()
        assertEquals(BookshelfDestinations.BookDetail.route, navController.currentDestination?.route)
        assertEquals(bookId, navController.currentBackStackEntry?.arguments?.getLong(BookshelfDestinations.BookDetail.ARG_BOOK_ID))
    }

    // AC3/plan #33 section 7: a loan opened while Edit is already on screen flips the read-only
    // checkbox on; `markReturned` flips it back off; saving afterwards never touches the `loans`
    // table (D13 - no write path exists under ui/editbook/).
    @Test
    fun openingAndReturningALoanWhileEditIsOpen_flipsTheCheckbox_andSaveLeavesLoansUntouched() {
        val navController = setContent()
        openBookDetailThenEdit(navController)

        composeRule.onNodeWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG).assertIsOff()

        val loanId = runBlocking {
            database.loanDao().insert(
                LoanEntity(
                    bookId = bookId,
                    borrowerName = "Alex",
                    borrowerContact = null,
                    loanedAt = Instant.EPOCH,
                    dueAt = LocalDate.of(2026, 11, 1),
                    returnedAt = null,
                    remindEnabled = false,
                    notes = null,
                ),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { node -> node.config.getOrElse(SemanticsProperties.ToggleableState) { ToggleableState.Indeterminate } == ToggleableState.On }
        }
        composeRule.onNodeWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG).assertIsOn()

        val loansBeforeReturn = runBlocking { database.loanDao().findOpenLoan(bookId) }
        assertEquals(loanId, loansBeforeReturn?.id)

        runBlocking { database.loanDao().markReturned(loanId, Instant.parse("2026-10-28T00:00:00Z")) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { node -> node.config.getOrElse(SemanticsProperties.ToggleableState) { ToggleableState.Indeterminate } == ToggleableState.Off }
        }
        composeRule.onNodeWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG).assertIsOff()

        val loanBeforeSave = runBlocking { database.loanDao().findOpenLoan(bookId) }
        assertEquals(null, loanBeforeSave)
        // Confirm the loan row itself (not just "has no open loan") is byte-identical across the
        // save - `findOpenLoan` alone would miss a returned loan silently gaining a spurious edit.
        val loanRowBeforeSave = selectLoanRow(loanId)

        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune Messiah")
        composeRule.onNodeWithTag(EDIT_BOOK_SAVE_BUTTON_TEST_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(EDIT_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }

        val loanRowAfterSave = selectLoanRow(loanId)
        assertEquals(loanRowBeforeSave, loanRowAfterSave)
    }

    private fun selectLoanRow(loanId: Long): LoanEntity? = runBlocking {
        database.openHelper.readableDatabase.query("SELECT * FROM loans WHERE id = $loanId").use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            LoanEntity(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                bookId = cursor.getLong(cursor.getColumnIndexOrThrow("book_id")),
                borrowerName = cursor.getString(cursor.getColumnIndexOrThrow("borrower_name")),
                borrowerContact = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("borrower_contact")),
                loanedAt = Instant.ofEpochMilli(cursor.getLong(cursor.getColumnIndexOrThrow("loaned_at"))),
                dueAt = LocalDate.ofEpochDay(cursor.getLong(cursor.getColumnIndexOrThrow("due_at"))),
                returnedAt = cursor.getColumnIndexOrThrow("returned_at").let { idx ->
                    if (cursor.isNull(idx)) null else Instant.ofEpochMilli(cursor.getLong(idx))
                },
                remindEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("remind_enabled")) != 0,
                notes = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("notes")),
            )
        }
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    // AC4: Cancel after edits (and the equivalent ✕/back paths exercised by the Compose-level
    // discard-dialog tests in EditBookRouteTest), followed by Discard, leaves the DB row
    // byte-for-byte unchanged.
    @Test
    fun cancelAfterEdits_thenDiscard_leavesTheDbRowUnchanged() {
        val navController = setContent()
        openBookDetailThenEdit(navController)
        val before = runBlocking { database.bookDao().observeById(bookId).first() }

        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune Messiah")
        composeRule.onNodeWithTag(MEMORY_WHY_FIELD_TEST_TAG)
            .performTextInput("Changed my mind about this")
        composeRule.onNodeWithTag(EDIT_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_BOOK_DISCARD_DIALOG_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Discard").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(EDIT_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        composeRule.onNodeWithTag(BOOK_DETAIL_SHEET_TEST_TAG).assertIsDisplayed()

        val after = runBlocking { database.bookDao().observeById(bookId).first() }
        assertEquals(before, after)
    }

    // F18: navigating to EditBook for the same book twice (a double tap on the Detail pencil)
    // never stacks two editors - `launchSingleTop` (navigation/EditBookNavigation.kt) collapses
    // the second navigation into the existing back-stack entry.
    @Test
    fun navigatingToEditBookTwice_doesNotStackTwoEditors() {
        val navController = setContent()
        navController.navigate(BookshelfDestinations.BookDetail.route(bookId))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(BOOK_DETAIL_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }

        composeRule.runOnIdle { navController.navigateToEditBook(bookId) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(EDIT_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        composeRule.runOnIdle { navController.navigateToEditBook(bookId) }
        composeRule.waitForIdle()

        val editEntries = navController.currentBackStack.value.count { it.destination.route == BookshelfDestinations.EditBook.route }
        assertEquals(1, editEntries)
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
