package com.minicore.bookshelf.navigation

import android.os.Bundle
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.room.Room
import androidx.test.espresso.Espresso.pressBack
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
import com.minicore.bookshelf.features.library.ui.addbook.ADD_BOOK_CANCEL_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.addbook.ADD_BOOK_CLOSE_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.addbook.ADD_BOOK_DISCARD_DIALOG_TEST_TAG
import com.minicore.bookshelf.features.library.ui.addbook.ADD_BOOK_SAVE_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.addbook.ADD_BOOK_SCRIM_TEST_TAG
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    // Critical #3: the scrim's clickable sits on an ancestor Box behind the Surface, not on top
    // of it - tapping any part of the sheet itself (here, its title) must never be mistaken for a
    // tap on the scrim and dismiss the sheet.
    @Test
    fun tappingTheSheetTitle_doesNotDismissTheSheet() {
        setContent()

        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(ADD_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }

        composeRule.onNodeWithText("New book").performClick()

        composeRule.onNodeWithTag(ADD_BOOK_SHEET_TEST_TAG).assertIsDisplayed()
    }

    // Top suggestion #1 (round 2): `clearAndSetSemantics {}` must sit *before* (outside)
    // `.clickable(...)` in the scrim's modifier chain - reversing that order lets Compose's
    // inner-to-outer semantics walk re-apply the clickable's own `onClick` action on top of the
    // clear, leaving a full-screen unlabeled TalkBack stop that still dismisses the sheet. Proven
    // directly on the merged (default) semantics tree, not by indirect dismiss behavior.
    @Test
    fun scrim_hasNoClickActionInTheAccessibilityTree() {
        setContent()

        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(ADD_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }

        composeRule.onNodeWithTag(ADD_BOOK_SCRIM_TEST_TAG).assert(!hasClickAction())
    }

    // Critical finding (round 2): `KEY_BOOK_ADDED` must be removed from the previous back stack
    // entry's `SavedStateHandle` only AFTER the Snackbar has shown - never left behind - else
    // Activity recreation (rotation/config-change/process-death restore) replays the same
    // non-null id via `getStateFlow` and reshows "Added to your shelf" forever. A full Activity
    // destroy+recreate isn't exercisable against `HiltTestActivity` here (it has no `onCreate`
    // that re-applies `composeRule.setContent`, unlike a real `ComponentActivity`), so this
    // simulates the same mechanism `NavHostController` itself relies on to survive recreation:
    // serializing the whole back stack (including every entry's `SavedStateHandle`) to a
    // `Bundle` via `saveState()`, then handing that `Bundle` to a brand new controller via
    // `restoreState()` before composing the graph again from scratch.
    @Test
    fun savingABook_doesNotReshowAddedSnackbar_afterSimulatedRecreate() {
        val generation = mutableStateOf(0)
        var savedNavState: Bundle? = null
        lateinit var currentController: TestNavHostController

        composeRule.setContent {
            key(generation.value) {
                currentController = remember(generation.value) {
                    TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                        navigatorProvider.addNavigator(ComposeNavigator())
                        savedNavState?.let { restoreState(it) }
                    }
                }
                BookshelfTheme {
                    BookshelfNavGraph(currentController)
                }
            }
        }

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

        // Let the Snackbar finish being shown - and the fix's `onBookAddedSnackbarShown` clear
        // the saved-state entry - before "recreating".
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("Added to your shelf")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        }

        savedNavState = currentController.saveState()
        generation.value++
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Added to your shelf").assertDoesNotExist()
    }

    // Round 3 critical finding: `onBookAddedSnackbarShown` must push `set(KEY_BOOK_ADDED, null)`
    // through the `SavedStateHandle`, not `remove()` it - `remove()` only clears the backing map
    // entry, it never emits to the `StateFlow` `LibraryRoute` is already collecting via
    // `getStateFlow`, so a second save in the same session (same `SavedStateHandle`, no
    // recreation) would never re-trigger the Snackbar. Proves the fix by actually saving twice.
    @Test
    fun savingTwoBooksInARow_showsAddedSnackbarBothTimes() {
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

        // Let the first Snackbar finish (and `onBookAddedSnackbarShown` clear the saved-state
        // entry) before saving again.
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("Added to your shelf")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        }

        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(ADD_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune Messiah")
        composeRule.onNodeWithTag(ADD_BOOK_SAVE_BUTTON_TEST_TAG).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(ADD_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        composeRule.onNodeWithText("Added to your shelf").assertIsDisplayed()
    }

    // UX spec section 2 / docs/ux/decisions.md:7: every exit path (Cancel, close, system back,
    // scrim) must only prompt a discard-confirm dialog when the draft is dirty. A clean/untouched
    // draft dismisses immediately, with no dialog ever appearing.

    private fun openAddBookSheet() {
        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(ADD_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun makeDraftDirty() {
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).performTextInput("Dune")
    }

    private fun assertDismissedImmediately_noDialogEverShown() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            allNodesWithTag(ADD_BOOK_SHEET_TEST_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        composeRule.onNodeWithTag(ADD_BOOK_DISCARD_DIALOG_TEST_TAG).assertDoesNotExist()
    }

    private fun assertDiscardDialogShown_sheetStillOpen() {
        composeRule.onNodeWithTag(ADD_BOOK_DISCARD_DIALOG_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ADD_BOOK_SHEET_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun cleanDraft_cancelButton_dismissesImmediately_noDialog() {
        setContent()
        openAddBookSheet()

        composeRule.onNodeWithTag(ADD_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()

        assertDismissedImmediately_noDialogEverShown()
    }

    @Test
    fun cleanDraft_closeButton_dismissesImmediately_noDialog() {
        setContent()
        openAddBookSheet()

        composeRule.onNodeWithTag(ADD_BOOK_CLOSE_BUTTON_TEST_TAG).performClick()

        assertDismissedImmediately_noDialogEverShown()
    }

    @Test
    fun cleanDraft_systemBack_dismissesImmediately_noDialog() {
        setContent()
        openAddBookSheet()

        pressBack()

        assertDismissedImmediately_noDialogEverShown()
    }

    @Test
    fun cleanDraft_scrimTap_dismissesImmediately_noDialog() {
        setContent()
        openAddBookSheet()

        composeRule.onNodeWithTag(ADD_BOOK_SCRIM_TEST_TAG).performClick()

        assertDismissedImmediately_noDialogEverShown()
    }

    @Test
    fun dirtyDraft_cancelButton_showsDiscardDialog_insteadOfDismissing() {
        setContent()
        openAddBookSheet()
        makeDraftDirty()

        composeRule.onNodeWithTag(ADD_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()

        assertDiscardDialogShown_sheetStillOpen()
    }

    @Test
    fun dirtyDraft_closeButton_showsDiscardDialog_insteadOfDismissing() {
        setContent()
        openAddBookSheet()
        makeDraftDirty()

        composeRule.onNodeWithTag(ADD_BOOK_CLOSE_BUTTON_TEST_TAG).performClick()

        assertDiscardDialogShown_sheetStillOpen()
    }

    @Test
    fun dirtyDraft_systemBack_showsDiscardDialog_insteadOfDismissing() {
        setContent()
        openAddBookSheet()
        makeDraftDirty()

        pressBack()

        assertDiscardDialogShown_sheetStillOpen()
    }

    @Test
    fun dirtyDraft_scrimTap_showsDiscardDialog_insteadOfDismissing() {
        setContent()
        openAddBookSheet()
        makeDraftDirty()

        composeRule.onNodeWithTag(ADD_BOOK_SCRIM_TEST_TAG).performClick()

        assertDiscardDialogShown_sheetStillOpen()
    }

    @Test
    fun discardDialog_keepEditing_closesDialog_keepsSheetAndFields() {
        setContent()
        openAddBookSheet()
        makeDraftDirty()
        composeRule.onNodeWithTag(ADD_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()
        assertDiscardDialogShown_sheetStillOpen()

        composeRule.onNodeWithText("Keep editing").performClick()

        composeRule.onNodeWithTag(ADD_BOOK_DISCARD_DIALOG_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(ADD_BOOK_SHEET_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BOOK_FORM_TITLE_FIELD_TEST_TAG).assert(hasText("Dune"))
    }

    @Test
    fun discardDialog_discard_closesSheet_noBookInserted() {
        setContent()
        openAddBookSheet()
        makeDraftDirty()
        composeRule.onNodeWithTag(ADD_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()
        assertDiscardDialogShown_sheetStillOpen()

        composeRule.onNodeWithText("Discard").performClick()

        assertDismissedImmediately_noDialogEverShown()
        val books = runBlocking { database.bookDao().observeLibraryBooks(null).first() }
        assert(books.isEmpty()) { "expected no book to be inserted after discarding, found $books" }
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
