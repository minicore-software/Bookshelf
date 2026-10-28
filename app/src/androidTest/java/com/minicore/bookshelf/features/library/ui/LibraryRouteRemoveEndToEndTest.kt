package com.minicore.bookshelf.features.library.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.HiltTestActivity
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.database.dao.BookDao
import com.minicore.bookshelf.core.database.dao.LoanDao
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.database.entity.LoanEntity
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.di.DatabaseModule
import com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_CANCEL_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG
import com.minicore.bookshelf.features.library.ui.removebook.REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Instant
import java.time.LocalDate
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The critical AC3 proof (reviewer finding #3): the real [LibraryRoute] + [LibraryViewModel] +
 * [com.minicore.bookshelf.features.library.domain.RemoveBookUseCase], against a fresh in-memory
 * database and the real `FileCoverImageStore` - mirrors `EditBookEndToEndTest`'s pattern, but
 * hosts [LibraryRoute] directly (the `BookshelfNavGraph` test harness is currently broken in this
 * environment, pre-existing, out of scope here). Run once per trigger (long-press, then "⋮") to
 * prove the two entry points produce an identical outcome - a route that silently wired
 * `onConfirm` to `onRemoveDismissed`, or dropped `borrowerName`, would fail every test below.
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class)
@RunWith(AndroidJUnit4::class)
class LibraryRouteRemoveEndToEndTest {

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

    private fun coverFileFor(bookId: Long): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        return File(coversDir, "book-$bookId-cover.jpg")
    }

    /** Seeds a lent book with a real stored cover file, returns (bookId, loanId, coverFile). */
    private fun seedLentBookWithCover(title: String): Triple<Long, Long, File> {
        val bookId = runBlocking {
            database.bookDao().insert(
                BookEntity(
                    title = title,
                    author = "Author",
                    isbn = null,
                    coverUri = null,
                    coverColor = null,
                    genre = null,
                    pageCount = null,
                    publishedYear = 2020,
                    status = BookStatus.Finished,
                    rating = null,
                    synopsis = null,
                    comment = null,
                    createdAt = Instant.EPOCH,
                ),
            )
        }
        val coverFile = coverFileFor(bookId)
        coverFile.writeText("fake-jpeg-bytes")
        val coverRef = "covers/${coverFile.name}"
        runBlocking {
            database.bookDao().update(requireNotNull(database.bookDao().observeById(bookId).first()).copy(coverUri = coverRef))
        }
        val loanId = runBlocking {
            database.loanDao().insert(
                LoanEntity(
                    bookId = bookId,
                    borrowerName = "Priya",
                    borrowerContact = null,
                    loanedAt = Instant.EPOCH,
                    dueAt = LocalDate.of(2026, 11, 1),
                    returnedAt = null,
                    remindEnabled = false,
                    notes = null,
                ),
            )
        }
        return Triple(bookId, loanId, coverFile)
    }

    private fun setContent() {
        composeRule.setContent {
            BookshelfTheme {
                LibraryRoute(onSearchClick = {}, onBookClick = {}, onSeeAllLoansClick = {}, onAddBookClick = {})
            }
        }
    }

    private fun waitForCard(bookId: Long) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(libraryGridCardTag(bookId)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun assertCascaded(bookId: Long, loanId: Long, coverFile: File) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(libraryGridCardTag(bookId)).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        val bookRow = runBlocking { database.bookDao().observeById(bookId).first() }
        assertNull(bookRow)
        val loanExists = runBlocking {
            database.openHelper.readableDatabase.query("SELECT * FROM loans WHERE id = $loanId").use { it.moveToFirst() }
        }
        assertEquals(false, loanExists)
        assertEquals(false, coverFile.exists())
    }

    // B13: long-press -> the open-loan warning shows, confirming cascades the row, the loan and
    // the cover file, and drops the card from the grid.
    @Test
    fun longPressTrigger_showsWarning_cascadesInDb_deletesCoverFile_dropsCard() {
        val (bookId, loanId, coverFile) = seedLentBookWithCover("Dune")
        setContent()
        waitForCard(bookId)

        composeRule.onNodeWithTag(libraryGridCardTag(bookId)).performTouchInput { longClick() }
        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_REMOVE_TEST_TAG).performClick()

        composeRule.onNodeWithTag(REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG).performClick()

        assertCascaded(bookId, loanId, coverFile)
    }

    // B14: the "⋮" trigger must produce the identical outcome to B13 - proves no divergence
    // between the two entry points (AC3).
    @Test
    fun overflowButtonTrigger_showsWarning_cascadesInDb_deletesCoverFile_dropsCard() {
        val (bookId, loanId, coverFile) = seedLentBookWithCover("Dune Messiah")
        setContent()
        waitForCard(bookId)

        composeRule.onNodeWithTag(libraryGridCardMenuButtonTag(bookId), useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_REMOVE_TEST_TAG).performClick()

        composeRule.onNodeWithTag(REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG).performClick()

        assertCascaded(bookId, loanId, coverFile)
    }

    // B15 (should): Cancel is a true no-op, including the cover file.
    @Test
    fun cancellingRemoval_leavesTheRowLoanAndCoverFileUnchanged() {
        val (bookId, loanId, coverFile) = seedLentBookWithCover("Children of Dune")
        setContent()
        waitForCard(bookId)

        composeRule.onNodeWithTag(libraryGridCardTag(bookId)).performTouchInput { longClick() }
        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_REMOVE_TEST_TAG).performClick()
        composeRule.onNodeWithTag(REMOVE_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(libraryGridCardTag(bookId)).assertIsDisplayed()
        val bookRow = runBlocking { database.bookDao().observeById(bookId).first() }
        assertNotNull(bookRow)
        val loanExists = runBlocking {
            database.openHelper.readableDatabase.query("SELECT * FROM loans WHERE id = $loanId").use { it.moveToFirst() }
        }
        assertEquals(true, loanExists)
        assertEquals(true, coverFile.exists())
        coverFile.delete()
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
