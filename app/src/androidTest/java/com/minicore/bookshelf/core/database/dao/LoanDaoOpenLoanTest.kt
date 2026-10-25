package com.minicore.bookshelf.core.database.dao

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.database.entity.LoanEntity
import com.minicore.bookshelf.core.model.BookStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AC3 (QA A10-A18): the open-loan overlay join and the trigger-enforced single-open-loan
 * invariant (ADR-001 S5).
 */
@RunWith(AndroidJUnit4::class)
class LoanDaoOpenLoanTest {

    private lateinit var database: BookshelfDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .applyBookshelfConfig()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun insertBook(title: String): Long = database.bookDao().insert(
        BookEntity(
            title = title,
            author = "Author",
            isbn = null,
            coverUri = null,
            genre = null,
            pageCount = null,
            publishedYear = null,
            status = BookStatus.Reading,
            rating = null,
            synopsis = null,
            createdAt = Instant.ofEpochMilli(1_000),
        )
    )

    private fun loanFor(
        bookId: Long,
        borrowerName: String = "Borrower",
        returnedAt: Instant? = null,
        dueAt: LocalDate = LocalDate.of(2026, 11, 1),
        loanedAt: Instant = Instant.ofEpochMilli(10_000),
    ) = LoanEntity(
        bookId = bookId,
        borrowerName = borrowerName,
        borrowerContact = null,
        loanedAt = loanedAt,
        dueAt = dueAt,
        returnedAt = returnedAt,
        remindEnabled = false,
        notes = null,
    )

    // A10: 0 open loans - no loans at all, and a book with only a returned loan.
    @Test
    fun zeroOpenLoans_lentOutEmptyAndOpenLoanNull() = runTest {
        val noLoansBook = insertBook("No Loans")
        val returnedOnlyBook = insertBook("Returned Only")
        database.loanDao().insert(loanFor(returnedOnlyBook, returnedAt = Instant.ofEpochMilli(20_000)))

        assertTrue(database.bookDao().observeLentOutBooks().first().isEmpty())
        val rows = database.bookDao().observeLibraryBooks(null).first()
        assertNull(rows.single { it.book.id == noLoansBook }.openLoanId)
        assertNull(rows.single { it.book.id == returnedOnlyBook }.openLoanId)
        assertEquals(0, database.loanDao().observeOpenLoanCount().first())
    }

    // A11: 1 open loan - book A: 1 returned + 1 open loan; book B: none.
    @Test
    fun oneOpenLoan_lentBookUsesOpenLoanDataNotReturnedLoan() = runTest {
        val bookA = insertBook("Book A")
        insertBook("Book B")
        val returnedLoanedAt = Instant.ofEpochMilli(5_000)
        val openLoanedAt = Instant.ofEpochMilli(15_000)
        database.loanDao().insert(
            loanFor(
                bookA,
                borrowerName = "Old Borrower",
                returnedAt = Instant.ofEpochMilli(20_000),
                loanedAt = returnedLoanedAt,
            )
        )
        val openDueAt = LocalDate.of(2026, 12, 25)
        database.loanDao().insert(
            loanFor(
                bookA,
                borrowerName = "Current Borrower",
                dueAt = openDueAt,
                loanedAt = openLoanedAt,
            )
        )

        val lentOut = database.bookDao().observeLentOutBooks().first()

        assertEquals(1, lentOut.size)
        assertEquals(bookA, lentOut.single().book.id)
        assertEquals("Current Borrower", lentOut.single().openLoanBorrowerName)
        assertEquals(openDueAt, lentOut.single().openLoanDueAt)
        assertEquals(openLoanedAt, lentOut.single().openLoanLoanedAt)
        assertEquals(1, database.loanDao().observeOpenLoanCount().first())

        val libraryRows = database.bookDao().observeLibraryBooks(null).first()
        val bookARow = libraryRows.single { it.book.id == bookA }
        assertEquals("Current Borrower", bookARow.openLoanBorrowerName)
        assertEquals(openDueAt, bookARow.openLoanDueAt)
        assertEquals(openLoanedAt, bookARow.openLoanLoanedAt)
    }

    // A12: attempted 2nd open loan on the same book throws; exactly one open loan remains; the
    // book appears exactly once in observeBooks (no duplicate row).
    @Test
    fun secondOpenLoanOnSameBook_throwsAndLeavesSingleOpenLoan() = runTest {
        val bookA = insertBook("Book A")
        database.loanDao().insert(loanFor(bookA, borrowerName = "First Borrower"))

        try {
            database.loanDao().insert(loanFor(bookA, borrowerName = "Second Borrower"))
            fail("expected SQLiteConstraintException")
        } catch (expected: SQLiteConstraintException) {
            // expected
        }

        assertEquals(1, database.loanDao().observeOpenLoanCount().first())
        assertEquals("First Borrower", database.loanDao().findOpenLoan(bookA)?.borrowerName)
        val rows = database.bookDao().observeLibraryBooks(null).first()
        assertEquals(1, rows.count { it.book.id == bookA })
    }

    // A13: trigger presence + durability.
    @Test
    fun triggers_existInSqliteMasterAfterApplyBookshelfConfig() = runTest {
        val triggerNames = mutableSetOf<String>()
        database.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'trigger'")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    triggerNames += cursor.getString(0)
                }
            }

        assertTrue(triggerNames.contains("loans_single_open_loan_insert"))
        assertTrue(triggerNames.contains("loans_single_open_loan_update"))
    }

    // FF1: foreign key enforcement is on, so the loans.book_id -> books.id cascade (A14) is
    // real, not a no-op.
    @Test
    fun foreignKeysPragma_isEnabled() = runTest {
        database.openHelper.readableDatabase.query("PRAGMA foreign_keys").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    // A15 (should): update-trigger reversal - flipping a returned loan back to NULL while
    // another loan on the same book is open throws.
    @Test
    fun updatingReturnedLoanBackToOpen_whileAnotherLoanIsOpen_throws() = runTest {
        val bookA = insertBook("Book A")
        val returnedLoanId =
            database.loanDao().insert(loanFor(bookA, returnedAt = Instant.ofEpochMilli(20_000)))
        database.loanDao().insert(loanFor(bookA, borrowerName = "Current Borrower"))

        try {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE loans SET returned_at = NULL WHERE id = $returnedLoanId"
            )
            fail("expected SQLiteConstraintException")
        } catch (expected: SQLiteConstraintException) {
            // expected
        }
    }

    // A15b (optional, closes the gap on the other branch of the update trigger): moving an
    // open loan's book_id onto a book that already has its own open loan throws, even though
    // the moved loan's own returned_at never changes.
    @Test
    fun updatingLoanBookId_ontoBookWithExistingOpenLoan_throws() = runTest {
        val bookA = insertBook("Book A")
        val bookC = insertBook("Book C")
        database.loanDao().insert(loanFor(bookA, borrowerName = "Borrower A"))
        val loanOnC = database.loanDao().insert(loanFor(bookC, borrowerName = "Borrower C"))

        try {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE loans SET book_id = $bookA WHERE id = $loanOnC"
            )
            fail("expected SQLiteConstraintException")
        } catch (expected: SQLiteConstraintException) {
            // expected
        }

        database.openHelper.readableDatabase
            .query("SELECT book_id FROM loans WHERE id = $loanOnC")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(bookC, cursor.getLong(0))
            }
    }

    // A16 (should): re-lend - open loan returned, then a new open loan succeeds.
    @Test
    fun reLendingAfterReturn_succeeds() = runTest {
        val bookA = insertBook("Book A")
        val firstLoanId = database.loanDao().insert(loanFor(bookA, borrowerName = "First Borrower"))

        val returnedRows = database.loanDao().markReturned(firstLoanId, Instant.ofEpochMilli(30_000))
        assertEquals(1, returnedRows)

        val secondLoanId = database.loanDao().insert(loanFor(bookA, borrowerName = "Second Borrower"))

        assertTrue(secondLoanId > 0)
        assertEquals("Second Borrower", database.loanDao().findOpenLoan(bookA)?.borrowerName)
    }

    // A17 (should): markReturned on an already-returned loan -> 0 affected rows, no throw, no
    // side effects on other rows.
    @Test
    fun markReturned_onAlreadyReturnedLoan_returnsZeroRowsWithNoSideEffects() = runTest {
        val bookA = insertBook("Book A")
        val loanId = database.loanDao().insert(loanFor(bookA, returnedAt = Instant.ofEpochMilli(20_000)))

        val affectedRows = database.loanDao().markReturned(loanId, Instant.ofEpochMilli(99_999))

        assertEquals(0, affectedRows)
        database.openHelper.readableDatabase
            .query("SELECT returned_at FROM loans WHERE id = $loanId")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(20_000L, cursor.getLong(0))
            }
    }

    // A18 (should): two different books A and B each with their own open loan - both in
    // observeLentOutBooks, lentOut == 2, no cross-book interference in the trigger's EXISTS scope.
    @Test
    fun twoBooksWithIndependentOpenLoans_bothAppearWithNoCrossBookInterference() = runTest {
        val bookA = insertBook("Book A")
        val bookB = insertBook("Book B")
        database.loanDao().insert(loanFor(bookA, borrowerName = "Borrower A"))
        database.loanDao().insert(loanFor(bookB, borrowerName = "Borrower B"))

        val lentOut = database.bookDao().observeLentOutBooks().first()

        assertEquals(2, lentOut.size)
        assertEquals(setOf(bookA, bookB), lentOut.map { it.book.id }.toSet())
        assertEquals(2, database.loanDao().observeOpenLoanCount().first())
    }
}
