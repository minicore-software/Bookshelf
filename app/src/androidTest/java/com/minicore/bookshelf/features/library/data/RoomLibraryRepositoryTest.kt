package com.minicore.bookshelf.features.library.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.database.entity.LoanEntity
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AC2/AC3 (plan section 5/8): [RoomLibraryRepository] against real DAOs/Room - the contract #20
 * and #21 build on.
 */
@RunWith(AndroidJUnit4::class)
class RoomLibraryRepositoryTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: RoomLibraryRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .applyBookshelfConfig()
            .build()
        repository = RoomLibraryRepository(database.bookDao(), database.loanDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun insertBook(title: String, status: BookStatus, createdAt: Long): Long =
        database.bookDao().insert(
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
                createdAt = Instant.ofEpochMilli(createdAt),
            )
        )

    private suspend fun insertOpenLoan(bookId: Long, borrowerName: String = "Borrower"): Long =
        database.loanDao().insert(
            LoanEntity(
                bookId = bookId,
                borrowerName = borrowerName,
                borrowerContact = null,
                loanedAt = Instant.ofEpochMilli(1_000),
                dueAt = LocalDate.of(2026, 11, 1),
                returnedAt = null,
                remindEnabled = false,
                notes = null,
            )
        )

    @Test
    fun observeBooks_withNullStatus_returnsAllBooksWithOverlay() = runTest {
        val wantToRead = insertBook("Want", BookStatus.WantToRead, 1_000)
        val lent = insertBook("Lent", BookStatus.Reading, 2_000)
        val loanId = insertOpenLoan(lent, borrowerName = "Jamie")

        val books = repository.observeBooks().first()

        assertEquals(listOf(lent, wantToRead), books.map { it.book.id })
        assertNull(books.single { it.book.id == wantToRead }.openLoan)
        val expectedOpenLoan = OpenLoan(
            loanId = loanId,
            borrowerName = "Jamie",
            loanedAt = Instant.ofEpochMilli(1_000),
            dueAt = LocalDate.of(2026, 11, 1),
        )
        assertEquals(expectedOpenLoan, books.single { it.book.id == lent }.openLoan)
    }

    @Test
    fun observeCurrentlyReading_returnsOnlyReadingBooks() = runTest {
        insertBook("Want", BookStatus.WantToRead, 1_000)
        val reading = insertBook("Reading", BookStatus.Reading, 2_000)

        val books = repository.observeCurrentlyReading().first()

        assertEquals(listOf(reading), books.map { it.book.id })
    }

    @Test
    fun observeLentOutBooks_returnsOnlyBooksWithOpenLoan() = runTest {
        val lent = insertBook("Lent", BookStatus.Reading, 1_000)
        insertBook("Not Lent", BookStatus.Reading, 2_000)
        insertOpenLoan(lent)

        val lentBooks = repository.observeLentOutBooks().first()

        assertEquals(listOf(lent), lentBooks.map { it.book.id })
    }

    @Test
    fun observeCounts_mixedLibrary_matchesStatusesAndLentOut() = runTest {
        insertBook("Reading 1", BookStatus.Reading, 1_000)
        val reading2 = insertBook("Reading 2", BookStatus.Reading, 2_000)
        insertBook("Finished", BookStatus.Finished, 3_000)
        insertOpenLoan(reading2)

        val counts = repository.observeCounts().first()

        assertEquals(LibraryCounts(all = 3, reading = 2, finished = 1, wantToRead = 0, lentOut = 1), counts)
    }

    @Test
    fun emptyDatabase_producesEmptyListsAndAllZeroCounts() = runTest {
        assertTrue(repository.observeBooks().first().isEmpty())
        assertTrue(repository.observeLentOutBooks().first().isEmpty())
        assertEquals(LibraryCounts(0, 0, 0, 0, 0), repository.observeCounts().first())
    }

    private fun sampleBook(cover: Cover = Cover.Preset(CoverPreset.Preset1)) = Book(
        id = 0,
        title = "A New Book",
        author = "Author",
        isbn = null,
        cover = cover,
        genre = null,
        pageCount = null,
        publishedYear = null,
        status = BookStatus.WantToRead,
        rating = null,
        synopsis = null,
        createdAt = Instant.ofEpochMilli(1_000),
    )

    // F10/AC4: addBook with id != 0 throws IllegalArgumentException.
    @Test(expected = IllegalArgumentException::class)
    fun addBook_withNonZeroId_throws() = runTest {
        repository.addBook(sampleBook().copy(id = 5))
    }

    // F11/AC4: addBook -> observeBook -> updateBook round-trips, including a cover flip.
    @Test
    fun addBook_thenObserveBook_thenUpdateBook_roundTrips() = runTest {
        val newId = repository.addBook(sampleBook())

        val added = repository.observeBook(newId).first()
        assertEquals(sampleBook().copy(id = newId), added)

        val updated = added!!.copy(
            comment = "Loved it",
            cover = Cover.Image("content://covers/flip.jpg"),
        )
        val wasUpdated = repository.updateBook(updated)
        assertTrue(wasUpdated)

        val reread = repository.observeBook(newId).first()
        assertEquals(updated, reread)

        // Flip back the other way: Image -> Preset.
        val flippedBack = reread!!.copy(cover = Cover.Preset(CoverPreset.Preset6))
        repository.updateBook(flippedBack)
        assertEquals(flippedBack, repository.observeBook(newId).first())
    }

    // AC4: observeBook on a missing id emits null.
    @Test
    fun observeBook_onMissingId_emitsNull() = runTest {
        assertNull(repository.observeBook(999).first())
    }

    // Plan #32/F1: observeLibraryBook on a missing id emits null.
    @Test
    fun observeLibraryBook_onMissingId_emitsNull() = runTest {
        assertNull(repository.observeLibraryBook(999).first())
    }

    // Plan #32/F2: observeLibraryBook maps the open-loan overlay the same way observeBooks does.
    @Test
    fun observeLibraryBook_withOpenLoan_mapsOverlay() = runTest {
        val bookId = insertBook("Lent", BookStatus.Reading, 1_000)
        val loanId = insertOpenLoan(bookId, borrowerName = "Jamie")

        val detail = repository.observeLibraryBook(bookId).first()

        assertEquals(bookId, detail?.book?.id)
        val expectedOpenLoan = OpenLoan(
            loanId = loanId,
            borrowerName = "Jamie",
            loanedAt = Instant.ofEpochMilli(1_000),
            dueAt = LocalDate.of(2026, 11, 1),
        )
        assertEquals(expectedOpenLoan, detail?.openLoan)
    }

    // AC3/plan #34 D1-D4: removeBook cascades to every one of its loans, returned and open
    // alike, in the same DELETE (the FK cascade, ADR-001) - another book and its own loan are
    // untouched.
    @Test
    fun removeBook_cascadesToAllItsLoans_leavesOtherBooksUntouched() = runTest {
        val bookA = insertBook("A", BookStatus.Reading, 1_000)
        insertOpenLoan(bookA, borrowerName = "Returned already").let { loanId ->
            database.loanDao().markReturned(loanId, Instant.ofEpochMilli(2_000))
        }
        insertOpenLoan(bookA, borrowerName = "Still open")
        val bookB = insertBook("B", BookStatus.Reading, 3_000)
        insertOpenLoan(bookB, borrowerName = "Unrelated")

        val removed = repository.removeBook(bookA)

        assertTrue(removed)
        assertNull(repository.observeBook(bookA).first())
        val loansForA = countLoansForBook(bookA)
        assertEquals(0, loansForA)
        assertEquals(bookB, repository.observeBook(bookB).first()?.id)
        assertEquals(1, countLoansForBook(bookB))
    }

    // D8: removeBook on a missing id returns false.
    @Test
    fun removeBook_onMissingId_returnsFalse() = runTest {
        assertFalse(repository.removeBook(999L))
    }

    private suspend fun countLoansForBook(bookId: Long): Int {
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM loans WHERE book_id = $bookId").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    // F7/R5: a corrupt stored cover_color resolves to the id-derived preset through the mapper,
    // not a crash (same fallback path as neither-set, plan S3).
    @Test
    fun observeBooks_withCorruptCoverColor_fallsBackToIdDerivedPreset() = runTest {
        val bookId = insertBook("Corrupt Cover", BookStatus.Reading, 1_000)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE books SET cover_color = 'bogus' WHERE id = $bookId"
        )

        val books = repository.observeBooks().first()

        assertEquals(Cover.Preset(CoverPreset.forBookId(bookId)), books.single().book.cover)
    }
}
