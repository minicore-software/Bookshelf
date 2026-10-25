package com.minicore.bookshelf.core.database.dao

import android.content.Context
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AC2 (QA A5-A8, A14): [BookDao.observeLibraryBooks], [BookDao.observeStatusCounts] and the
 * cascade delete behaviour (`loans.book_id -> books.id ON DELETE CASCADE`).
 */
@RunWith(AndroidJUnit4::class)
class BookDaoTest {

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

    private suspend fun insertBook(
        title: String,
        status: BookStatus,
        createdAt: Instant,
    ): Long = database.bookDao().insert(
        BookEntity(
            title = title,
            author = "Author",
            isbn = null,
            coverUri = null,
            genre = null,
            pageCount = null,
            publishedYear = null,
            status = status,
            rating = null,
            synopsis = null,
            createdAt = createdAt,
        )
    )

    private suspend fun insertLoan(bookId: Long, returnedAt: Instant?): Long =
        database.loanDao().insert(
            LoanEntity(
                bookId = bookId,
                borrowerName = "Borrower",
                borrowerContact = null,
                loanedAt = Instant.ofEpochMilli(100),
                dueAt = LocalDate.of(2026, 11, 1),
                returnedAt = returnedAt,
                remindEnabled = false,
                notes = null,
            )
        )

    // A5: observeBooks(null) on mixed library -> all books, openLoan set only where applicable,
    // ordered by createdAt DESC.
    @Test
    fun observeLibraryBooks_withNullStatus_returnsAllOrderedByCreatedAtDescending() = runTest {
        val older = insertBook("Older", BookStatus.WantToRead, Instant.ofEpochMilli(1_000))
        val newer = insertBook("Newer", BookStatus.Finished, Instant.ofEpochMilli(2_000))
        insertLoan(newer, returnedAt = null)

        val rows = database.bookDao().observeLibraryBooks(null).first()

        assertEquals(listOf(newer, older), rows.map { it.book.id })
        assertNull(rows.single { it.book.id == older }.openLoanId)
        assertEquals("Borrower", rows.single { it.book.id == newer }.openLoanBorrowerName)
    }

    // A6: observeBooks(Finished) / observeCurrentlyReading() equivalent -> only matching status.
    @Test
    fun observeLibraryBooks_withStatusFilter_returnsOnlyMatchingStatus() = runTest {
        insertBook("Want", BookStatus.WantToRead, Instant.ofEpochMilli(1_000))
        val reading = insertBook("Reading", BookStatus.Reading, Instant.ofEpochMilli(2_000))
        insertBook("Finished", BookStatus.Finished, Instant.ofEpochMilli(3_000))

        val rows = database.bookDao().observeLibraryBooks(BookStatus.Reading).first()

        assertEquals(listOf(reading), rows.map { it.book.id })
    }

    // A7: observeCounts() on mixed library including a zero-count status -> all = sum of three
    // statuses, zero status reports 0 not null/missing.
    @Test
    fun observeStatusCounts_mixedLibrary_reportsEachStatusIncludingZero() = runTest {
        insertBook("Reading 1", BookStatus.Reading, Instant.ofEpochMilli(1_000))
        insertBook("Reading 2", BookStatus.Reading, Instant.ofEpochMilli(2_000))
        insertBook("Finished 1", BookStatus.Finished, Instant.ofEpochMilli(3_000))
        // No WantToRead books inserted - a zero-count status.

        val counts = database.bookDao().observeStatusCounts().first().associate { it.status to it.count }

        assertEquals(2, counts[BookStatus.Reading])
        assertEquals(1, counts[BookStatus.Finished])
        assertNull(counts[BookStatus.WantToRead])
    }

    // A8: observeCounts()/observeBooks()/observeLentOutBooks() on empty DB -> empty list, no crash.
    @Test
    fun emptyDatabase_producesEmptyListsWithoutCrashing() = runTest {
        assertTrue(database.bookDao().observeLibraryBooks(null).first().isEmpty())
        assertTrue(database.bookDao().observeLentOutBooks().first().isEmpty())
        assertTrue(database.bookDao().observeStatusCounts().first().isEmpty())
    }

    // A14: cascade delete - book with 2 loans (1 returned, 1 open), delete book -> both loans
    // gone via FK cascade, the open one included.
    @Test
    fun deletingBook_cascadesToItsLoans_includingTheOpenOne() = runTest {
        val bookId = insertBook("Cascading", BookStatus.Reading, Instant.ofEpochMilli(1_000))
        insertLoan(bookId, returnedAt = Instant.ofEpochMilli(5_000))
        insertLoan(bookId, returnedAt = null)

        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM loans WHERE book_id = $bookId")
            .use {
                it.moveToFirst()
                assertEquals(2, it.getInt(0))
            }

        database.bookDao().deleteById(bookId)

        val remainingLoans = database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM loans WHERE book_id = $bookId")
        remainingLoans.use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }
}
