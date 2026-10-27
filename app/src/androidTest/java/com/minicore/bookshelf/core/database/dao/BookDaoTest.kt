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
import com.minicore.bookshelf.core.model.CoverPreset
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    // F11/AC4: insert -> observeById round-trips all 5 new columns for a Preset book.
    @Test
    fun observeById_roundTripsNewColumns_forPresetBook() = runTest {
        val id = database.bookDao().insert(
            BookEntity(
                title = "Preset Book",
                author = "Author",
                isbn = null,
                coverUri = null,
                genre = null,
                pageCount = null,
                publishedYear = null,
                status = BookStatus.Reading,
                rating = null,
                synopsis = null,
                comment = "Great read",
                memoryWhy = "Gift from a friend",
                memoryWhere = "Lisbon",
                memoryWhat = "Finished it on a train",
                coverColor = CoverPreset.Preset4,
                createdAt = Instant.ofEpochMilli(1_000),
            )
        )

        val restored = database.bookDao().observeById(id).first()

        assertEquals("Great read", restored?.comment)
        assertEquals("Gift from a friend", restored?.memoryWhy)
        assertEquals("Lisbon", restored?.memoryWhere)
        assertEquals("Finished it on a train", restored?.memoryWhat)
        assertEquals(CoverPreset.Preset4, restored?.coverColor)
        assertNull(restored?.coverUri)
    }

    // F18/AC4: insert -> observeById round-trips for an Image book too.
    @Test
    fun observeById_roundTripsNewColumns_forImageBook() = runTest {
        val id = database.bookDao().insert(
            BookEntity(
                title = "Image Book",
                author = "Author",
                isbn = null,
                coverUri = "content://covers/a.jpg",
                genre = null,
                pageCount = null,
                publishedYear = null,
                status = BookStatus.Reading,
                rating = null,
                synopsis = null,
                createdAt = Instant.ofEpochMilli(1_000),
            )
        )

        val restored = database.bookDao().observeById(id).first()

        assertEquals("content://covers/a.jpg", restored?.coverUri)
        assertNull(restored?.coverColor)
    }

    // F9/AC4: update on a nonexistent id returns 0, no throw.
    @Test
    fun update_onNonexistentId_returnsZero_withoutThrowing() = runTest {
        val book = BookEntity(
            id = 999,
            title = "Ghost",
            author = "Author",
            isbn = null,
            coverUri = null,
            genre = null,
            pageCount = null,
            publishedYear = null,
            status = BookStatus.Reading,
            rating = null,
            synopsis = null,
            coverColor = CoverPreset.Preset1,
            createdAt = Instant.ofEpochMilli(1_000),
        )

        val rowsAffected = database.bookDao().update(book)

        assertEquals(0, rowsAffected)
    }

    // F8/AC4: update on a book with an open loan leaves the loan untouched (no REPLACE/cascade).
    @Test
    fun update_onBookWithOpenLoan_leavesLoanIntact() = runTest {
        val bookId = insertBook("Lent Book", BookStatus.Reading, Instant.ofEpochMilli(1_000))
        val loanId = insertLoan(bookId, returnedAt = null)

        val original = database.bookDao().observeById(bookId).first()!!
        val updated = original.copy(comment = "Updated comment")
        val rowsAffected = database.bookDao().update(updated)

        assertEquals(1, rowsAffected)
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM loans WHERE id = $loanId AND book_id = $bookId AND returned_at IS NULL")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
    }

    // F5/AC1: insert violating the never-both-covers invariant throws, no row is written.
    @Test(expected = SQLiteConstraintException::class)
    fun insert_withBothCoverUriAndCoverColor_throwsAndWritesNoRow() = runTest {
        try {
            database.bookDao().insert(
                BookEntity(
                    title = "Invalid",
                    author = "Author",
                    isbn = null,
                    coverUri = "content://covers/a.jpg",
                    genre = null,
                    pageCount = null,
                    publishedYear = null,
                    status = BookStatus.Reading,
                    rating = null,
                    synopsis = null,
                    coverColor = CoverPreset.Preset1,
                    createdAt = Instant.ofEpochMilli(1_000),
                )
            )
        } finally {
            val rows = database.bookDao().observeLibraryBooks(null).first()
            assertTrue(rows.none { it.book.title == "Invalid" })
        }
    }

    // F1/plan #32: observeLibraryBook on an unknown id emits null.
    @Test
    fun observeLibraryBook_unknownId_emitsNull() = runTest {
        assertNull(database.bookDao().observeLibraryBook(999).first())
    }

    // F2/plan #32: no loan -> openLoanId null; open loan -> populated; only a returned loan ->
    // null loan overlay (the LEFT JOIN condition excludes it).
    @Test
    fun observeLibraryBook_noLoan_hasNullOpenLoanId() = runTest {
        val bookId = insertBook("No Loan", BookStatus.Reading, Instant.ofEpochMilli(1_000))

        val row = database.bookDao().observeLibraryBook(bookId).first()

        assertEquals(bookId, row?.book?.id)
        assertNull(row?.openLoanId)
    }

    @Test
    fun observeLibraryBook_withOpenLoan_isPopulated() = runTest {
        val bookId = insertBook("With Loan", BookStatus.Reading, Instant.ofEpochMilli(1_000))
        insertLoan(bookId, returnedAt = null)

        val row = database.bookDao().observeLibraryBook(bookId).first()

        assertEquals("Borrower", row?.openLoanBorrowerName)
    }

    @Test
    fun observeLibraryBook_withOnlyAReturnedLoan_hasNullOpenLoanId() = runTest {
        val bookId = insertBook("Returned Loan", BookStatus.Reading, Instant.ofEpochMilli(1_000))
        insertLoan(bookId, returnedAt = Instant.ofEpochMilli(5_000))

        val row = database.bookDao().observeLibraryBook(bookId).first()

        assertEquals(bookId, row?.book?.id)
        assertNull(row?.openLoanId)
    }

    // F3/plan #32: the flow re-emits live when a loan is inserted, and again when it is returned.
    // Room's invalidation-tracker re-query runs on a background dispatcher, so a fixed `yield()`
    // count doesn't reliably wait for it (flaky, review round 1 critical finding) - collect into
    // an unlimited Channel instead and block on each emission actually arriving.
    @Test
    fun observeLibraryBook_reEmits_whenLoanInsertedThenReturned() = runTest {
        val bookId = insertBook("Live Book", BookStatus.Reading, Instant.ofEpochMilli(1_000))
        val emissions = Channel<Boolean>(Channel.UNLIMITED)
        val job = launch {
            database.bookDao().observeLibraryBook(bookId).collect { emissions.send(it?.openLoanId != null) }
        }
        suspend fun awaitNextEmission() = withContext(Dispatchers.Default) {
            withTimeout(5_000) { emissions.receive() }
        }

        assertEquals(false, awaitNextEmission())

        val loanId = insertLoan(bookId, returnedAt = null)
        assertEquals(true, awaitNextEmission())

        database.loanDao().markReturned(loanId, Instant.ofEpochMilli(9_000))
        assertEquals(false, awaitNextEmission())

        job.cancel()
    }

    // F6/AC1: update violating the invariant throws, the row is unchanged afterwards.
    @Test
    fun update_withBothCoverUriAndCoverColor_throwsAndLeavesRowUnchanged() = runTest {
        val bookId = insertBook("Valid", BookStatus.Reading, Instant.ofEpochMilli(1_000))
        val original = database.bookDao().observeById(bookId).first()!!

        try {
            database.bookDao().update(
                original.copy(coverUri = "content://covers/a.jpg", coverColor = CoverPreset.Preset2)
            )
            throw AssertionError("expected SQLiteConstraintException")
        } catch (expected: SQLiteConstraintException) {
            // expected
        }

        val afterFailedUpdate = database.bookDao().observeById(bookId).first()
        assertEquals(original, afterFailedUpdate)
    }
}
