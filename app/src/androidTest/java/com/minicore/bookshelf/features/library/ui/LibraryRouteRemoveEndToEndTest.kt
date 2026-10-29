package com.minicore.bookshelf.features.library.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.HiltTestActivity
import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.coverimage.CoverRefs
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.database.dao.BookDao
import com.minicore.bookshelf.core.database.dao.LoanDao
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.database.entity.LoanEntity
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.di.DatabaseModule
import com.minicore.bookshelf.features.library.data.RoomLibraryRepository
import com.minicore.bookshelf.features.library.domain.CurrentDateProvider
import com.minicore.bookshelf.features.library.domain.ObserveLibraryGridUseCase
import com.minicore.bookshelf.features.library.domain.ObserveLibraryRowsUseCase
import com.minicore.bookshelf.features.library.domain.RemoveBookUseCase
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // The real, Context-backed cover store (CoverImageModule is not uninstalled here) - used to
    // hand-build a `RemoveBookUseCase` for the process-death test below (reviewer finding: build
    // the ViewModel by hand with a pre-filled SavedStateHandle, rather than relying on
    // `scenario.recreate()`/`StateRestorationTester`, neither of which is viable against
    // `HiltTestActivity` - see the class KDoc).
    @Inject
    lateinit var coverImageStore: CoverImageStore

    private lateinit var database: BookshelfDatabase

    // Tracks every cover file this test writes directly to disk (not through
    // `FileCoverImageStore`), so a failing assertion still cleans up `filesDir/covers` rather
    // than leaving litter for the next instrumented run on the same device (reviewer finding).
    private val seededCoverFiles = mutableListOf<File>()

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
        seededCoverFiles.forEach { it.delete() }
    }

    /** A fresh, real stored-cover-ref-shaped file - reviewer finding: a `"book-$bookId-..."`
     * name fails [CoverRefs.isValidStoredRef] (path-traversal protection working as intended),
     * so [com.minicore.bookshelf.core.coverimage.FileCoverImageStore.delete] silently no-ops on
     * it, which is a fixture bug, not a production one. */
    private fun newCoverFile(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coversDir = File(context.filesDir, CoverRefs.COVERS_DIR).apply { mkdirs() }
        val file = File(coversDir, "${UUID.randomUUID()}.jpg")
        seededCoverFiles += file
        return file
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
        val coverFile = newCoverFile()
        coverFile.writeText("fake-jpeg-bytes")
        val coverRef = "${CoverRefs.COVERS_DIR}/${coverFile.name}"
        assertTrue("fixture bug: $coverRef must match CoverRefs.STORED_REF_REGEX", CoverRefs.isValidStoredRef(coverRef))
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

    // K7/B18/B19 (reviewer finding): a hand-built LibraryViewModel, over the same in-memory DB,
    // with its SavedStateHandle pre-filled exactly as process-death restore would leave it -
    // proves the dialog renders correctly with *zero user action*, and that confirming from that
    // restored state still cascades. `scenario.recreate()`/`StateRestorationTester` are not used
    // here - see the class KDoc for why neither is viable against `HiltTestActivity`.
    @Test
    fun restoredSavedStateHandle_rendersThePrompt_andConfirmingStillCascades() {
        val (bookId, loanId, coverFile) = seedLentBookWithCover("Heretics of Dune")

        val repository = RoomLibraryRepository(database.bookDao(), database.loanDao())
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "library_remove_book_id" to bookId,
                "library_remove_book_title" to "Heretics of Dune",
                "library_remove_book_borrower" to "Priya",
            ),
        )
        val viewModel = LibraryViewModel(
            ObserveLibraryRowsUseCase(repository, CurrentDateProvider(Provider { Clock.systemDefaultZone() })),
            ObserveLibraryGridUseCase(repository),
            RemoveBookUseCase(repository, coverImageStore),
            savedStateHandle,
        )

        composeRule.setContent {
            BookshelfTheme {
                LibraryRoute(
                    onSearchClick = {},
                    onBookClick = {},
                    onSeeAllLoansClick = {},
                    onAddBookClick = {},
                    viewModel = viewModel,
                )
            }
        }

        // No long-press, no "⋮" tap: the dialog must already be showing, restored purely from
        // the pre-filled SavedStateHandle.
        composeRule.onNodeWithTag(REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG).performClick()

        assertCascaded(bookId, loanId, coverFile)
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
